package com.hairgo.app.firebase;

import androidx.annotation.NonNull;

import com.google.firebase.auth.FirebaseAuth;
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
}