package com.hairgo.app.fragments;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.firebase.auth.FirebaseAuth;
import com.hairgo.app.R;
import com.hairgo.app.databinding.BottomSheetLeaveReviewBinding;
import com.hairgo.app.firebase.ReviewManager;

/**
 * Collects a star rating and a comment for one completed booking.
 *
 * <p>The write goes through ReviewManager rather than Firestore directly, so the
 * stored field names are the same ones the manager reads back. The booking and
 * salon ids arrive as arguments because a review is meaningless without them:
 * the salon id is what links the review to a rating average.
 */
public class LeaveReviewBottomSheet extends BottomSheetDialogFragment {

    private static final String ARG_BOOKING_ID = "bookingId";
    private static final String ARG_SALON_ID = "salonId";
    private static final String TAG = "LeaveReview";

    private BottomSheetLeaveReviewBinding binding;

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
        binding.btnSubmitReview.setOnClickListener(v -> submitReview());
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
            Toast.makeText(getContext(), R.string.review_rating_required, Toast.LENGTH_SHORT).show();
            return;
        }

        if (comment.isEmpty()) {
            binding.etReviewComment.setError(getString(R.string.review_comment_required));
            return;
        }

        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            Toast.makeText(getContext(), R.string.review_login_required, Toast.LENGTH_SHORT).show();
            return;
        }
        String clientId = FirebaseAuth.getInstance().getCurrentUser().getUid();

        binding.btnSubmitReview.setEnabled(false);
        binding.btnSubmitReview.setText(R.string.review_submitting);

        new ReviewManager().createReview(
                bookingId,
                clientId,
                salonId,
                Math.round(rating),
                comment,
                new ReviewManager.ReviewCallback() {
                    @Override
                    public void onSuccess() {
                        if (binding == null) return;
                        Toast.makeText(getContext(),
                                R.string.review_submitted, Toast.LENGTH_SHORT).show();
                        dismiss();
                    }

                    @Override
                    public void onFailure(String errorMessage) {
                        Log.w(TAG, "createReview failed: " + errorMessage);
                        if (binding == null) return;
                        resetSubmitButton();
                        Toast.makeText(getContext(),
                                getString(R.string.review_submit_failed, errorMessage),
                                Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void resetSubmitButton() {
        binding.btnSubmitReview.setEnabled(true);
        binding.btnSubmitReview.setText(R.string.submit_review);
    }

    /** Returns null when the sheet was opened without a booking, which cannot happen. */
    private String requireArgument(String key) {
        Bundle args = getArguments();
        String value = args == null ? null : args.getString(key);
        if (value == null || value.isEmpty()) {
            Toast.makeText(getContext(), R.string.review_missing_booking, Toast.LENGTH_LONG).show();
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
