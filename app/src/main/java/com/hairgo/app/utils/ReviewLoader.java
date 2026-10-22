package com.hairgo.app.utils;

import com.google.firebase.Timestamp;
import com.hairgo.app.firebase.OwnerManager;
import com.hairgo.app.firebase.ReviewManager;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads one salon's reviews and turns the raw Firestore maps into small display
 * rows. The list is sorted newest first in memory, so the existing query needs
 * no extra Firestore index. Old reviews may be missing any field, so every
 * value falls back to something safe.
 */
public final class ReviewLoader {

    public interface Callback {
        void onLoaded(List<Item> reviews);
        void onFailure(String errorMessage);
    }

    /** One review ready for a card. */
    public static class Item {
        private final float rating;
        private final String comment;
        private final String clientId;
        private final Date createdAt;
        private String reviewerName;

        Item(float rating, String comment, String clientId, String reviewerName, Date createdAt) {
            this.rating = rating;
            this.comment = comment;
            this.clientId = clientId;
            this.reviewerName = reviewerName;
            this.createdAt = createdAt;
        }

        public float getRating() { return rating; }
        public String getComment() { return comment; }
        public String getReviewerName() { return reviewerName; }
        public Date getCreatedAt() { return createdAt; }

        String getClientId() { return clientId; }

        void setReviewerName(String reviewerName) { this.reviewerName = reviewerName; }

        long sortTime() { return createdAt == null ? Long.MIN_VALUE : createdAt.getTime(); }
    }

    private ReviewLoader() {}

    public static void load(String salonId, Callback callback) {
        new ReviewManager().getReviewsForSalon(salonId, new ReviewManager.ReviewListCallback() {
            @Override
            public void onSuccess(List<Map<String, Object>> rawReviews) {
                List<Item> reviews = parse(rawReviews);

                // Newest first. Reviews without a date go to the end.
                reviews.sort((a, b) -> Long.compare(b.sortTime(), a.sortTime()));

                resolveReviewerNames(reviews, callback);
            }

            @Override
            public void onFailure(String errorMessage) {
                callback.onFailure(errorMessage);
            }
        });
    }

    private static List<Item> parse(List<Map<String, Object>> rawReviews) {
        List<Item> items = new ArrayList<>();
        if (rawReviews == null) {
            return items;
        }

        for (Map<String, Object> raw : rawReviews) {
            if (raw == null) continue;
            // The query already skips these, but old data is checked again.
            if (Boolean.TRUE.equals(raw.get("isDeleted"))) continue;

            Double ratingValue = asNumber(raw.get("rating"));
            float rating = ratingValue == null
                    ? 0f
                    : (float) Math.max(0, Math.min(5, ratingValue));

            String storedName = asString(raw.get("userName"));
            if (storedName.isEmpty()) {
                storedName = asString(raw.get("clientName"));
            }
            if (!storedName.isEmpty()) {
                storedName = firstWord(storedName);
            }

            String clientId = asString(raw.get("clientID"));
            if (clientId.isEmpty()) {
                // Some old reviews used a different field name.
                clientId = asString(raw.get("userId"));
            }

            Date createdAt = asDate(raw.get("createdAt"));
            if (createdAt == null) {
                createdAt = asDate(raw.get("timestamp"));
            }

            items.add(new Item(rating, asString(raw.get("comment")), clientId,
                    storedName, createdAt));
        }

        return items;
    }

    /**
     * Reviews store the writer's id, not their name. Look up each different
     * writer once, and use "Client" when there is no profile or no name.
     */
    private static void resolveReviewerNames(List<Item> reviews, Callback callback) {
        List<String> userIds = new ArrayList<>();
        for (Item review : reviews) {
            String id = review.getClientId();
            if (!review.getReviewerName().isEmpty() || id.isEmpty()) continue;
            if (!userIds.contains(id)) {
                userIds.add(id);
            }
        }

        if (userIds.isEmpty()) {
            callback.onLoaded(reviews);
            return;
        }

        Map<String, String> names = new HashMap<>();
        int[] remaining = {userIds.size()};
        OwnerManager ownerManager = new OwnerManager();

        for (String userId : userIds) {
            ownerManager.getClientName(userId, new OwnerManager.OwnerCallback<String>() {
                @Override
                public void onSuccess(String fullName) {
                    names.put(userId, firstWord(fullName));
                    if (--remaining[0] == 0) {
                        applyNames(reviews, names);
                        callback.onLoaded(reviews);
                    }
                }

                @Override
                public void onFailure(String errorMessage) {
                    // A missing name is not worth failing the whole list.
                    names.put(userId, "");
                    if (--remaining[0] == 0) {
                        applyNames(reviews, names);
                        callback.onLoaded(reviews);
                    }
                }
            });
        }
    }

    private static void applyNames(List<Item> reviews, Map<String, String> names) {
        for (Item review : reviews) {
            if (!review.getReviewerName().isEmpty()) continue;
            String name = names.get(review.getClientId());
            if (name != null && !name.isEmpty()) {
                review.setReviewerName(name);
            }
        }
    }

    private static String firstWord(String text) {
        if (text == null) return "";
        String trimmed = text.trim();
        if (trimmed.isEmpty()) return "";
        return trimmed.split("\\s+")[0];
    }

    private static Double asNumber(Object value) {
        return value instanceof Number ? ((Number) value).doubleValue() : null;
    }

    private static String asString(Object value) {
        return value == null ? "" : value.toString().trim();
    }

    private static Date asDate(Object value) {
        if (value instanceof Timestamp) return ((Timestamp) value).toDate();
        if (value instanceof Date) return (Date) value;
        if (value instanceof Number) return new Date(((Number) value).longValue());
        return null;
    }
}
