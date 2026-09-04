package com.hairgo.app.firebase;

import androidx.annotation.NonNull;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

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

    // Callback used when registration/login finishes
    public interface AuthCallback {
        void onSuccess(String role);
        void onFailure(String errorMessage);
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
                            .addOnSuccessListener(unused ->
                                    callback.onSuccess("client"))
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