package com.hairgo.app.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.hairgo.app.R;
import com.hairgo.app.databinding.BottomSheetLeaveReviewBinding;
import com.hairgo.app.models.Review;

import java.util.UUID;

public class LeaveReviewBottomSheet extends BottomSheetDialogFragment {

    private BottomSheetLeaveReviewBinding binding;
    private FirebaseAuth auth;
    private FirebaseFirestore db;

    public static LeaveReviewBottomSheet newInstance() {
        return new LeaveReviewBottomSheet();
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
        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        setupClickListeners();
    }

    private void setupClickListeners() {
        binding.btnSubmitReview.setOnClickListener(v -> submitReview());
    }

    private void submitReview() {
        float rating = binding.ratingBar.getRating();
        String comment = binding.etReviewComment.getText().toString().trim();

        if (rating == 0f) {
            Toast.makeText(getContext(), "Please select a star rating", Toast.LENGTH_SHORT).show();
            return;
        }

        if (comment.isEmpty()) {
            binding.etReviewComment.setError("Please enter a comment");
            return;
        }

        if (auth.getCurrentUser() == null) {
            Toast.makeText(getContext(), "Please login first", Toast.LENGTH_SHORT).show();
            return;
        }
        String uid = auth.getCurrentUser().getUid();

        binding.btnSubmitReview.setEnabled(false);
        binding.btnSubmitReview.setText("Submitting...");

        db.collection("users").document(uid).get()
                .addOnSuccessListener(document -> {
                    String userName = "Anonymous";
                    if (document != null && document.exists()) {
                        String fn = document.getString("fullName");
                        if (fn != null) userName = fn;
                    }

                    Review review = new Review();
                    review.setReviewId(UUID.randomUUID().toString());
                    review.setUserId(uid);
                    review.setUserName(userName);
                    review.setRating(rating);
                    review.setComment(comment);

                    db.collection("reviews")
                            .document(review.getReviewId())
                            .set(review)
                            .addOnSuccessListener(aVoid -> {
                                Toast.makeText(getContext(), "Review submitted! Thank you.", Toast.LENGTH_SHORT).show();
                                dismiss();
                            })
                            .addOnFailureListener(e -> {
                                Toast.makeText(getContext(), "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                                binding.btnSubmitReview.setEnabled(true);
                                binding.btnSubmitReview.setText(R.string.submit_review);
                            });
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(getContext(), "Failed to get user info", Toast.LENGTH_SHORT).show();
                    binding.btnSubmitReview.setEnabled(true);
                    binding.btnSubmitReview.setText(R.string.submit_review);
                });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}