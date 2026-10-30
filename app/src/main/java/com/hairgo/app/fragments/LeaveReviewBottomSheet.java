package com.hairgo.app.fragments;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.firebase.auth.FirebaseAuth;
import com.hairgo.app.R;
import com.hairgo.app.databinding.BottomSheetLeaveReviewBinding;
import com.hairgo.app.firebase.ReviewManager;
import com.hairgo.app.utils.Snackbars;

/**
 * Collects a star rating and a comment for one completed booking.
 *
 * <p>The write goes through ReviewManager rather than Firestore directly, so the
 * stored field names are the same ones the manager reads back. The booking and
 * salon ids arrive as arguments because a review is meaningless without them:
 * the salon id is what links the review to a rating average.
 */
public class LeaveReviewBottomSheet extends BottomSheetDialogFragment {

    public static final String RESULT_REVIEW_SUBMITTED = "review_submitted";

    private static final String ARG_BOOKING_ID = "bookingId";
    private static final String ARG_SALON_ID = "salonId";
    private static final String TAG = "LeaveReview";
    private static final String LABEL_UPDATE_REVIEW = "Update Review";
    private static final String MSG_REVIEW_UPDATED = "Review updated.";

    private BottomSheetLeaveReviewBinding binding;
    private ReviewManager reviewManager;
    private ReviewManager.ReviewData existingReview;

    public static LeaveReviewBottomSheet newInstance(String bookingId, String salonId) {
        LeaveReviewBottomSheet sheet = new LeaveReviewBottomSheet();
        Bundle args = new Bundle();
        args.putString(ARG_BOOKING_ID, bookingId);
        args.putString(ARG_SALON_ID, salonId);
        sheet.setArguments(args);
        return sheet;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = BottomSheetLeaveReviewBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        reviewManager = new ReviewManager();
        binding.btnSubmitReview.setOnClickListener(v -> submitReview());
        loadExistingReview();
    }

    private void submitReview() {
        String bookingId = requireArgument(ARG_BOOKING_ID);
        String salonId = requireArgument(ARG_SALON_ID);
        if (bookingId == null || salonId == null) {
            return;
        }

        float rating = binding.ratingBar.getRating();
        String comment = binding.etReviewComment.getText().toString().trim();

        if (rating == 0f) {
            Snackbars.show(requireView(), R.string.review_rating_required);
            return;
        }

        if (comment.isEmpty()) {
            binding.etReviewComment.setError(getString(R.string.review_comment_required));
            return;
        }

        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            Snackbars.show(requireView(), R.string.review_login_required);
            return;
        }
        String clientId = FirebaseAuth.getInstance().getCurrentUser().getUid();

        binding.btnSubmitReview.setEnabled(false);
        binding.btnSubmitReview.setText(R.string.review_submitting);

        boolean editing = existingReview != null;
        reviewManager.saveReview(
                bookingId,
                clientId,
                salonId,
                Math.round(rating),
                comment,
                new ReviewManager.ReviewCallback() {
                    @Override
                    public void onSuccess() {
                        if (binding == null) return;
                        getParentFragmentManager().setFragmentResult(
                                RESULT_REVIEW_SUBMITTED, new Bundle());
                        // Anchor to the activity so the pill stays after the sheet closes.
                        Snackbars.show(requireActivity().findViewById(android.R.id.content),
                                editing ? MSG_REVIEW_UPDATED : getString(R.string.review_submitted));
                        dismiss();
                    }

                    @Override
                    public void onFailure(String errorMessage) {
                        Log.w(TAG, "saveReview failed: " + errorMessage);
                        if (binding == null) return;
                        resetSubmitButton();
                        Snackbars.show(requireView(),
                                getString(R.string.review_submit_failed, errorMessage));
                    }
                });
    }

    /** Prefills the form when this booking already has a review. */
    private void loadExistingReview() {
        String bookingId = requireArgument(ARG_BOOKING_ID);
        if (bookingId == null) return;

        reviewManager.getReviewForBooking(bookingId, new ReviewManager.ReviewDataCallback() {
            @Override
            public void onSuccess(ReviewManager.ReviewData review) {
                if (binding == null || review == null) return;
                existingReview = review;
                binding.ratingBar.setRating(review.rating);
                binding.etReviewComment.setText(review.comment);
                binding.btnSubmitReview.setText(LABEL_UPDATE_REVIEW);
            }

            @Override
            public void onFailure(String errorMessage) {
                Log.w(TAG, "Could not load review: " + errorMessage);
            }
        });
    }

    private void resetSubmitButton() {
        binding.btnSubmitReview.setEnabled(true);
        if (existingReview == null) {
            binding.btnSubmitReview.setText(R.string.submit_review);
        } else {
            binding.btnSubmitReview.setText(LABEL_UPDATE_REVIEW);
        }
    }

    /** Returns null when the sheet was opened without a booking, which cannot happen. */
    private String requireArgument(String key) {
        Bundle args = getArguments();
        String value = args == null ? null : args.getString(key);
        if (value == null || value.isEmpty()) {
            // Anchor to the activity so the pill stays after the sheet closes.
            Snackbars.show(requireActivity().findViewById(android.R.id.content),
                    R.string.review_missing_booking);
            dismiss();
            return null;
        }
        return value;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
