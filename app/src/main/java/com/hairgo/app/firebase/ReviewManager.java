package com.hairgo.app.firebase;

import android.util.Log;

import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ReviewManager {
    private static final String TAG = "ReviewManager";

    private FirebaseFirestore db;

    public ReviewManager() {
        db = FirebaseFirestore.getInstance();
    }

    public interface ReviewCallback {
        void onSuccess();
        void onFailure(String errorMessage);
    }

    public interface ReviewListCallback {
        void onSuccess(List<Map<String, Object>> reviews, boolean fromCache);
        void onFailure(String errorMessage);
    }

    public interface ReviewExistsCallback {
        void onResult(boolean reviewed);
        void onFailure(String errorMessage);
    }

    public interface ReviewDataCallback {
        void onSuccess(ReviewData review);
        void onFailure(String errorMessage);
    }

    /** One active review as stored, with the id needed to update it. */
    public static class ReviewData {
        public final String documentId;
        public final int rating;
        public final String comment;

        ReviewData(String documentId, int rating, String comment) {
            this.documentId = documentId;
            this.rating = rating;
            this.comment = comment;
        }
    }

    /**
     * Saves the client's review for a booking. An existing active review is
     * updated in place, so a booking can never end up with two reviews.
     */
    public void saveReview(String bookingId, String clientId, String salonId,
                           int rating, String comment, ReviewCallback callback) {
        getReviewForBooking(bookingId, new ReviewDataCallback() {
            @Override
            public void onSuccess(ReviewData existing) {
                if (existing == null) {
                    writeReview(bookingId, clientId, salonId, rating, comment, callback);
                } else {
                    updateReview(existing.documentId, salonId, rating, comment, callback);
                }
            }

            @Override
            public void onFailure(String errorMessage) {
                callback.onFailure(errorMessage);
            }
        });
    }

    /** Returns the booking's active review, or null when it has none. */
    public void getReviewForBooking(String bookingId, ReviewDataCallback callback) {
        db.collection("reviews")
                .whereEqualTo("bookingID", bookingId)
                .get()
                .addOnSuccessListener(query -> {
                    for (QueryDocumentSnapshot doc : query) {
                        // Old reviews may be missing isDeleted; only an explicit
                        // true counts as deleted.
                        if (Boolean.TRUE.equals(doc.get("isDeleted"))) continue;

                        Object ratingValue = doc.get("rating");
                        int rating = ratingValue instanceof Number
                                ? ((Number) ratingValue).intValue() : 0;
                        String comment = doc.getString("comment");
                        callback.onSuccess(new ReviewData(
                                doc.getId(), rating, comment == null ? "" : comment));
                        return;
                    }
                    callback.onSuccess(null);
                })
                .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
    }

    public void isBookingReviewed(String bookingId, ReviewExistsCallback callback) {
        getReviewForBooking(bookingId, new ReviewDataCallback() {
            @Override
            public void onSuccess(ReviewData review) {
                callback.onResult(review != null);
            }

            @Override
            public void onFailure(String errorMessage) {
                callback.onFailure(errorMessage);
            }
        });
    }

    private void updateReview(String documentId, String salonId, int rating,
                              String comment, ReviewCallback callback) {
        Map<String, Object> update = new HashMap<>();
        update.put("rating", rating);
        update.put("comment", comment);
        update.put("edited", true);
        update.put("updatedAt", FieldValue.serverTimestamp());

        db.collection("reviews").document(documentId).update(update)
                .addOnSuccessListener(unused -> {
                    recalculateSalonRating(salonId);
                    callback.onSuccess();
                })
                .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
    }

    private void writeReview(String bookingId, String clientId, String salonId,
                             int rating, String comment, ReviewCallback callback) {
        DocumentReference ref = db.collection("reviews").document(bookingId);

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
                    callback.onSuccess(reviews, query.getMetadata().isFromCache());
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
                    double total = 0;
                    int ratedCount = 0;
                    for (QueryDocumentSnapshot doc : query) {
                        // Ratings may be stored as a whole number or a decimal,
                        // and an old review may have no rating at all.
                        Object ratingValue = doc.get("rating");
                        if (ratingValue instanceof Number) {
                            total += ((Number) ratingValue).doubleValue();
                            ratedCount++;
                        }
                    }
                    double avgRating = ratedCount > 0 ? total / ratedCount : 0.0;

                    updateSalonRating(salonId, avgRating);
                })
                .addOnFailureListener(e ->
                        Log.e(TAG, "Could not read reviews for salon " + salonId, e));
    }

    /**
     * Saves the salon's average rating. This is the only field a client may
     * change, so the salon profile can also use it to repair a stale value.
     */
    public void updateSalonRating(String salonId, double avgRating) {
        Map<String, Object> update = new HashMap<>();
        update.put("avgRating", avgRating);

        // The write is checked rather than fire-and-forget so a rejected update
        // (for example a security-rules denial) shows up in the log instead of
        // failing silently.
        db.collection("salons").document(salonId).update(update)
                .addOnSuccessListener(unused ->
                        Log.d(TAG, "avgRating=" + avgRating
                                + " saved for salon " + salonId))
                .addOnFailureListener(e ->
                        Log.e(TAG, "avgRating save FAILED for salon "
                                + salonId, e));
    }
}