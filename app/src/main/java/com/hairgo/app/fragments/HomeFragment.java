package com.hairgo.app.fragments;

import android.content.Intent;
import android.location.Location;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.hairgo.app.R;
import com.hairgo.app.activities.ClientDashboardActivity;
import com.hairgo.app.activities.SalonProfileActivity;
import com.hairgo.app.adapters.SalonAdapter;
import com.hairgo.app.firebase.SalonManager;
import com.hairgo.app.models.Salon;
import com.hairgo.app.utils.FuzzySearch;
import com.hairgo.app.utils.SalonPhotos;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class HomeFragment extends Fragment {

    private static final String TAG = "HomeFragment";

    private RecyclerView rvSalons;
    private TextView tvEmptyState;
    private EditText etSearch;
    private View locationBanner;
    private MaterialButton btnAddLocation;
    private SalonAdapter adapter;

    // Full list from Firestore, kept so filtering does not refetch every keystroke.
    private final List<Salon> allSalons = new ArrayList<>();
    // What the adapter is currently showing.
    private final List<Salon> visibleSalons = new ArrayList<>();

    // The client's saved location. Both stay null for older accounts without
    // coordinates, or when the saved values are not usable.
    private Double clientLatitude;
    private Double clientLongitude;
    private boolean hasClientLocation;

    private SalonManager salonManager;

    // Nearest first. Salons without a usable distance go to the bottom so they
    // are still listed, just without a distance label.
    private static final Comparator<Salon> DISTANCE_ORDER = (first, second) -> {
        Double firstDistance = first.getDistanceMetres();
        Double secondDistance = second.getDistanceMetres();
        if (firstDistance == null && secondDistance == null) {
            return compareSalonKeys(first, second);
        }
        if (firstDistance == null) return 1;
        if (secondDistance == null) return -1;
        int byDistance = Double.compare(firstDistance, secondDistance);
        return byDistance != 0 ? byDistance : compareSalonKeys(first, second);
    };

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        rvSalons = view.findViewById(R.id.rvSalons);
        tvEmptyState = view.findViewById(R.id.tvEmptyState);
        etSearch = view.findViewById(R.id.etSearch);
        locationBanner = view.findViewById(R.id.locationBanner);
        btnAddLocation = view.findViewById(R.id.btnAddLocation);

        rvSalons.setLayoutManager(new LinearLayoutManager(getContext()));

        adapter = new SalonAdapter(visibleSalons, salon -> {
            Intent intent = new Intent(getContext(), SalonProfileActivity.class);
            intent.putExtra("salonId", salon.getSalonId());
            startActivity(intent);
        });
        rvSalons.setAdapter(adapter);

        if (btnAddLocation != null) {
            btnAddLocation.setOnClickListener(v -> openProfileTab());
        }

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                applyFilter(s.toString());
            }
        });

        salonManager = new SalonManager();

        loadSalons();
    }

    /**
     * The dashboard keeps this fragment alive and only hides it, so returning to
     * the Home tab has to re-read the salons or anything added since launch would
     * never show up. It also re-reads the client's location, so a location saved
     * on the Profile tab re-sorts the list straight away.
     */
    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (!hidden && salonManager != null) {
            loadSalons();
        }
    }

    /**
     * Reloads when the fragment becomes visible again, so a rating changed by a
     * review (or a new salon) is reflected without a manual refresh.
     */
    @Override
    public void onResume() {
        super.onResume();
        if (salonManager != null) {
            loadSalons();
        }
    }

    private void loadSalons() {
        loadClientLocation(this::fetchSalons);
    }

    /**
     * Reads the client's saved coordinates. Every value is read null-safe
     * because Firestore can store whole numbers as Long. Missing or invalid
     * coordinates simply mean the list stays in its normal order.
     */
    private void loadClientLocation(Runnable onDone) {
        FirebaseUser user = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            applyClientLocation(null, null);
            onDone.run();
            return;
        }

        FirebaseFirestore.getInstance().collection("users").document(user.getUid()).get()
                .addOnSuccessListener(documentSnapshot -> {
                    Double latitude = null;
                    Double longitude = null;
                    if (documentSnapshot != null && documentSnapshot.exists()) {
                        latitude = asCoordinate(documentSnapshot.get("latitude"), -90d, 90d);
                        longitude = asCoordinate(documentSnapshot.get("longitude"), -180d, 180d);
                    }
                    if (!isValidCoordinatePair(latitude, longitude)) {
                        latitude = null;
                        longitude = null;
                    }
                    applyClientLocation(latitude, longitude);
                    onDone.run();
                })
                .addOnFailureListener(e -> {
                    Log.w(TAG, "Could not load client location: " + e.getMessage());
                    applyClientLocation(null, null);
                    onDone.run();
                });
    }

    private void applyClientLocation(Double latitude, Double longitude) {
        boolean valid = isValidCoordinatePair(latitude, longitude);
        clientLatitude = valid ? latitude : null;
        clientLongitude = valid ? longitude : null;
        hasClientLocation = valid;

        if (locationBanner != null && isAdded()) {
            locationBanner.setVisibility(valid ? View.GONE : View.VISIBLE);
        }
    }

    private void openProfileTab() {
        if (getActivity() instanceof ClientDashboardActivity) {
            ((ClientDashboardActivity) getActivity()).navigateToTab(R.id.nav_profile);
        }
    }

    private void fetchSalons() {
        if (!isAdded()) return;

        salonManager.getAllSalons(new SalonManager.SalonListCallback() {
            @Override
            public void onSuccess(List<Map<String, Object>> salons) {
                if (!isAdded()) return;
                if (salons == null || salons.isEmpty()) {
                    Log.w(TAG, "No salons in Firestore yet - showing the empty state.");
                    showSalons(Collections.emptyList());
                    return;
                }

                List<Salon> parsed = parseSalons(salons);
                if (parsed.isEmpty()) {
                    // Documents came back but none had a usable name, so the field
                    // names in Firestore do not match what we read. Showing the
                    // empty state is honest, blank cards are not.
                    Log.w(TAG, "No usable salons in Firestore - showing the empty state.");
                    showSalons(Collections.emptyList());
                    return;
                }

                showSalons(parsed);
            }

            @Override
            public void onFailure(String errorMessage) {
                if (!isAdded()) return;
                // Firestore can fail outright on a fresh install or with no
                // network. Fall back to the empty state rather than fake salons.
                Log.w(TAG, "Could not load salons: " + errorMessage + " - showing the empty state.");
                showSalons(Collections.emptyList());
            }
        });
    }

    /** Replaces the whole list, keeping the active search filter applied. */
    private void showSalons(List<Salon> salons) {
        if (!isAdded()) return;

        assignDistances(salons);

        allSalons.clear();
        allSalons.addAll(salons);
        applyFilter(currentQuery());
        loadPhotos();
    }

    /**
     * Calculates the distance from the client to each salon with Android's
     * built-in Location helper. Nothing happens when the client has no usable
     * location, so the list keeps its normal order and no labels are shown.
     */
    private void assignDistances(List<Salon> salons) {
        if (!hasClientLocation) return;

        float[] results = new float[1];
        for (Salon salon : salons) {
            Double latitude = salon.getLatitude();
            Double longitude = salon.getLongitude();
            if (!isValidCoordinatePair(latitude, longitude)) continue;

            try {
                Location.distanceBetween(
                        clientLatitude, clientLongitude, latitude, longitude, results);
                double metres = results[0];
                if (!Double.isNaN(metres) && !Double.isInfinite(metres) && metres >= 0) {
                    salon.setDistanceMetres(metres);
                }
            } catch (Exception e) {
                Log.w(TAG, "Could not calculate distance: " + e.getMessage());
            }
        }
    }

    /**
     * Photos live in their own collection so the salon list query stays light.
     * A failed read leaves every card on the placeholder icon.
     */
    private void loadPhotos() {
        SalonPhotos.fetchAll(photos -> {
            if (!isAdded() || adapter == null) return;
            adapter.setPhotos(photos);
        });
    }

    private String currentQuery() {
        return etSearch == null ? "" : etSearch.getText().toString();
    }

    /**
     * Filtering happens in memory on the cached list. Querying Firestore on every
     * keystroke would burn read quota and add latency for no benefit at this scale.
     *
     * Results are scored with {@link FuzzySearch} so typos still match. When the
     * client has a location, the final order is always nearest first; salons
     * without coordinates are kept at the bottom.
     */
    private void applyFilter(String query) {
        String trimmed = query == null ? "" : query.trim();

        visibleSalons.clear();

        if (trimmed.isEmpty()) {
            visibleSalons.addAll(allSalons);
        } else {
            List<Salon> scored = new ArrayList<>();
            for (Salon salon : allSalons) {
                if (FuzzySearch.bestScore(trimmed, salon.getName(), salon.getLocation()) > 0) {
                    scored.add(salon);
                }
            }
            // Highest score first so the best match is always on top.
            scored.sort(Comparator.comparingDouble(
                    salon -> -FuzzySearch.bestScore(trimmed, salon.getName(), salon.getLocation())));
            visibleSalons.addAll(scored);
        }

        if (hasClientLocation) {
            visibleSalons.sort(DISTANCE_ORDER);
        }

        adapter.notifyDataSetChanged();

        boolean isEmpty = visibleSalons.isEmpty();
        rvSalons.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
        tvEmptyState.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
    }

    /**
     * Converts raw Firestore maps into the Salon model. Every field is null-checked
     * because a document can legally be missing any of them.
     */
    private List<Salon> parseSalons(List<Map<String, Object>> rawSalons) {
        List<Salon> parsed = new ArrayList<>();
        Set<String> seenSalonKeys = new HashSet<>();

        for (Map<String, Object> raw : rawSalons) {
            if (raw == null) continue;

            String name = asString(raw.get("name"));
            String location = asString(raw.get("location"));
            String salonId = asString(raw.get("salonID"));

            if (name == null || name.trim().isEmpty()) {
                // Log the real field names so we can match them instead of guessing.
                Log.w(TAG, "Salon has no 'name' field. Actual fields were: " + raw.keySet());
                continue;
            }

            // The document id is unique. The fallback only exists for documents
            // that somehow arrive without one, so no salon can be listed twice.
            String salonKey = (salonId != null && !salonId.trim().isEmpty())
                    ? salonId
                    : name + "|" + (location == null ? "" : location);
            if (!seenSalonKeys.add(salonKey)) continue;

            double avgRating = 0.0;
            Object ratingValue = raw.get("avgRating");
            if (ratingValue instanceof Number) {
                avgRating = ((Number) ratingValue).doubleValue();
            }

            List<String> services = asStringList(raw.get("services"));

            Double latitude = asCoordinate(raw.get("latitude"), -90d, 90d);
            Double longitude = asCoordinate(raw.get("longitude"), -180d, 180d);
            if (!isValidCoordinatePair(latitude, longitude)) {
                latitude = null;
                longitude = null;
            }

            // imageRes stays 0 - Firestore holds no images, so the adapter falls
            // back to the placeholder icon.
            Salon salon = new Salon(salonId, name, location, avgRating, services);
            if (latitude != null && longitude != null) {
                salon.setCoordinates(latitude, longitude);
            }
            parsed.add(salon);
        }

        return parsed;
    }

    private String asString(Object value) {
        return value == null ? null : value.toString();
    }

    /**
     * Firestore may return a whole number as Long, so always read through
     * Number.doubleValue(). Anything outside the given range is treated as
     * missing rather than passed to Location.
     */
    private Double asCoordinate(Object value, double minimum, double maximum) {
        if (!(value instanceof Number)) return null;
        double parsed = ((Number) value).doubleValue();
        if (Double.isNaN(parsed) || Double.isInfinite(parsed)) return null;
        if (parsed < minimum || parsed > maximum) return null;
        return parsed;
    }

    /**
     * A pair is usable only when both values are present, inside their valid
     * ranges, and not the placeholder 0,0 that a blank picker can leave behind.
     */
    private boolean isValidCoordinatePair(Double latitude, Double longitude) {
        if (latitude == null || longitude == null) return false;
        if (Double.isNaN(latitude) || Double.isInfinite(latitude)) return false;
        if (Double.isNaN(longitude) || Double.isInfinite(longitude)) return false;
        if (latitude < -90d || latitude > 90d) return false;
        if (longitude < -180d || longitude > 180d) return false;
        return !(latitude == 0d && longitude == 0d);
    }

    private static int compareSalonKeys(Salon first, Salon second) {
        String firstKey = first.getSalonId() != null ? first.getSalonId() : first.getName();
        String secondKey = second.getSalonId() != null ? second.getSalonId() : second.getName();
        if (firstKey == null && secondKey == null) return 0;
        if (firstKey == null) return 1;
        if (secondKey == null) return -1;
        return firstKey.compareTo(secondKey);
    }

    private List<String> asStringList(Object value) {
        List<String> result = new ArrayList<>();
        if (value instanceof List) {
            for (Object item : (List<?>) value) {
                if (item != null) result.add(item.toString());
            }
        }
        return result;
    }
}
