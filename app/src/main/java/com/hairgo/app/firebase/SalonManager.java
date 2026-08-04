package com.hairgo.app.firebase;

import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SalonManager {
    private FirebaseFirestore db;

    public SalonManager() {
        db = FirebaseFirestore.getInstance();
    }

    public interface SalonCallback {
        void onSuccess();
        void onFailure(String errorMessage);
    }

    public interface SalonDataCallback {
        void onSuccess(Map<String, Object> salon);
        void onFailure(String errorMessage);
    }

    public interface SalonListCallback {
        void onSuccess(List<Map<String, Object>> salons);
        void onFailure(String errorMessage);
    }

    public void createSalon(String ownerId, String name, String location,
                            List<String> services, SalonCallback callback) {
        DocumentReference ref = db.collection("salons").document();

        Map<String, Object> salon = new HashMap<>();
        salon.put("salonID", ref.getId());
        salon.put("ownerID", ownerId);
        salon.put("name", name);
        salon.put("location", location);
        salon.put("services", services);
        salon.put("isDeleted", false);
        salon.put("avgRating", 0.0);

        ref.set(salon)
                .addOnSuccessListener(unused -> callback.onSuccess())
                .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
    }

    public void getSalonById(String salonId, SalonDataCallback callback) {
        db.collection("salons").document(salonId).get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) callback.onSuccess(doc.getData());
                    else callback.onFailure("Salon not found.");
                })
                .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
    }

    public void getAllSalons(SalonListCallback callback) {
        db.collection("salons").whereEqualTo("isDeleted", false).get()
                .addOnSuccessListener(query -> {
                    List<Map<String, Object>> salons = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : query) salons.add(doc.getData());
                    callback.onSuccess(salons);
                })
                .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
    }

    public void getSalonsByLocation(String location, SalonListCallback callback) {
        db.collection("salons")
                .whereEqualTo("isDeleted", false)
                .whereEqualTo("location", location)
                .get()
                .addOnSuccessListener(query -> {
                    List<Map<String, Object>> salons = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : query) salons.add(doc.getData());
                    callback.onSuccess(salons);
                })
                .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
    }

    public void updateSalon(String salonId, Map<String, Object> updates, SalonCallback callback) {
        db.collection("salons").document(salonId).update(updates)
                .addOnSuccessListener(unused -> callback.onSuccess())
                .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
    }

    public void softDeleteSalon(String salonId, SalonCallback callback) {
        db.collection("salons").document(salonId).update("isDeleted", true)
                .addOnSuccessListener(unused -> callback.onSuccess())
                .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
    }
}