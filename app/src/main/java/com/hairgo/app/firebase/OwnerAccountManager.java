package com.hairgo.app.firebase;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Deleting a salon owner's account.
 *
 * <p>Mirrors the client flow (re-check the password, soft-delete the profile, remove the
 * login) and adds what only an owner has: their salons and the bookings clients made there.
 * Nothing is erased from Firestore, so the records stay for auditing:
 * <ol>
 *   <li>re-check the password (this also gives Firebase the recent login it needs)</li>
 *   <li>cancel every upcoming pending or confirmed booking at the owner's salons; clients see
 *       them as Cancelled, the same as when an owner declines</li>
 *   <li>flag every salon isDeleted, so clients can no longer find or book it</li>
 *   <li>flag the owner's profile isDeleted, which blocks all future logins</li>
 *   <li>remove the Firebase login itself</li>
 * </ol>
 *
 * <p>Each step runs only once the one before it has finished, and every write is idempotent,
 * so if something fails part-way the owner can simply try again.
 */
public class OwnerAccountManager {

    /** The message AuthManager.reauthenticate returns for a wrong password. */
    public static final String INCORRECT_PASSWORD = "Incorrect password.";

    /**
     * true: also delete the Firebase login (the email can be used to sign up again), exactly
     * like the client delete. false: only deactivate; the login stays but is blocked.
     */
    private static final boolean REMOVE_LOGIN_ACCOUNT = true;

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private final AuthManager authManager = new AuthManager();

    /** What deleting the account will touch. */
    public static class Impact {
        public final List<String> salonIds;
        public final List<String> bookingIds;

        Impact(List<String> salonIds, List<String> bookingIds) {
            this.salonIds = salonIds;
            this.bookingIds = bookingIds;
        }
    }

    public interface ImpactCallback {
        void onSuccess(Impact impact);
        void onFailure(String errorMessage);
    }

    public interface DeleteCallback {
        /**
         * @param loginRemoved false when the profile was deactivated but the Firebase login
         *                     itself could not be removed (the account is unusable either way)
         */
        void onSuccess(boolean loginRemoved);
        void onFailure(String errorMessage);
    }

    /**
     * Finds the owner's active salons and the upcoming bookings at them. Used to tell the owner
     * what will happen, and again at deletion time so the numbers are never stale.
     * Equality filters only, so no index is needed.
     */
    public void loadImpact(String ownerId, ImpactCallback callback) {
        db.collection("salons")
                .whereEqualTo("ownerID", ownerId)
                .whereEqualTo("isDeleted", false)
                .get()
                .addOnSuccessListener(salonQuery -> {
                    List<String> salonIds = new ArrayList<>();
                    for (DocumentSnapshot salon : salonQuery.getDocuments()) {
                        salonIds.add(salon.getId());
                    }

                    if (salonIds.isEmpty()) {
                        callback.onSuccess(new Impact(salonIds, new ArrayList<>()));
                        return;
                    }

                    List<Task<QuerySnapshot>> queries = new ArrayList<>();
                    for (String salonId : salonIds) {
                        queries.add(db.collection("bookings")
                                .whereEqualTo("salonID", salonId)
                                .whereEqualTo("isDeleted", false)
                                .get());
                    }

                    Tasks.whenAll(queries)
                            .addOnSuccessListener(unused -> {
                                Date now = new Date();
                                List<String> bookingIds = new ArrayList<>();
                                for (Task<QuerySnapshot> query : queries) {
                                    for (DocumentSnapshot booking : query.getResult().getDocuments()) {
                                        String status = booking.getString("status");
                                        Timestamp when = booking.getTimestamp("dateTime");
                                        boolean active = "pending".equals(status)
                                                || "confirmed".equals(status);
                                        boolean upcoming = when != null && !when.toDate().before(now);
                                        if (active && upcoming) bookingIds.add(booking.getId());
                                    }
                                }
                                callback.onSuccess(new Impact(salonIds, bookingIds));
                            })
                            .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
                })
                .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
    }

    /** Runs the whole delete flow for the signed-in owner. */
    public void deleteAccount(String password, DeleteCallback callback) {
        FirebaseUser user = authManager.getCurrentUser();
        if (user == null) {
            callback.onFailure("Not signed in.");
            return;
        }
        String ownerId = user.getUid();

        // 1. Re-check the password.
        authManager.reauthenticate(password, new AuthManager.OperationCallback() {
            @Override
            public void onSuccess() {
                // 2. Work out what is affected right now.
                loadImpact(ownerId, new ImpactCallback() {
                    @Override
                    public void onSuccess(Impact impact) {
                        cancelBookings(impact, callback);
                    }

                    @Override
                    public void onFailure(String errorMessage) {
                        callback.onFailure(errorMessage);
                    }
                });
            }

            @Override
            public void onFailure(String errorMessage) {
                callback.onFailure(errorMessage);
            }
        });
    }

    // Cancel upcoming bookings. Single writes rather than one big batch: each write is checked
    // by the Firestore rules on its own, so a long list can never trip the per-batch rule limits.
    private void cancelBookings(Impact impact, DeleteCallback callback) {
        List<Task<Void>> updates = new ArrayList<>();
        for (String bookingId : impact.bookingIds) {
            // Same as an owner declining: status only, so the client still sees "Cancelled".
            updates.add(db.collection("bookings").document(bookingId)
                    .update("status", "cancelled"));
        }

        Tasks.whenAll(updates)
                .addOnSuccessListener(unused -> deactivateSalons(impact, callback))
                .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
    }

    private void deactivateSalons(Impact impact, DeleteCallback callback) {
        Map<String, Object> flag = new HashMap<>();
        flag.put("isDeleted", true);
        flag.put("deletedAt", FieldValue.serverTimestamp());

        List<Task<Void>> updates = new ArrayList<>();
        for (String salonId : impact.salonIds) {
            updates.add(db.collection("salons").document(salonId).update(flag));
        }

        Tasks.whenAll(updates)
                .addOnSuccessListener(unused -> deactivateProfile(callback))
                .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
    }

    // Must happen while the session is still live: the rules authorise the write with the
    // owner's own token.
    private void deactivateProfile(DeleteCallback callback) {
        authManager.softDeleteCurrentUserProfile(new AuthManager.OperationCallback() {
            @Override
            public void onSuccess() {
                removeLogin(callback);
            }

            @Override
            public void onFailure(String errorMessage) {
                callback.onFailure(errorMessage);
            }
        });
    }

    private void removeLogin(DeleteCallback callback) {
        if (!REMOVE_LOGIN_ACCOUNT) {
            callback.onSuccess(false);
            return;
        }

        authManager.deleteAuthAccount(new AuthManager.OperationCallback() {
            @Override
            public void onSuccess() {
                callback.onSuccess(true);
            }

            @Override
            public void onFailure(String errorMessage) {
                // The profile is already flagged deleted, so the account cannot be used
                // either way. Report it as deactivated rather than as a failure.
                callback.onSuccess(false);
            }
        });
    }

    // ------------------------------------------------------------------ close ONE salon

    public interface CloseCallback {
        void onSuccess();
        void onFailure(String errorMessage);
    }

    /**
     * Finds the upcoming pending or confirmed bookings at ONE salon, so the owner can be told
     * what closing it will do. Equality filters only, so no index is needed.
     */
    public void loadSalonImpact(String salonId, ImpactCallback callback) {
        db.collection("bookings")
                .whereEqualTo("salonID", salonId)
                .whereEqualTo("isDeleted", false)
                .get()
                .addOnSuccessListener(query -> {
                    Date now = new Date();
                    List<String> bookingIds = new ArrayList<>();
                    for (DocumentSnapshot booking : query.getDocuments()) {
                        String status = booking.getString("status");
                        Timestamp when = booking.getTimestamp("dateTime");
                        boolean active = "pending".equals(status) || "confirmed".equals(status);
                        boolean upcoming = when != null && !when.toDate().before(now);
                        if (active && upcoming) bookingIds.add(booking.getId());
                    }

                    List<String> salonIds = new ArrayList<>();
                    salonIds.add(salonId);
                    callback.onSuccess(new Impact(salonIds, bookingIds));
                })
                .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
    }

    /**
     * Closes one salon and leaves the owner's account and other salons alone: its upcoming
     * bookings are cancelled (clients see them as Cancelled), then the salon is flagged
     * isDeleted so clients can no longer find or book it. Nothing is erased, and the steps are
     * safe to repeat if one fails.
     */
    public void closeSalon(String salonId, CloseCallback callback) {
        loadSalonImpact(salonId, new ImpactCallback() {
            @Override
            public void onSuccess(Impact impact) {
                List<Task<Void>> cancellations = new ArrayList<>();
                for (String bookingId : impact.bookingIds) {
                    // Same as an owner declining: status only, so the client still sees "Cancelled".
                    cancellations.add(db.collection("bookings").document(bookingId)
                            .update("status", "cancelled"));
                }

                Tasks.whenAll(cancellations)
                        .addOnSuccessListener(unused -> {
                            Map<String, Object> flag = new HashMap<>();
                            flag.put("isDeleted", true);
                            flag.put("deletedAt", FieldValue.serverTimestamp());

                            db.collection("salons").document(salonId).update(flag)
                                    .addOnSuccessListener(done -> callback.onSuccess())
                                    .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
                        })
                        .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
            }

            @Override
            public void onFailure(String errorMessage) {
                callback.onFailure(errorMessage);
            }
        });
    }
}