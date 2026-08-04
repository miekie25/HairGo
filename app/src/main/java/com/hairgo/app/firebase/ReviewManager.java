package com.hairgo.app.firebase;

import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ReviewManager {
    private FirebaseFirestore db;

    public ReviewManager() {
        db = FirebaseFirestore.getInstance();
    }

    public interface ReviewCallback {
        void onSuccess();
        void onFailure(String errorMessage);
    }

    public interface ReviewListCallback {
        void onSuccess(List<Map<String, Object>> reviews);
        void onFailure(String errorMessage);
    }

    public void createReview(String bookingId, String clientId, String salonId,
                             int rating, String comment, ReviewCallback callback) {
        DocumentReference ref = db.collection("reviews").document();

        Map<String, Object> review = new HashMap<>();
        review.put("reviewID", ref.getId());
        review.put("bookingID", bookingId);
        review.put("clientID", clientId);
        review.put("salonID", salonId);
        review.put("rating", rating);
        review.put("comment", comment);
        review.put("createdAt", FieldValue.serverTimestamp());
        review.put("isDeleted", false);

        ref.set(review)
                .addOnSuccessListener(unused -> {
                    recalculateSalonRating(salonId);
                    callback.onSuccess();
                })
                .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
    }

    public void getReviewsForSalon(String salonId, ReviewListCallback callback) {
        db.collection("reviews")
                .whereEqualTo("salonID", salonId)
                .whereEqualTo("isDeleted", false)
                .get()
                .addOnSuccessListener(query -> {
                    List<Map<String, Object>> reviews = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : query) reviews.add(doc.getData());
                    callback.onSuccess(reviews);
                })
                .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
    }

    public void softDeleteReview(String reviewId, String salonId, ReviewCallback callback) {
        db.collection("reviews").document(reviewId).update("isDeleted", true)
                .addOnSuccessListener(unused -> {
                    recalculateSalonRating(salonId);
                    callback.onSuccess();
                })
                .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
    }

    // Keeps the salon's avgRating in sync whenever a review is added or removed
    private void recalculateSalonRating(String salonId) {
        db.collection("reviews")
                .whereEqualTo("salonID", salonId)
                .whereEqualTo("isDeleted", false)
                .get()
                .addOnSuccessListener(query -> {
                    int count = query.size();
                    double total = 0;
                    for (QueryDocumentSnapshot doc : query) {
                        Long rating = doc.getLong("rating");
                        if (rating != null) total += rating;
                    }
                    double avgRating = count > 0 ? total / count : 0.0;

                    Map<String, Object> update = new HashMap<>();
                    update.put("avgRating", avgRating);

                    db.collection("salons").document(salonId).update(update);
                });
    }
}