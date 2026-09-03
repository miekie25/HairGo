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
import com.hairgo.app.databinding.BottomSheetReportProblemBinding;
import com.hairgo.app.models.Report;

import java.util.UUID;

public class ReportProblemBottomSheet extends BottomSheetDialogFragment {

    private BottomSheetReportProblemBinding binding;
    private FirebaseAuth auth;
    private FirebaseFirestore db;

    public static ReportProblemBottomSheet newInstance() {
        return new ReportProblemBottomSheet();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = BottomSheetReportProblemBinding.inflate(inflater, container, false);
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
        binding.btnSubmitReport.setOnClickListener(v -> submitReport());
    }

    private void submitReport() {
        String description = binding.etProblemDescription.getText().toString().trim();

        if (description.isEmpty()) {
            binding.etProblemDescription.setError("Please describe the problem");
            return;
        }

        if (description.length() < 10) {
            binding.etProblemDescription.setError("Please provide more details (min 10 characters)");
            return;
        }

        if (auth.getCurrentUser() == null) {
            Toast.makeText(getContext(), "Please login first", Toast.LENGTH_SHORT).show();
            return;
        }
        String uid = auth.getCurrentUser().getUid();

        binding.btnSubmitReport.setEnabled(false);
        binding.btnSubmitReport.setText("Submitting...");

        db.collection("users").document(uid).get()
                .addOnSuccessListener(document -> {
                    String userName = "Anonymous";
                    String userEmail = "";
                    if (document != null && document.exists()) {
                        String fn = document.getString("fullName");
                        String em = document.getString("email");
                        if (fn != null) userName = fn;
                        if (em != null) userEmail = em;
                    }

                    Report report = new Report();
                    report.setReportId(UUID.randomUUID().toString());
                    report.setUserId(uid);
                    report.setUserName(userName);
                    report.setUserEmail(userEmail);
                    report.setDescription(description);
                    report.setStatus("pending");

                    db.collection("reports")
                            .document(report.getReportId())
                            .set(report)
                            .addOnSuccessListener(aVoid -> {
                                Toast.makeText(getContext(), "Report submitted. We'll look into it!", Toast.LENGTH_LONG).show();
                                dismiss();
                            })
                            .addOnFailureListener(e -> {
                                Toast.makeText(getContext(), "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                                binding.btnSubmitReport.setEnabled(true);
                                binding.btnSubmitReport.setText(R.string.submit_report);
                            });
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(getContext(), "Failed to get user info", Toast.LENGTH_SHORT).show();
                    binding.btnSubmitReport.setEnabled(true);
                    binding.btnSubmitReport.setText(R.string.submit_report);
                });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}