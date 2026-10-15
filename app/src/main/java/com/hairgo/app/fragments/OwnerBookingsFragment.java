package com.hairgo.app.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.firebase.firestore.ListenerRegistration;
import com.hairgo.app.R;
import com.hairgo.app.adapters.OwnerBookingAdapter;
import com.hairgo.app.firebase.BookingManager;
import com.hairgo.app.models.OwnerBooking;
import com.hairgo.app.utils.OwnerSession;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;

public class OwnerBookingsFragment extends Fragment {

    private final List<OwnerBooking> bookings = new ArrayList<>();
    private OwnerBookingAdapter adapter;
    private TextView tvEmpty;
    private SwipeRefreshLayout swipeRefresh;
    private BookingManager bookingManager;
    private ListenerRegistration bookingsListener;
    private boolean receivedData;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_owner_bookings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        bookingManager = new BookingManager();

        RecyclerView rv = view.findViewById(R.id.rvOwnerBookings);
        tvEmpty = view.findViewById(R.id.tvBookingsEmpty);
        swipeRefresh = view.findViewById(R.id.swipeRefreshBookings);

        adapter = new OwnerBookingAdapter(bookings, new OwnerBookingAdapter.OnBookingActionListener() {
            @Override
            public void onConfirm(OwnerBooking booking) {
                updateStatus(booking, "confirmed");
            }

            @Override
            public void onDecline(OwnerBooking booking) {
                confirmDecline(booking);
            }

            @Override
            public void onComplete(OwnerBooking booking) {
                updateStatus(booking, "completed");
            }
        });

        rv.setLayoutManager(new LinearLayoutManager(getContext()));
        rv.setAdapter(adapter);
        swipeRefresh.setOnRefreshListener(this::refresh);
    }

    /**
     * Listens while the fragment is on screen so the salon's bookings stay
     * current in real time (a new client booking, or a status change made
     * elsewhere, shows up without a manual refresh). The listener is removed in
     * onStop so it does not keep reading in the background.
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
        String salonId = OwnerSession.getSelectedSalonId();
        if (salonId == null) {
            bookings.clear();
            if (adapter != null) adapter.notifyDataSetChanged();
            swipeRefresh.setRefreshing(false);
            showEmpty("Add a salon to start receiving bookings");
            return;
        }

        swipeRefresh.setRefreshing(true);
        bookingsListener = bookingManager.listenToBookingsForSalon(salonId, new BookingManager.BookingListCallback() {
            @Override
            public void onSuccess(List<Map<String, Object>> raw) {
                if (!isAdded()) return;

                receivedData = true;
                bookings.clear();
                for (Map<String, Object> map : raw) {
                    OwnerBooking booking = OwnerBooking.fromMap(map);
                    if (booking != null) bookings.add(booking);
                }
                sortBookings();
                adapter.notifyDataSetChanged();
                swipeRefresh.setRefreshing(false);
                showEmpty("No bookings yet");
            }

            @Override
            public void onFailure(String errorMessage) {
                if (!isAdded()) return;
                swipeRefresh.setRefreshing(false);
                // A transient error after we already have data should not wipe
                // the list; only fall back to the empty state on the first load.
                if (!receivedData) {
                    bookings.clear();
                    adapter.notifyDataSetChanged();
                    showEmpty("Could not load bookings");
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

    /** Pull-to-refresh re-attaches the listener so a fresh snapshot is fetched. */
    private void refresh() {
        stopListening();
        startListening();
    }

    /** Upcoming bookings first (soonest at the top), then past ones (most recent first). */
    private void sortBookings() {
        final Date now = new Date();
        Collections.sort(bookings, (a, b) -> {
            Date ta = a.getDateTime();
            Date tb = b.getDateTime();
            if (ta == null && tb == null) return 0;
            if (ta == null) return 1;
            if (tb == null) return -1;

            boolean aPast = ta.before(now);
            boolean bPast = tb.before(now);
            if (aPast != bPast) return aPast ? 1 : -1;
            return aPast ? tb.compareTo(ta) : ta.compareTo(tb);
        });
    }

    private void updateStatus(OwnerBooking booking, String newStatus) {
        bookingManager.updateBookingStatus(booking.getBookingId(), newStatus,
                new BookingManager.BookingCallback() {
                    @Override
                    public void onSuccess() {
                        if (!isAdded()) return;
                        booking.setStatus(newStatus);
                        int index = bookings.indexOf(booking);
                        if (index >= 0) adapter.notifyItemChanged(index);
                    }

                    @Override
                    public void onFailure(String errorMessage) {
                        if (!isAdded()) return;
                        Toast.makeText(getContext(), "Could not update booking", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void confirmDecline(OwnerBooking booking) {
        new AlertDialog.Builder(requireContext())
                .setTitle("Decline this booking?")
                .setMessage("The time slot will become available again.")
                .setPositiveButton("Decline", (dialog, which) -> declineBooking(booking))
                .setNegativeButton("Keep", null)
                .show();
    }

    private void declineBooking(OwnerBooking booking) {
        // cancelBooking also soft-deletes the booking, which is what frees the time slot.
        bookingManager.cancelBooking(booking.getBookingId(), new BookingManager.BookingCallback() {
            @Override
            public void onSuccess() {
                if (!isAdded()) return;
                int index = bookings.indexOf(booking);
                if (index >= 0) {
                    bookings.remove(index);
                    adapter.notifyItemRemoved(index);
                }
                showEmpty("No bookings yet");
            }

            @Override
            public void onFailure(String errorMessage) {
                if (!isAdded()) return;
                Toast.makeText(getContext(), "Could not decline booking", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showEmpty(String message) {
        tvEmpty.setText(message);
        tvEmpty.setVisibility(bookings.isEmpty() ? View.VISIBLE : View.GONE);
    }
}