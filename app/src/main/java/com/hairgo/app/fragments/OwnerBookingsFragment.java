package com.hairgo.app.fragments;

import android.content.Intent;
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

import com.google.android.material.chip.ChipGroup;
import com.google.firebase.firestore.ListenerRegistration;
import com.hairgo.app.R;
import com.hairgo.app.activities.ChatActivity;
import com.hairgo.app.adapters.OwnerBookingAdapter;
import com.hairgo.app.firebase.BookingManager;
import com.hairgo.app.firebase.OwnerManager;
import com.hairgo.app.models.OwnerBooking;
import com.hairgo.app.utils.OwnerSession;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class OwnerBookingsFragment extends Fragment {

    private static final int FILTER_ALL = 0;
    private static final int FILTER_PENDING = 1;
    private static final int FILTER_UPCOMING = 2;
    private static final int FILTER_PAST = 3;

    /** Everything loaded for the selected salon. */
    private final List<OwnerBooking> allBookings = new ArrayList<>();
    /** What the list currently shows after the filter chips are applied. */
    private final List<OwnerBooking> shownBookings = new ArrayList<>();
    /** Clients whose name is currently being looked up, so each is fetched only once. */
    private final Set<String> pendingLookups = new HashSet<>();

    private int currentFilter = FILTER_ALL;
    private OwnerBookingAdapter adapter;
    private TextView tvEmpty;
    private SwipeRefreshLayout swipeRefresh;
    private BookingManager bookingManager;
    private OwnerManager ownerManager;
    private ListenerRegistration bookingsListener;

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
        ownerManager = new OwnerManager();

        RecyclerView rv = view.findViewById(R.id.rvOwnerBookings);
        tvEmpty = view.findViewById(R.id.tvBookingsEmpty);
        swipeRefresh = view.findViewById(R.id.swipeRefreshBookings);
        ChipGroup chipGroup = view.findViewById(R.id.chipGroupFilter);

        adapter = new OwnerBookingAdapter(shownBookings, new OwnerBookingAdapter.OnBookingActionListener() {
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

            @Override
            public void onMessage(OwnerBooking booking) {
                openChat(booking.getBookingId());
            }
        });

        rv.setLayoutManager(new LinearLayoutManager(getContext()));
        rv.setAdapter(adapter);
        swipeRefresh.setOnRefreshListener(this::startListening);

        chipGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) return;
            int id = checkedIds.get(0);
            if (id == R.id.chipPending) currentFilter = FILTER_PENDING;
            else if (id == R.id.chipUpcoming) currentFilter = FILTER_UPCOMING;
            else if (id == R.id.chipPast) currentFilter = FILTER_PAST;
            else currentFilter = FILTER_ALL;
            applyFilter();
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        startListening();
    }

    @Override
    public void onPause() {
        super.onPause();
        stopListening();
    }

    /**
     * Keeps the list live while this tab is on screen: new requests and client cancellations
     * appear on their own. Pull-to-refresh simply restarts the listener.
     */
    private void startListening() {
        stopListening();

        String salonId = OwnerSession.getSelectedSalonId();
        if (salonId == null) {
            allBookings.clear();
            swipeRefresh.setRefreshing(false);
            applyFilter();
            return;
        }

        swipeRefresh.setRefreshing(true);
        bookingsListener = ownerManager.listenToSalonBookings(salonId,
                new BookingManager.BookingListCallback() {
                    @Override
                    public void onSuccess(List<Map<String, Object>> raw) {
                        if (!isAdded()) return;

                        allBookings.clear();
                        for (Map<String, Object> map : raw) {
                            OwnerBooking booking = OwnerBooking.fromMap(map);
                            if (booking != null) allBookings.add(booking);
                        }
                        swipeRefresh.setRefreshing(false);
                        resolveMissingClientNames();
                        refreshList();
                    }

                    @Override
                    public void onFailure(String errorMessage) {
                        if (!isAdded()) return;
                        swipeRefresh.setRefreshing(false);
                        Toast.makeText(getContext(), "Could not load bookings", Toast.LENGTH_SHORT).show();
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
     * Bookings made before clientName was stored have no name on them. Fill those in from the
     * client's profile, once per client. If a profile cannot be read or has no name, the card
     * shows "Unknown client".
     */
    private void resolveMissingClientNames() {
        for (OwnerBooking booking : allBookings) {
            if (!booking.getClientName().isEmpty()) continue;

            String clientId = booking.getClientId();
            if (clientId == null || clientId.isEmpty()) continue;

            String cached = OwnerSession.getCachedClientName(clientId);
            if (cached != null) {
                booking.setClientName(cached);
                continue;
            }

            if (!pendingLookups.add(clientId)) continue; // already being fetched

            ownerManager.getClientName(clientId, new OwnerManager.OwnerCallback<String>() {
                @Override
                public void onSuccess(String name) {
                    OwnerSession.cacheClientName(clientId, name);
                    pendingLookups.remove(clientId);
                    if (!isAdded()) return;
                    for (OwnerBooking b : allBookings) {
                        if (clientId.equals(b.getClientId()) && b.getClientName().isEmpty()) {
                            b.setClientName(name);
                        }
                    }
                    adapter.notifyDataSetChanged();
                }

                @Override
                public void onFailure(String errorMessage) {
                    // Not cached, so a later refresh can try again. The card shows "Unknown client".
                    pendingLookups.remove(clientId);
                }
            });
        }
    }

    /** Re-sorts everything and re-applies the current filter. */
    private void refreshList() {
        sortBookings();
        applyFilter();
    }

    /**
     * Bookings that still need attention come first (soonest at the top). Everything else
     * (completed, cancelled, expired, or already past) follows, most recent first.
     */
    private void sortBookings() {
        Collections.sort(allBookings, (a, b) -> {
            boolean aActive = a.isActiveUpcoming();
            boolean bActive = b.isActiveUpcoming();
            if (aActive != bActive) return aActive ? -1 : 1;

            Date ta = a.getDateTime();
            Date tb = b.getDateTime();
            if (ta == null && tb == null) return 0;
            if (ta == null) return 1;
            if (tb == null) return -1;
            return aActive ? ta.compareTo(tb) : tb.compareTo(ta);
        });
    }

    private void applyFilter() {
        shownBookings.clear();
        for (OwnerBooking booking : allBookings) {
            if (matchesFilter(booking)) shownBookings.add(booking);
        }
        adapter.notifyDataSetChanged();

        tvEmpty.setText(emptyMessage());
        tvEmpty.setVisibility(shownBookings.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private boolean matchesFilter(OwnerBooking booking) {
        switch (currentFilter) {
            case FILTER_PENDING:
                return "pending".equals(booking.getStatus());
            case FILTER_UPCOMING:
                return booking.isActiveUpcoming();
            case FILTER_PAST:
                return !booking.isActiveUpcoming();
            case FILTER_ALL:
            default:
                return true;
        }
    }

    private String emptyMessage() {
        if (OwnerSession.getSelectedSalonId() == null) {
            return "Add a salon to start receiving bookings";
        }
        switch (currentFilter) {
            case FILTER_PENDING:
                return "No pending requests";
            case FILTER_UPCOMING:
                return "No upcoming bookings";
            case FILTER_PAST:
                return "No past bookings";
            case FILTER_ALL:
            default:
                return "No bookings yet";
        }
    }

    private void updateStatus(OwnerBooking booking, String newStatus) {
        bookingManager.updateBookingStatus(booking.getBookingId(), newStatus,
                new BookingManager.BookingCallback() {
                    @Override
                    public void onSuccess() {
                        if (!isAdded()) return;
                        booking.setStatus(newStatus);
                        refreshList();
                    }

                    @Override
                    public void onFailure(String errorMessage) {
                        if (!isAdded()) return;
                        Toast.makeText(getContext(), "Could not update booking", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    /** Opens the chat with this booking's client. */
    private void openChat(String bookingId) {
        Intent intent = new Intent(getContext(), ChatActivity.class);
        intent.putExtra("bookingId", bookingId);
        startActivity(intent);
    }

    private void confirmDecline(OwnerBooking booking) {
        new AlertDialog.Builder(requireContext())
                .setTitle("Decline this booking?")
                .setMessage("The client will see it as cancelled, and the time slot becomes available again.")
                .setPositiveButton("Decline", (dialog, which) -> updateStatus(booking, "cancelled"))
                .setNegativeButton("Keep", null)
                .show();
    }
}