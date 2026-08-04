package com.hairgo.app.firebase;

import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ReportManager {
    private FirebaseFirestore db;

    public ReportManager() {
        db = FirebaseFirestore.getInstance();
    }

    public interface ReportCallback {
        void onSuccess();
        void onFailure(String errorMessage);
    }

    public interface ReportListCallback {
        void onSuccess(List<Map<String, Object>> reports);
        void onFailure(String errorMessage);
    }

    public void createReport(String userId, String description, ReportCallback callback) {
        DocumentReference ref = db.collection("reports").document();

        Map<String, Object> report = new HashMap<>();
        report.put("reportID", ref.getId());
        report.put("userID", userId);
        report.put("description", description);
        report.put("createdAt", FieldValue.serverTimestamp());
        report.put("isDeleted", false);

        ref.set(report)
                .addOnSuccessListener(unused -> callback.onSuccess())
                .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
    }

    public void getAllReports(ReportListCallback callback) {
        db.collection("reports").whereEqualTo("isDeleted", false).get()
                .addOnSuccessListener(query -> {
                    List<Map<String, Object>> reports = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : query) reports.add(doc.getData());
                    callback.onSuccess(reports);
                })
                .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
    }

    public void softDeleteReport(String reportId, ReportCallback callback) {
        db.collection("reports").document(reportId).update("isDeleted", true)
                .addOnSuccessListener(unused -> callback.onSuccess())
                .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
    }
}