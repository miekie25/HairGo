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
                             String phoneNumber, AuthCallback callback) {

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
}