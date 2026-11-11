package com.hairgo.app.firebase;

import androidx.annotation.NonNull;

import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import com.hairgo.app.utils.Constants;

import java.util.HashMap;
import java.util.Map;

public class AuthManager {

    // Sentinel used by LoginActivity so it can offer a resend action without
    // exposing raw Firebase errors to the user.
    public static final String EMAIL_NOT_VERIFIED = "EMAIL_NOT_VERIFIED";

    private static final String FIELD_EMAIL_VERIFICATION_REQUIRED =
            "emailVerificationRequired";

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    public AuthManager() {
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
    }

    public FirebaseAuth getAuth() {
        return mAuth;
    }

    public FirebaseUser getCurrentUser() {
        return mAuth.getCurrentUser();
    }

    public boolean isLoggedIn() {
        return getCurrentUser() != null;
    }

    public void signOut() {
        mAuth.signOut();
    }

    /**
     * Fetches this device's FCM registration token and stores it on the
     * signed-in user's profile, so booking notifications can reach the device.
     */
    public void saveFcmToken() {
        if (getCurrentUser() == null) return;
        FirebaseMessaging.getInstance().getToken()
                .addOnSuccessListener(this::saveFcmToken);
    }

    /** Stores an already-known token; used by the messaging service. */
    public void saveFcmToken(String token) {
        if (token == null || token.isEmpty()) return;

        FirebaseUser user = getCurrentUser();
        if (user == null) return;

        Map<String, Object> updates = new HashMap<>();
        updates.put("fcmToken", token);
        updates.put("fcmUpdatedAt", FieldValue.serverTimestamp());

        // merge() so only the token fields are touched on the existing profile.
        db.collection(Constants.COLLECTION_USERS)
                .document(user.getUid())
                .set(updates, SetOptions.merge());
    }

    // Callback used when registration/login finishes
    public interface AuthCallback {
        void onSuccess(String role);
        void onFailure(String errorMessage);
    }

    // Callback for reading the signed-in user's profile document.
    public interface ProfileCallback {
        void onSuccess(String name, String role);
        void onFailure(String errorMessage);
    }

    // Callback for the early "is this email already taken?" check on the
    // register screens.
    public interface EmailCheckCallback {
        void onResult(boolean registered);
    }

    // Used by Splash to decide whether to show the check-email screen. The role
    // is passed along so "Wrong email? Start over" can reopen the right form.
    public interface VerificationCheckCallback {
        void onResult(boolean pending, String role);
    }

    /**
     * Best-effort check for an existing account with this email, so the register
     * screens can warn before OTP and terms. Firebase's email-enumeration
     * protection can make this report "not registered" even for taken emails,
     * so callers must keep the createUser "email already in use" error as the
     * safety net either way.
     */
    public void isEmailRegistered(String email, EmailCheckCallback callback) {
        if (email == null || email.trim().isEmpty()) {
            callback.onResult(false);
            return;
        }
        mAuth.fetchSignInMethodsForEmail(email.trim())
                .addOnCompleteListener(task -> {
                    boolean registered = task.isSuccessful()
                            && task.getResult() != null
                            && !task.getResult().getSignInMethods().isEmpty();
                    callback.onResult(registered);
                });
    }

    /**
     * Sends Firebase's built-in verification email to the signed-in account.
     * No backend or Cloud Function is involved; errors are returned raw so the
     * caller can map them to a clear message.
     */
    public void sendVerificationEmail(OperationCallback callback) {

        FirebaseUser firebaseUser = getCurrentUser();

        if (firebaseUser == null) {
            callback.onFailure("You are not signed in.");
            return;
        }

        if (firebaseUser.getEmail() == null
                || firebaseUser.getEmail().trim().isEmpty()) {
            callback.onFailure("This account has no email address.");
            return;
        }

        firebaseUser.sendEmailVerification()
                .addOnSuccessListener(unused -> callback.onSuccess())
                .addOnFailureListener(e -> callback.onFailure(
                        e.getMessage() == null
                                ? "Could not send the verification email."
                                : e.getMessage()
                ));
    }

    /**
     * True when this account was created by the new email-verification flow
     * and still has not verified its email. Accounts created before the flow
     * have no flag, so they are treated as legacy and never blocked.
     */
    public void isEmailVerificationPending(VerificationCheckCallback callback) {

        FirebaseUser firebaseUser = getCurrentUser();

        if (firebaseUser == null) {
            callback.onResult(false, null);
            return;
        }

        db.collection(Constants.COLLECTION_USERS)
                .document(firebaseUser.getUid())
                .get()
                .addOnSuccessListener(documentSnapshot -> {

                    if (!documentSnapshot.exists()) {
                        callback.onResult(false, null);
                        return;
                    }

                    String role = documentSnapshot.getString("role");

                    Boolean required = documentSnapshot.getBoolean(
                            FIELD_EMAIL_VERIFICATION_REQUIRED
                    );

                    if (!Boolean.TRUE.equals(required)) {
                        callback.onResult(false, role);
                        return;
                    }

                    firebaseUser.reload().addOnCompleteListener(task -> {
                        FirebaseUser refreshed = getCurrentUser();
                        boolean verified = refreshed != null
                                && refreshed.isEmailVerified();
                        callback.onResult(!verified, role);
                    });
                })
                .addOnFailureListener(e -> callback.onResult(false, null));
    }

    /**
     * Reads the current user's profile from Firestore. Used by screens that need
     * the user's name or role after the session is already established.
     */
    public void loadCurrentUserProfile(ProfileCallback callback) {

        FirebaseUser firebaseUser = getCurrentUser();

        if (firebaseUser == null) {
            callback.onFailure("Not signed in.");
            return;
        }

        db.collection(Constants.COLLECTION_USERS)
                .document(firebaseUser.getUid())
                .get()
                .addOnSuccessListener(documentSnapshot -> {

                    if (!documentSnapshot.exists()) {
                        callback.onFailure("User profile was not found.");
                        return;
                    }

                    // A soft-deleted account must never be re-admitted, even if
                    // its Firebase session is still on the device (e.g. after an
                    // app restart routed through SplashActivity).
                    Boolean isDeleted = documentSnapshot.getBoolean("isDeleted");
                    if (Boolean.TRUE.equals(isDeleted)) {
                        callback.onFailure("This account has been deactivated.");
                        return;
                    }

                    String name = documentSnapshot.getString("name");
                    String role = documentSnapshot.getString("role");

                    if (role == null || role.trim().isEmpty()) {
                        callback.onFailure("User role is missing from your profile.");
                        return;
                    }

                    callback.onSuccess(name, role.trim().toLowerCase());
                })
                .addOnFailureListener(e ->
                        callback.onFailure(
                                "Could not load your user profile: " + e.getMessage()
                        )
                );
    }

    public void registerUser(String name, String surname, String email, String password,
                             String phoneNumber, String area, Double latitude, Double longitude,
                             AuthCallback callback) {

        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener(authResult -> {

                    FirebaseUser firebaseUser = authResult.getUser();

                    if (firebaseUser == null) {
                        callback.onFailure("Something went wrong. Please try again.");
                        return;
                    }

                    String uid = firebaseUser.getUid();

                    Map<String, Object> newUser = new HashMap<>();
                    newUser.put("name", name);
                    newUser.put("surname", surname);
                    newUser.put("email", email);
                    newUser.put("phoneNumber", phoneNumber);
                    newUser.put("role", "client");
                    newUser.put("createdAt", FieldValue.serverTimestamp());
                    newUser.put("isDeleted", false);
                    // New accounts must verify their email before they can use
                    // the app. Older profiles have no flag and stay unaffected.
                    newUser.put(FIELD_EMAIL_VERIFICATION_REQUIRED, true);
                    // Optional home area and map coordinates. Fields are only
                    // written when the client picked them during registration.
                    if (area != null && !area.trim().isEmpty()) {
                        newUser.put("area", area.trim());
                    }
                    if (latitude != null && longitude != null) {
                        newUser.put("latitude", latitude);
                        newUser.put("longitude", longitude);
                    }

                    db.collection("users")
                            .document(uid)
                            .set(newUser)
                            .addOnSuccessListener(unused -> {
                                saveFcmToken();
                                callback.onSuccess("client");
                            })
                            .addOnFailureListener(e ->
                                    callback.onFailure(
                                            "Account created, but saving profile failed: "
                                                    + e.getMessage()
                                    )
                            );
                })
                .addOnFailureListener(e ->
                        callback.onFailure(e.getMessage())
                );
    }

    public void loginUser(String email, String password, AuthCallback callback) {

        mAuth.signInWithEmailAndPassword(email, password)

                .addOnSuccessListener(authResult -> {

                    FirebaseUser firebaseUser = authResult.getUser();

                    if (firebaseUser == null) {
                        callback.onFailure("Could not retrieve user account.");
                        return;
                    }

                    String uid = firebaseUser.getUid();

                    // Get the user's profile from Firestore
                    db.collection("users")
                            .document(uid)
                            .get()

                            .addOnSuccessListener(documentSnapshot -> {

                                if (!documentSnapshot.exists()) {
                                    callback.onFailure(
                                            "User profile was not found in Firestore."
                                    );
                                    return;
                                }

                                Boolean isDeleted =
                                        documentSnapshot.getBoolean("isDeleted");

                                if (Boolean.TRUE.equals(isDeleted)) {
                                    callback.onFailure(
                                            "This account has been deactivated."
                                    );
                                    return;
                                }

                                String role =
                                        documentSnapshot.getString("role");

                                if (role == null || role.trim().isEmpty()) {
                                    callback.onFailure(
                                            "User role is missing from your profile."
                                    );
                                    return;
                                }

                                // Only accounts created by the new email flow
                                // carry this flag. Legacy accounts skip the
                                // check entirely.
                                Boolean requiresVerification =
                                        documentSnapshot.getBoolean(
                                                FIELD_EMAIL_VERIFICATION_REQUIRED
                                        );

                                if (Boolean.TRUE.equals(requiresVerification)) {

                                    firebaseUser.reload().addOnCompleteListener(reloadTask -> {

                                        if (!reloadTask.isSuccessful()
                                                && reloadTask.getException() != null) {

                                            String reloadError =
                                                    reloadTask.getException().getMessage();

                                            if (isNetworkError(reloadError)) {
                                                callback.onFailure(reloadError);
                                                return;
                                            }
                                        }

                                        FirebaseUser refreshed = getCurrentUser();

                                        if (refreshed == null) {
                                            callback.onFailure(
                                                    "Could not refresh your account. Please try again."
                                            );
                                            return;
                                        }

                                        if (!refreshed.isEmailVerified()) {
                                            callback.onFailure(EMAIL_NOT_VERIFIED);
                                            return;
                                        }

                                        saveFcmToken();
                                        callback.onSuccess(role.trim().toLowerCase());
                                    });

                                    return;
                                }

                                saveFcmToken();
                                callback.onSuccess(role.trim().toLowerCase());
                            })

                            .addOnFailureListener(e ->
                                    callback.onFailure(
                                            "Could not load your user profile: "
                                                    + e.getMessage()
                                    )
                            );
                })

                .addOnFailureListener(e ->
                        callback.onFailure(e.getMessage())
                );
    }

    // Callback for an operation that has no value to return.
    public interface OperationCallback {
        void onSuccess();
        void onFailure(String errorMessage);
    }

    /**
     * Re-checks the user's password by re-authenticating the signed-in account.
     * Firebase requires a recent login before the account can be deleted, so this
     * runs first in the delete flow.
     */
    public void reauthenticate(String password, OperationCallback callback) {

        FirebaseUser firebaseUser = getCurrentUser();

        if (firebaseUser == null) {
            callback.onFailure("Not signed in.");
            return;
        }

        String email = firebaseUser.getEmail();

        if (email == null || email.trim().isEmpty()) {
            callback.onFailure("This account cannot be verified with a password.");
            return;
        }

        AuthCredential credential =
                EmailAuthProvider.getCredential(email, password);

        firebaseUser.reauthenticate(credential)
                .addOnSuccessListener(unused -> callback.onSuccess())
                .addOnFailureListener(e -> {
                    if (e instanceof FirebaseAuthInvalidCredentialsException) {
                        callback.onFailure("Incorrect password.");
                    } else {
                        callback.onFailure(e.getMessage());
                    }
                });
    }

    /**
     * Soft-deletes the signed-in user's profile: the document stays in Firestore
     * (for auditing) but is flagged isDeleted, which blocks all future logins.
     * Must run while the session is still live, because the Firestore rules
     * authorise the write with the user's own token.
     */
    public void softDeleteCurrentUserProfile(OperationCallback callback) {

        FirebaseUser firebaseUser = getCurrentUser();

        if (firebaseUser == null) {
            callback.onFailure("Not signed in.");
            return;
        }

        Map<String, Object> updates = new HashMap<>();
        updates.put("isDeleted", true);
        updates.put("deletedAt", FieldValue.serverTimestamp());

        db.collection(Constants.COLLECTION_USERS)
                .document(firebaseUser.getUid())
                .update(updates)
                .addOnSuccessListener(unused -> callback.onSuccess())
                .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
    }

    /**
     * Hard-deletes the Firebase Auth account. Firestore keeps the audited trail
     * (the soft-deleted profile and any cancelled bookings), so this only removes
     * the login credential itself.
     */
    public void deleteAuthAccount(OperationCallback callback) {

        FirebaseUser firebaseUser = getCurrentUser();

        if (firebaseUser == null) {
            callback.onFailure("Not signed in.");
            return;
        }

        firebaseUser.delete()
                .addOnSuccessListener(unused -> callback.onSuccess())
                .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
    }

    private boolean isNetworkError(String rawMessage) {

        if (rawMessage == null) {
            return false;
        }

        String message = rawMessage.toLowerCase();

        return message.contains("network")
                || message.contains("timeout");
    }
}