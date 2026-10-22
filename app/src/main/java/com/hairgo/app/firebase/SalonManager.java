package com.hairgo.app.firebase;

import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.hairgo.app.models.ServiceDetail;

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

    /**
     * Used when creating a salon, so the caller learns the id Firestore
     * generated for the new document.
     */
    public interface SalonCreatedCallback {
        void onSuccess(String salonId);
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

    /**
     * Reads the services (with price and duration) from a salon map.
     * If the salon has no saved details yet, they are built from the old
     * list of names (no price, 60 minutes). Never returns null.
     */
    public static List<ServiceDetail> getServiceDetails(Map<String, Object> salonData) {
        if (salonData == null) {
            return new ArrayList<>();
        }
        List<ServiceDetail> details = ServiceDetail.fromRawList(salonData.get("serviceDetails"));
        if (!details.isEmpty()) {
            return details;
        }
        // No details saved, so fall back to the old list of names.
        return ServiceDetail.fromRawList(salonData.get("services"));
    }

    // Old way of creating a salon, with just service names. Used by the seeder.
    public void createSalon(String ownerId, String name, String location,
                            List<String> services, SalonCreatedCallback callback) {
        createSalon(null, ownerId, name, location, services, callback);
    }

    /**
     * Creates a salon, optionally writing to a known document id.
     *
     * <p>Passing a documentId turns this into an upsert: the named document is
     * replaced instead of a new one being created. The development seeder relies
     * on that so it can reset its own test data without ever producing
     * duplicates, and it needs no separate delete step that could fail quietly.
     */
    public void createSalon(String documentId, String ownerId, String name, String location,
                            List<String> services, SalonCreatedCallback callback) {
        // Turn the names into services with no price and the default duration.
        saveNewSalon(documentId, ownerId, name, location,
                ServiceDetail.fromNames(services), callback);
    }

    /**
     * Creates a salon with services that have a price and a duration.
     * Both the new details and the old list of names are saved.
     */
    public void createSalonWithDetails(String ownerId, String name, String location,
                                       List<ServiceDetail> serviceDetails,
                                       SalonCreatedCallback callback) {
        saveNewSalon(null, ownerId, name, location, serviceDetails, callback);
    }

    // Shared code that writes a new salon document.
    private void saveNewSalon(String documentId, String ownerId, String name, String location,
                              List<ServiceDetail> serviceDetails, SalonCreatedCallback callback) {
        DocumentReference ref = documentId == null
                ? db.collection("salons").document()
                : db.collection("salons").document(documentId);

        Map<String, Object> salon = new HashMap<>();
        salon.put("salonID", ref.getId());
        salon.put("ownerID", ownerId);
        salon.put("name", name);
        salon.put("location", location);
        // The old list of names is still saved, because other screens read it.
        salon.put("services", ServiceDetail.toNames(serviceDetails));
        // The new list holds the name, price and duration of each service.
        salon.put("serviceDetails", ServiceDetail.toMapList(serviceDetails));
        salon.put("isDeleted", false);
        salon.put("avgRating", 0.0);

        ref.set(salon)
                .addOnSuccessListener(unused -> callback.onSuccess(ref.getId()))
                .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
    }

    /**
     * Saves a new list of services for a salon. This writes the old list of
     * names and the new details together, so they always match.
     */
    public void updateServices(String salonId, List<ServiceDetail> serviceDetails,
                               SalonCallback callback) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("services", ServiceDetail.toNames(serviceDetails));
        updates.put("serviceDetails", ServiceDetail.toMapList(serviceDetails));
        updateSalon(salonId, updates, callback);
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
                    for (QueryDocumentSnapshot doc : query) {
                        // Use the real document id, never a possibly-stale
                        // salonID field, so ids stay consistent everywhere.
                        Map<String, Object> data = new HashMap<>(doc.getData());
                        data.put("salonID", doc.getId());
                        salons.add(data);
                    }
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
                    for (QueryDocumentSnapshot doc : query) {
                        // Use the real document id, never a possibly-stale
                        // salonID field, so ids stay consistent everywhere.
                        Map<String, Object> data = new HashMap<>(doc.getData());
                        data.put("salonID", doc.getId());
                        salons.add(data);
                    }
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