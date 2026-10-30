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
        void onAlreadyReviewed();
    }

    public interface ReviewListCallback {
        void onSuccess(List<Map<String, Object>> reviews, boolean fromCache);
        void onFailure(String errorMessage);
    }

    public interface ReviewExistsCallback {
        void onResult(boolean reviewed);
        void onFailure(String errorMessage);
    }

    public void createReview(String bookingId, String clientId, String salonId,
                             int rating, String comment, ReviewCallback callback) {
        isBookingReviewed(bookingId, new ReviewExistsCallback() {
            @Override
            public void onResult(boolean reviewed) {
                if (reviewed) {
                    callback.onAlreadyReviewed();
                    return;
                }
                writeReview(bookingId, clientId, salonId, rating, comment, callback);
            }

            @Override
            public void onFailure(String errorMessage) {
                callback.onFailure(errorMessage);
            }
        });
    }

    /**
     * True when this booking already has a review that is not soft-deleted.
     * The booking id doubles as the review document id, so a new review can
     * never create a second document for the same booking.
     */
    public void isBookingReviewed(String bookingId, ReviewExistsCallback callback) {
        db.collection("reviews")
                .whereEqualTo("bookingID", bookingId)
                .get()
                .addOnSuccessListener(query -> {
                    for (QueryDocumentSnapshot doc : query) {
                        // Old reviews may be missing isDeleted; only an explicit
                        // true counts as deleted.
                        if (!Boolean.TRUE.equals(doc.get("isDeleted"))) {
                            callback.onResult(true);
                            return;
                        }
                    }
                    callback.onResult(false);
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