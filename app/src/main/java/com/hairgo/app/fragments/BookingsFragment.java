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
import com.google.firebase.firestore.ListenerRegistration;
import com.hairgo.app.R;
import com.hairgo.app.activities.BookingDetailsActivity;
import com.hairgo.app.adapters.BookingSectionAdapter;
import com.hairgo.app.firebase.AuthManager;
import com.hairgo.app.firebase.BookingManager;
import com.hairgo.app.models.Booking;
import com.hairgo.app.utils.BookingSections;
import com.hairgo.app.utils.BookingStatusNotifier;
import com.hairgo.app.utils.SalonPhotos;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class BookingsFragment extends Fragment {

    private static final String TAG = "BookingsFragment";

    private RecyclerView rvBookings;
    private TextView tvEmptyState;
    private BookingSectionAdapter adapter;
    private AuthManager authManager;
    private BookingManager bookingManager;
    private ListenerRegistration bookingsListener;
    private boolean receivedData;

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

        // One adapter for the fragment's lifetime. Reloading updates its data
        // instead of swapping in a new adapter, which would reset the scroll.
        adapter = new BookingSectionAdapter(new ArrayList<>(), booking -> {
            Intent intent = new Intent(getContext(), BookingDetailsActivity.class);
            intent.putExtra("bookingId", booking.getBookingId());
            startActivity(intent);
        });
        rvBookings.setAdapter(adapter);

        authManager = new AuthManager();
        bookingManager = new BookingManager();
    }

    /**
     * Listens while the fragment is on screen so bookings stay current in real
     * time (for example when a booking is confirmed elsewhere). The listener is
     * removed in onStop so it does not keep reading in the background.
     */
    @Override
    public void onStart() {
        super.onStart();
        startListening();
    }

    @Override
    public void onStop() {
        super.onStop();
        stopListening();
    }

    private void startListening() {
        FirebaseUser currentUser = authManager.getCurrentUser();
        if (currentUser == null) {
            showBookings(Collections.emptyList());
            return;
        }

        bookingsListener = bookingManager.listenToBookingsForClient(
                currentUser.getUid(), new BookingManager.BookingListCallback() {
                    @Override
                    public void onSuccess(List<Map<String, Object>> rawBookings) {
                        receivedData = true;
                        if (rawBookings == null || rawBookings.isEmpty()) {
                            // Nothing booked yet, so the empty state is the honest thing to show.
                            showBookings(Collections.emptyList());
                            return;
                        }
                        List<Booking> bookings = parseBookings(rawBookings);
                        expireStaleBookings(bookings);
                        showBookings(bookings);
                    }

                    @Override
                    public void onFailure(String errorMessage) {
                        // A transient error after we already have data should not
                        // wipe the list; only fall back for the very first load.
                        Log.w(TAG, "Could not load bookings: " + errorMessage);
                        if (!receivedData) {
                            showBookings(Collections.emptyList());
                        }
                    }
                });
    }

    private void stopListening() {
        if (bookingsListener != null) {
            bookingsListener.remove();
            bookingsListener = null;
        }
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
            List<String> serviceNames = asStringList(raw.get("serviceNames"));
            if (serviceNames.isEmpty()) {
                // Legacy bookings stored a single serviceName string.
                String singleService = asString(raw.get("serviceName"));
                if (singleService != null && !singleService.isEmpty()) {
                    serviceNames.add(singleService);
                }
            }
            String status = asString(raw.get("status"));
            Date dateTime = asDate(raw.get("dateTime"));

            if (salonName == null || salonName.isEmpty()) {
                Log.w(TAG, "Booking has no 'salonName' field. Actual fields were: " + raw.keySet());
                continue;
            }

            Booking booking = new Booking(bookingId, salonId, salonName,
                    serviceNames, dateTime, status, 0);

            // New bookings keep a copy of the chosen services. Old bookings
            // have none, so their card looks exactly as it did before.
            booking.setServiceDetails(BookingManager.getServiceDetails(raw));
            Double savedPrice = BookingManager.getTotalPrice(raw);
            if (savedPrice != null) {
                booking.setTotalPrice(savedPrice);
            }
            int savedDuration = BookingManager.getTotalDuration(raw);
            if (savedDuration > 0) {
                booking.setTotalDurationMinutes(savedDuration);
            }

            parsed.add(booking);
        }

        return parsed;
    }

    /**
     * Persists expiry for stale pending bookings. The list is bound straight
     * after this call and derives "expired" from the date itself, so the write
     * runs in the background and a failure only means Firestore still reads
     * pending until the next load tries again.
     */
    private void expireStaleBookings(List<Booking> bookings) {
        List<String> staleIds = new ArrayList<>();
        for (Booking booking : bookings) {
            if (BookingSections.isExpired(booking)) {
                staleIds.add(booking.getBookingId());
            }
        }
        if (staleIds.isEmpty()) return;

        bookingManager.expireBookings(staleIds, new BookingManager.BookingCallback() {
            @Override
            public void onSuccess() {
            }

            @Override
            public void onFailure(String errorMessage) {
                Log.w(TAG, "Could not expire stale bookings: " + errorMessage);
            }
        });
    }

    private void showBookings(List<Booking> bookings) {
        if (!isAdded()) return;

        // Announce any status change since the list was last seen here.
        BookingStatusNotifier.notifyChanges(getContext(), bookings);

        // Let the dashboard light its bell dot straight away, without waiting
        // for the user to leave and come back.
        if (getActivity() instanceof NotificationDotHost) {
            ((NotificationDotHost) getActivity()).refreshNotificationDot();
        }

        List<BookingSections.BookingSection> sections = BookingSections.partition(bookings);

        if (BookingSections.isEmpty(sections)) {
            rvBookings.setVisibility(View.GONE);
            tvEmptyState.setVisibility(View.VISIBLE);
            return;
        }

        rvBookings.setVisibility(View.VISIBLE);
        tvEmptyState.setVisibility(View.GONE);

        // Update in place so the RecyclerView keeps its scroll position.
        adapter.updateSections(sections);

        // Fetch salon photos for the bookings
        fetchSalonPhotos(sections);
    }

    private void fetchSalonPhotos(List<BookingSections.BookingSection> sections) {
        if (!isAdded()) return;

        // Collect unique salon IDs from all bookings
        Set<String> salonIds = new HashSet<>();
        for (BookingSections.BookingSection section : sections) {
            if (section == null) continue;
            for (Booking booking : section.getBookings()) {
                if (booking != null && booking.getSalonId() != null && !booking.getSalonId().isEmpty()) {
                    salonIds.add(booking.getSalonId());
                }
            }
        }

        if (salonIds.isEmpty()) return;

        SalonPhotos.fetchPhotos(new ArrayList<>(salonIds), photos -> {
            if (!isAdded()) return;
            adapter.setPhotos(photos);
        });
    }

    private String asString(Object value) {
        return value == null ? null : value.toString();
    }

    /** Reads a serviceNames array; returns an empty list when absent or malformed. */
    private List<String> asStringList(Object value) {
        List<String> result = new ArrayList<>();
        if (value instanceof List) {
            for (Object item : (List<?>) value) {
                if (item != null && !item.toString().isEmpty()) result.add(item.toString());
            }
        }
        return result;
    }

    private Date asDate(Object value) {
        if (value instanceof Timestamp) {
            return ((Timestamp) value).toDate();
        }
        return null;
    }
}
