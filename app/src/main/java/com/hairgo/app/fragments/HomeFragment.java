package com.hairgo.app.fragments;

import android.content.Intent;
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

import com.hairgo.app.R;
import com.hairgo.app.activities.SalonProfileActivity;
import com.hairgo.app.adapters.SalonAdapter;
import com.hairgo.app.firebase.SalonManager;
import com.hairgo.app.models.Salon;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class HomeFragment extends Fragment {

    private static final String TAG = "HomeFragment";

    private RecyclerView rvSalons;
    private TextView tvEmptyState;
    private EditText etSearch;
    private SalonAdapter adapter;

    // Full list from Firestore, kept so filtering does not refetch every keystroke.
    private final List<Salon> allSalons = new ArrayList<>();
    // What the adapter is currently showing.
    private final List<Salon> visibleSalons = new ArrayList<>();

    private SalonManager salonManager;

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

        rvSalons.setLayoutManager(new LinearLayoutManager(getContext()));

        adapter = new SalonAdapter(visibleSalons, salon -> {
            Intent intent = new Intent(getContext(), SalonProfileActivity.class);
            intent.putExtra("salonId", salon.getSalonId());
            startActivity(intent);
        });
        rvSalons.setAdapter(adapter);

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
     * never show up.
     */
    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (!hidden && salonManager != null) {
            loadSalons();
        }
    }

    private void loadSalons() {
        salonManager.getAllSalons(new SalonManager.SalonListCallback() {
            @Override
            public void onSuccess(List<Map<String, Object>> salons) {
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

        allSalons.clear();
        allSalons.addAll(salons);
        applyFilter(currentQuery());
    }

    private String currentQuery() {
        return etSearch == null ? "" : etSearch.getText().toString();
    }

    /**
     * Filtering happens in memory on the cached list. Querying Firestore on every
     * keystroke would burn read quota and add latency for no benefit at this scale.
     */
    private void applyFilter(String query) {
        String trimmed = query == null ? "" : query.trim().toLowerCase(Locale.getDefault());

        visibleSalons.clear();
        for (Salon salon : allSalons) {
            if (trimmed.isEmpty() || matches(salon, trimmed)) {
                visibleSalons.add(salon);
            }
        }

        adapter.notifyDataSetChanged();

        boolean isEmpty = visibleSalons.isEmpty();
        rvSalons.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
        tvEmptyState.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
    }

    private boolean matches(Salon salon, String lowerCaseQuery) {
        String name = salon.getName() == null ? "" : salon.getName().toLowerCase(Locale.getDefault());
        String location = salon.getLocation() == null ? "" : salon.getLocation().toLowerCase(Locale.getDefault());
        return name.contains(lowerCaseQuery) || location.contains(lowerCaseQuery);
    }

    /**
     * Converts raw Firestore maps into the Salon model. Every field is null-checked
     * because a document can legally be missing any of them.
     */
    private List<Salon> parseSalons(List<Map<String, Object>> rawSalons) {
        List<Salon> parsed = new ArrayList<>();

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

            double avgRating = 0.0;
            Object ratingValue = raw.get("avgRating");
            if (ratingValue instanceof Number) {
                avgRating = ((Number) ratingValue).doubleValue();
            }

            List<String> services = asStringList(raw.get("services"));

            // imageRes stays 0 - Firestore holds no images, so the adapter falls
            // back to the placeholder icon.
            parsed.add(new Salon(salonId, name, location, avgRating, services));
        }

        return parsed;
    }

    private String asString(Object value) {
        return value == null ? null : value.toString();
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