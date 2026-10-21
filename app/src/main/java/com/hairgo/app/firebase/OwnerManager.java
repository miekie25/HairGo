package com.hairgo.app.firebase;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
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

    /** What the dashboard needs to know about the signed-in user before showing owner screens. */
    public static class OwnerProfile {
        public final String name;
        public final String role;
        public final boolean deleted;

        OwnerProfile(String name, String role, boolean deleted) {
            this.name = name;
            this.role = role;
            this.deleted = deleted;
        }
    }

    /** Reads the owner's first name from users/{uid}. Kept so older callers still compile. */
    public void getOwnerName(String uid, OwnerCallback<String> callback) {
        db.collection("users").document(uid).get()
                .addOnSuccessListener(doc -> {
                    String name = doc.getString("name");
                    callback.onSuccess(name == null ? "" : name);
                })
                .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
    }

    /** Reads the signed-in user's name, role and deactivated flag from users/{uid}. */
    public void getOwnerProfile(String uid, OwnerCallback<OwnerProfile> callback) {
        db.collection("users").document(uid).get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) {
                        callback.onFailure("User profile was not found.");
                        return;
                    }
                    String name = doc.getString("name");
                    String role = doc.getString("role");
                    Boolean deleted = doc.getBoolean("isDeleted");
                    callback.onSuccess(new OwnerProfile(
                            name == null ? "" : name,
                            role == null ? "" : role.trim().toLowerCase(),
                            Boolean.TRUE.equals(deleted)));
                })
                .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
    }

    /**
     * Looks up a client's full name from their profile, for bookings made before
     * clientName was stored on the booking. Returns "" if the profile has no name.
     */
    public void getClientName(String uid, OwnerCallback<String> callback) {
        db.collection("users").document(uid).get()
                .addOnSuccessListener(doc -> {
                    String first = doc.getString("name");
                    String last = doc.getString("surname");
                    String full = ((first == null ? "" : first) + " "
                            + (last == null ? "" : last)).trim();
                    callback.onSuccess(full);
                })
                .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
    }

    /**
     * Reads a client's phone number from their profile, so the owner can call
     * them about a booking. Returns "" when the profile has no number. Any
     * signed-in user may read a user profile (see firestore.rules), so this is
     * allowed for the salon owner.
     */
    public void getClientPhone(String uid, OwnerCallback<String> callback) {
        db.collection("users").document(uid).get()
                .addOnSuccessListener(doc -> {
                    String phone = doc.getString("phoneNumber");
                    callback.onSuccess(phone == null ? "" : phone.trim());
                })
                .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
    }

    /**
     * Live feed of one salon's bookings: fires now and again on every change, so a new request
     * or a client's cancellation appears without refreshing. Remove the returned registration
     * when the screen stops (onPause) to avoid background reads.
     *
     * <p>A client's cancellation is stored as status "cancelled" plus isDeleted true, which
     * BookingManager.getBookingsForSalon hides. This query keeps those, so the owner sees them
     * as cancelled. Any other soft-deleted booking stays hidden. A single equality filter, so
     * no index is needed.
     */
    public ListenerRegistration listenToSalonBookings(String salonId,
                                                      BookingManager.BookingListCallback callback) {
        return db.collection("bookings")
                .whereEqualTo("salonID", salonId)
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null) {
                        callback.onFailure(error.getMessage());
                        return;
                    }
                    List<Map<String, Object>> bookings = new ArrayList<>();
                    if (snapshots != null) {
                        for (QueryDocumentSnapshot doc : snapshots) {
                            boolean deleted = Boolean.TRUE.equals(doc.getBoolean("isDeleted"));
                            String status = doc.getString("status");
                            if (deleted && !"cancelled".equals(status)) continue;
                            bookings.add(doc.getData());
                        }
                    }
                    callback.onSuccess(bookings);
                });
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
     * Account-only owner signup: Firebase Auth user plus users/{uid} with role "owner".
     * No salon is created here. The dashboard prompts the owner to add their first salon
     * (name, location, services), so clients never see a placeholder salon.
     */
    public void registerOwner(String fullName, String email, String password, String phone,
                              AuthManager.AuthCallback callback) {
        registerOwner(fullName, email, password, phone, null, null, null, callback);
    }

    /**
     * Creates a salon-owner account and, if a salon name is given, their first salon.
     * The role is written as "owner" at creation, never changed afterwards, so it works
     * with rules that stop users editing their own role.
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
                            .addOnSuccessListener(unused -> {
                                // Same as the client flow: let this device receive notifications.
                                new AuthManager().saveFcmToken();

                                if (salonName == null || salonName.trim().isEmpty()) {
                                    callback.onSuccess("owner");
                                    return;
                                }

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
                                        });
                            })
                            .addOnFailureListener(e ->
                                    callback.onFailure("Account created, but saving profile failed: "
                                            + e.getMessage()));
                })
                .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
    }
}