package com.hairgo.app.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseUser;
import com.hairgo.app.R;
import com.hairgo.app.activities.BookingDetailsActivity;
import com.hairgo.app.adapters.BookingSectionAdapter;
import com.hairgo.app.firebase.AuthManager;
import com.hairgo.app.firebase.BookingManager;
import com.hairgo.app.models.Booking;
import com.hairgo.app.utils.BookingSections;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;

public class BookingsFragment extends Fragment {

    private static final String TAG = "BookingsFragment";

    private RecyclerView rvBookings;
    private TextView tvEmptyState;
    private AuthManager authManager;
    private BookingManager bookingManager;

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_bookings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        rvBookings = view.findViewById(R.id.rvBookings);
        tvEmptyState = view.findViewById(R.id.tvEmptyState);
        rvBookings.setLayoutManager(new LinearLayoutManager(getContext()));

        authManager = new AuthManager();
        bookingManager = new BookingManager();
    }

    /**
     * Covers two cases: the first time the tab is opened, and coming back from
     * Booking Details after a booking was cancelled.
     */
    @Override
    public void onResume() {
        super.onResume();
        if (bookingManager != null) {
            loadBookings();
        }
    }

    /**
     * The dashboard keeps this fragment alive and only hides it, so switching
     * back to the Bookings tab has to re-read the data too.
     */
    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (!hidden && bookingManager != null) {
            loadBookings();
        }
    }

    private void loadBookings() {
        FirebaseUser currentUser = authManager.getCurrentUser();
        if (currentUser == null) {
            showBookings(Collections.emptyList());
            return;
        }

        bookingManager.getBookingsForClient(currentUser.getUid(), new BookingManager.BookingListCallback() {
            @Override
            public void onSuccess(List<Map<String, Object>> rawBookings) {
                if (rawBookings == null || rawBookings.isEmpty()) {
                    // Nothing booked yet, so the empty state is the honest thing to show.
                    showBookings(Collections.emptyList());
                    return;
                }
                showBookings(parseBookings(rawBookings));
            }

            @Override
            public void onFailure(String errorMessage) {
                // Firestore can fail on a first run or with no network. Show the
                // empty state rather than inventing bookings that do not exist.
                Log.w(TAG, "Could not load bookings: " + errorMessage);
                showBookings(Collections.emptyList());
            }
        });
    }

    /**
     * Converts raw Firestore maps into the Booking model. Fields are null-checked
     * because a booking document is allowed to be missing any of them.
     */
    private List<Booking> parseBookings(List<Map<String, Object>> rawBookings) {
        List<Booking> parsed = new ArrayList<>();

        for (Map<String, Object> raw : rawBookings) {
            if (raw == null) continue;

            String bookingId = asString(raw.get("bookingID"));
            if (bookingId == null || bookingId.isEmpty()) {
                Log.w(TAG, "Booking has no 'bookingID' field. Actual fields were: " + raw.keySet());
                continue;
            }

            String salonId = asString(raw.get("salonID"));
            String salonName = asString(raw.get("salonName"));
            String serviceName = asString(raw.get("serviceName"));
            String status = asString(raw.get("status"));
            Date dateTime = asDate(raw.get("dateTime"));

            if (salonName == null || salonName.isEmpty()) {
                Log.w(TAG, "Booking has no 'salonName' field. Actual fields were: " + raw.keySet());
                continue;
            }

            parsed.add(new Booking(bookingId, salonId, salonName,
                    serviceName == null ? "" : serviceName,
                    dateTime, status));
        }

        return parsed;
    }

    private void showBookings(List<Booking> bookings) {
        if (!isAdded()) return;

        List<BookingSections.BookingSection> sections = BookingSections.partition(bookings);

        if (BookingSections.isEmpty(sections)) {
            rvBookings.setVisibility(View.GONE);
            tvEmptyState.setVisibility(View.VISIBLE);
            return;
        }

        rvBookings.setVisibility(View.VISIBLE);
        tvEmptyState.setVisibility(View.GONE);
        rvBookings.setAdapter(new BookingSectionAdapter(sections, booking -> {
            Intent intent = new Intent(getContext(), BookingDetailsActivity.class);
            intent.putExtra("bookingId", booking.getBookingId());
            startActivity(intent);
        }));
    }

    private String asString(Object value) {
        return value == null ? null : value.toString();
    }

    private Date asDate(Object value) {
        if (value instanceof Timestamp) {
            return ((Timestamp) value).toDate();
        }
        return null;
    }
}
