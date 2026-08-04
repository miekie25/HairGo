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

    public interface BookingDataCallback {
        void onSuccess(Map<String, Object> booking);
        void onFailure(String errorMessage);
    }

    public interface BookingListCallback {
        void onSuccess(List<Map<String, Object>> bookings);
        void onFailure(String errorMessage);
    }

    public void createBooking(String clientId, String salonId, String serviceId,
                              Date dateTime, BookingCallback callback) {
        DocumentReference ref = db.collection("bookings").document();

        Map<String, Object> booking = new HashMap<>();
        booking.put("bookingID", ref.getId());
        booking.put("clientID", clientId);
        booking.put("salonID", salonId);
        booking.put("serviceID", serviceId);
        booking.put("dateTime", dateTime);
        booking.put("status", "pending");
        booking.put("isDeleted", false);

        ref.set(booking)
                .addOnSuccessListener(unused -> callback.onSuccess())
                .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
    }

    public void getBookingById(String bookingId, BookingDataCallback callback) {
        db.collection("bookings").document(bookingId).get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) callback.onSuccess(doc.getData());
                    else callback.onFailure("Booking not found.");
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
}