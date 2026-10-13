package com.hairgo.app.firebase;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.hairgo.app.models.OwnerSalon;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Everything the owner side needs from Firestore that the shared managers don't cover.
 * Bookings are NOT here on purpose: use BookingManager (getBookingsForSalon,
 * updateBookingStatus, cancelBooking) so there is one source of truth for them.
 */
public class OwnerManager {

    private final FirebaseFirestore db;

    public OwnerManager() {
        db = FirebaseFirestore.getInstance();
    }

    public interface OwnerCallback<T> {
        void onSuccess(T result);
        void onFailure(String errorMessage);
    }

    /** Reads the owner's first name from users/{uid}. */
    public void getOwnerName(String uid, OwnerCallback<String> callback) {
        db.collection("users").document(uid).get()
                .addOnSuccessListener(doc -> {
                    String name = doc.getString("name");
                    callback.onSuccess(name == null ? "" : name);
                })
                .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
    }

    /** All non-deleted salons owned by this user. Equality filters only, so no index is needed. */
    public void getSalonsForOwner(String ownerId, OwnerCallback<List<OwnerSalon>> callback) {
        db.collection("salons")
                .whereEqualTo("ownerID", ownerId)
                .whereEqualTo("isDeleted", false)
                .get()
                .addOnSuccessListener(query -> {
                    List<OwnerSalon> salons = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : query) {
                        String name = doc.getString("name");
                        if (name == null || name.trim().isEmpty()) continue;
                        String location = doc.getString("location");
                        salons.add(new OwnerSalon(doc.getId(), name, location == null ? "" : location));
                    }
                    Collections.sort(salons, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
                    callback.onSuccess(salons);
                })
                .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
    }

    // --------------------------------------------------------- registration

    /**
     * Creates a salon-owner account: Firebase Auth user, users/{uid} with role "owner",
     * and their first salon. The registration flow's final button should call this.
     * Mirrors the field names AuthManager.registerUser writes for clients.
     */
    public void registerOwner(String fullName, String email, String password, String phone,
                              String salonName, String location, List<String> services,
                              AuthManager.AuthCallback callback) {

        FirebaseAuth.getInstance().createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener(authResult -> {
                    FirebaseUser firebaseUser = authResult.getUser();
                    if (firebaseUser == null) {
                        callback.onFailure("Something went wrong. Please try again.");
                        return;
                    }
                    String uid = firebaseUser.getUid();

                    String trimmed = fullName == null ? "" : fullName.trim();
                    int space = trimmed.indexOf(' ');
                    String firstName = space == -1 ? trimmed : trimmed.substring(0, space);
                    String surname = space == -1 ? "" : trimmed.substring(space + 1).trim();

                    Map<String, Object> newUser = new HashMap<>();
                    newUser.put("name", firstName);
                    newUser.put("surname", surname);
                    newUser.put("email", email);
                    newUser.put("phoneNumber", phone);
                    newUser.put("role", "owner");
                    newUser.put("createdAt", FieldValue.serverTimestamp());
                    newUser.put("isDeleted", false);

                    db.collection("users").document(uid).set(newUser)
                            .addOnSuccessListener(unused ->
                                    new SalonManager().createSalon(uid, salonName, location, services,
                                            new SalonManager.SalonCreatedCallback() {
                                                @Override
                                                public void onSuccess(String salonId) {
                                                    callback.onSuccess("owner");
                                                }

                                                @Override
                                                public void onFailure(String errorMessage) {
                                                    callback.onFailure(
                                                            "Account created, but saving your salon failed: "
                                                                    + errorMessage);
                                                }
                                            }))
                            .addOnFailureListener(e ->
                                    callback.onFailure("Account created, but saving profile failed: "
                                            + e.getMessage()));
                })
                .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
    }
}