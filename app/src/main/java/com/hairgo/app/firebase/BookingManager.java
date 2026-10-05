package com.hairgo.app.firebase;

import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BookingManager {
    private FirebaseFirestore db;

    public BookingManager() {
        db = FirebaseFirestore.getInstance();
    }

    public interface BookingCallback {
        void onSuccess();
        void onFailure(String errorMessage);
    }

    /**
     * Used when creating a booking, so the caller learns the id Firestore
     * generated for the new document.
     */
    public interface BookingCreatedCallback {
        void onSuccess(String bookingId);
        void onFailure(String errorMessage);
    }

    public interface BookingDataCallback {
        void onSuccess(Map<String, Object> booking);
        void onFailure(String errorMessage);
    }

    public interface BookingListCallback {
        void onSuccess(List<Map<String, Object>> bookings);
        void onFailure(String errorMessage);
    }

    /**
     * Creates a booking for the logged-in client.
     *
     * <p>The salon and service names are stored on the booking as well as their
     * ids. Salon services are plain strings rather than documents, so the name
     * is the only way to label a booking, and keeping a copy here means the
     * bookings list can render in a single read.
     */
    public void createBooking(String clientId, String salonId, String salonName,
                              String serviceName, Date dateTime, BookingCreatedCallback callback) {
        createBooking(null, clientId, salonId, salonName, serviceName, dateTime, callback);
    }

    /**
     * Creates a booking, optionally writing to a known document id.
     *
     * <p>Passing a documentId turns this into an upsert: the named document is
     * replaced instead of a new one being created. The development seeder relies
     * on that, and on the replacement resetting status and isDeleted, so a
     * cancelled test booking comes back to life on the next run.
     */
    public void createBooking(String documentId, String clientId, String salonId, String salonName,
                              String serviceName, Date dateTime, BookingCreatedCallback callback) {
        DocumentReference ref = documentId == null
                ? db.collection("bookings").document()
                : db.collection("bookings").document(documentId);

        Map<String, Object> booking = new HashMap<>();
        booking.put("bookingID", ref.getId());
        booking.put("clientID", clientId);
        booking.put("salonID", salonId);
        booking.put("salonName", salonName);
        booking.put("serviceName", serviceName);
        booking.put("dateTime", dateTime);
        booking.put("status", "pending");
        booking.put("isDeleted", false);

        ref.set(booking)
                .addOnSuccessListener(unused -> callback.onSuccess(ref.getId()))
                .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
    }

    /**
     * Reads one booking by id.
     *
     * <p>A soft-deleted booking is reported as not found. Cancelling flips
     * isDeleted rather than removing the document, so without this check a
     * cancelled booking can still be opened from recents and cancelled again.
     */
    public void getBookingById(String bookingId, BookingDataCallback callback) {
        db.collection("bookings").document(bookingId).get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) {
                        callback.onFailure("Booking not found.");
                        return;
                    }
                    Map<String, Object> booking = doc.getData();
                    Boolean deleted = booking == null ? null : (Boolean) booking.get("isDeleted");
                    if (Boolean.TRUE.equals(deleted)) {
                        callback.onFailure("Booking not found.");
                        return;
                    }
                    callback.onSuccess(booking);
                })
                .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
    }

    public void getBookingsForClient(String clientId, BookingListCallback callback) {
        db.collection("bookings")
                .whereEqualTo("clientID", clientId)
                .whereEqualTo("isDeleted", false)
                .get()
                .addOnSuccessListener(query -> {
                    List<Map<String, Object>> bookings = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : query) bookings.add(doc.getData());
                    callback.onSuccess(bookings);
                })
                .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
    }

    public void getBookingsForSalon(String salonId, BookingListCallback callback) {
        db.collection("bookings")
                .whereEqualTo("salonID", salonId)
                .whereEqualTo("isDeleted", false)
                .get()
                .addOnSuccessListener(query -> {
                    List<Map<String, Object>> bookings = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : query) bookings.add(doc.getData());
                    callback.onSuccess(bookings);
                })
                .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
    }

    /**
     * Returns the bookings a salon has for one specific day. Used to work out
     * which time slots are already taken before showing the time picker.
     *
     * <p>Requires a composite index on salonID + isDeleted + dateTime. Create it
     * in the Firebase console when the console reports a FAILED_PRECONDITION.
     */
    public void getBookingsForSalonOnDate(String salonId, Date dayStart, Date dayEnd,
                                          BookingListCallback callback) {
        db.collection("bookings")
                .whereEqualTo("salonID", salonId)
                .whereEqualTo("isDeleted", false)
                .whereGreaterThanOrEqualTo("dateTime", dayStart)
                .whereLessThan("dateTime", dayEnd)
                .get()
                .addOnSuccessListener(query -> {
                    List<Map<String, Object>> bookings = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : query) bookings.add(doc.getData());
                    callback.onSuccess(bookings);
                })
                .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
    }

    /**
     * Returns every booking a salon has from the given moment onwards, so a
     * screen can work out which coming days still have room.
     *
     * <p>Shares the composite index on salonID + isDeleted + dateTime with
     * getBookingsForSalonOnDate.
     */
    public void getFutureBookingsForSalon(String salonId, Date fromDate, BookingListCallback callback) {
        db.collection("bookings")
                .whereEqualTo("salonID", salonId)
                .whereEqualTo("isDeleted", false)
                .whereGreaterThanOrEqualTo("dateTime", fromDate)
                .get()
                .addOnSuccessListener(query -> {
                    List<Map<String, Object>> bookings = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : query) bookings.add(doc.getData());
                    callback.onSuccess(bookings);
                })
                .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
    }

    public void updateBookingStatus(String bookingId, String newStatus, BookingCallback callback) {
        db.collection("bookings").document(bookingId).update("status", newStatus)
                .addOnSuccessListener(unused -> callback.onSuccess())
                .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
    }

    public void softDeleteBooking(String bookingId, BookingCallback callback) {
        db.collection("bookings").document(bookingId).update("isDeleted", true)
                .addOnSuccessListener(unused -> callback.onSuccess())
                .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
    }

    /**
     * Cancels a booking by writing the cancelled status and the soft-delete flag
     * in a single update.
     *
     * <p>Both fields go in one write so a booking can never end up hidden from
     * the client's list while still reading as pending to the owner, and so a
     * failure part way through cannot leave it half cancelled.
     */
    public void cancelBooking(String bookingId, BookingCallback callback) {
        Map<String, Object> update = new HashMap<>();
        update.put("status", "cancelled");
        update.put("isDeleted", true);

        db.collection("bookings").document(bookingId).update(update)
                .addOnSuccessListener(unused -> callback.onSuccess())
                .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
    }
}