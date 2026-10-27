package com.hairgo.app.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.firestore.ListenerRegistration;
import com.hairgo.app.R;
import com.hairgo.app.activities.ChatActivity;
import com.hairgo.app.activities.OwnerBookingDetailsActivity;
import com.hairgo.app.adapters.OwnerBookingAdapter;
import com.hairgo.app.firebase.BookingManager;
import com.hairgo.app.firebase.OwnerManager;
import com.hairgo.app.models.OwnerBooking;
import com.hairgo.app.utils.CompletionReminderManager;
import com.hairgo.app.utils.OwnerSession;

import java.text.SimpleDateFormat;
import java.util.Locale;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class OwnerBookingsFragment extends Fragment {

    /** Lets callers (the home stat cards) pre-select a filter when opening this tab. */
    public static final String ARG_FILTER = "initialFilter";

    public static final int FILTER_ALL = 0;
    public static final int FILTER_PENDING = 1;
    public static final int FILTER_UPCOMING = 2;
    public static final int FILTER_PAST = 3;
    public static final int FILTER_TODAY = 4;
    public static final int FILTER_COMPLETED = 5;

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
    private final Handler reminderHandler = new Handler(Looper.getMainLooper());
    private final Runnable reminderCheckRunnable = new Runnable() {
        @Override
        public void run() {
            checkForCompletionReminders();
            reminderHandler.postDelayed(this, 60000);
        }
    };

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

            @Override
            public void onOpen(OwnerBooking booking) {
                openBookingDetails(booking.getBookingId());
            }
        });

        rv.setLayoutManager(new LinearLayoutManager(getContext()));
        rv.setAdapter(adapter);
        swipeRefresh.setOnRefreshListener(this::startListening);

        chipGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) return;
            currentFilter = filterForChip(checkedIds.get(0));
            applyFilter();
        });

        // A filter passed in by the home stat cards pre-selects its chip.
        Bundle args = getArguments();
        if (args != null) {
            currentFilter = args.getInt(ARG_FILTER, FILTER_ALL);
            checkFilterChip(chipGroup);
        }
    }

    private int filterForChip(int chipId) {
        if (chipId == R.id.chipPending) return FILTER_PENDING;
        if (chipId == R.id.chipUpcoming) return FILTER_UPCOMING;
        if (chipId == R.id.chipPast) return FILTER_PAST;
        if (chipId == R.id.chipToday) return FILTER_TODAY;
        if (chipId == R.id.chipCompleted) return FILTER_COMPLETED;
        return FILTER_ALL;
    }

    private void checkFilterChip(ChipGroup group) {
        switch (currentFilter) {
            case FILTER_TODAY:
                group.check(R.id.chipToday);
                break;
            case FILTER_PENDING:
                group.check(R.id.chipPending);
                break;
            case FILTER_UPCOMING:
                group.check(R.id.chipUpcoming);
                break;
            case FILTER_COMPLETED:
                group.check(R.id.chipCompleted);
                break;
            case FILTER_PAST:
                group.check(R.id.chipPast);
                break;
            case FILTER_ALL:
            default:
                group.check(R.id.chipAll);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        startListening();
        reminderHandler.removeCallbacks(reminderCheckRunnable);
        reminderHandler.postDelayed(reminderCheckRunnable, 1000);
    }

    @Override
    public void onPause() {
        super.onPause();
        stopListening();
        reminderHandler.removeCallbacks(reminderCheckRunnable);
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
            checkForCompletionReminders();
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
                        checkForCompletionReminders();
                    }

                    @Override
                    public void onFailure(String errorMessage) {
                        if (!isAdded()) return;
                        swipeRefresh.setRefreshing(false);
                        Toast.makeText(getContext(), "Could not load bookings", Toast.LENGTH_SHORT).show();
                        checkForCompletionReminders();
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
        checkForCompletionReminders();
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
            case FILTER_TODAY:
                // Same "live" rule the home stats use: cancelled and expired never count.
                return booking.isToday()
                        && !"cancelled".equals(booking.getStatus())
                        && !"expired".equals(booking.getStatus());
            case FILTER_COMPLETED:
                return "completed".equals(booking.getStatus());
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
            case FILTER_TODAY:
                return "No bookings today";
            case FILTER_COMPLETED:
                return "No completed bookings";
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
                        CompletionReminderManager.getInstance().remove(booking.getBookingId());
                        CompletionReminderManager.getInstance().clearCurrentlyShowing(booking.getBookingId());
                        refreshList();
                        checkForCompletionReminders();
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

    /** Opens the full detail view for one booking. The list is live, so any
     *  status change made there shows up here without a manual refresh. */
    private void openBookingDetails(String bookingId) {
        Intent intent = new Intent(getContext(), OwnerBookingDetailsActivity.class);
        intent.putExtra("bookingId", bookingId);
        startActivity(intent);
    }

    private void confirmDecline(OwnerBooking booking) {
        new AlertDialog.Builder(requireContext())
                .setTitle("Decline this booking?")
                .setMessage("The client will see it as cancelled, and the time slot becomes available again.")
                .setPositiveButton("Decline", (dialog, which) -> {
                    CompletionReminderManager.getInstance().remove(booking.getBookingId());
                    CompletionReminderManager.getInstance().clearCurrentlyShowing(booking.getBookingId());
                    updateStatus(booking, "cancelled");
                })
                .setNegativeButton("Keep", null)
                .show();
    }

    private void checkForCompletionReminders() {
        if (!isAdded()) {
            return;
        }
        CompletionReminderManager manager = CompletionReminderManager.getInstance();
        if (manager.isCurrentlyShowing(null) || (manager.peekNext() != null && findCurrentShowing() != null)) {
            // avoid stacking
        }
        // Clean up removed/completed/cancelled
        for (OwnerBooking booking : new ArrayList<OwnerBooking>(allBookings)) {
            if (booking == null) {
                continue;
            }
            if ("completed".equalsIgnoreCase(booking.getStatus())
                    || "cancelled".equalsIgnoreCase(booking.getStatus())
                    || booking.isCancelledByClient()) {
                manager.remove(booking.getBookingId());
                manager.clearCurrentlyShowing(booking.getBookingId());
            }
        }
        manager.enqueueBookings(requireContext(), allBookings);
        if (manager.isCurrentlyShowing(null)) {
            // check if already showing
        }
        String nextId = manager.peekNext();
        if (nextId == null) {
            return;
        }
        if (manager.isCurrentlyShowing(nextId)) {
            return;
        }
        OwnerBooking toShow = findBookingById(nextId);
        if (toShow == null) {
            manager.remove(nextId);
            return;
        }
        if (!"confirmed".equalsIgnoreCase(toShow.getStatus())) {
            manager.remove(nextId);
            return;
        }
        long now = System.currentTimeMillis();
        if (!toShow.isOverdueForCompletion(now)) {
            manager.remove(nextId);
            return;
        }
        if (manager.isDeferred(requireContext(), nextId, now)) {
            manager.remove(nextId);
            return;
        }
        showCompletionReminderDialog(toShow);
    }

    private OwnerBooking findBookingById(String bookingId) {
        if (bookingId == null) {
            return null;
        }
        for (OwnerBooking booking : allBookings) {
            if (booking != null && bookingId.equals(booking.getBookingId())) {
                return booking;
            }
        }
        return null;
    }

    private AlertDialog findCurrentShowing() {
        return null;
    }

    private void showCompletionReminderDialog(OwnerBooking booking) {
        if (!isAdded() || getContext() == null) {
            return;
        }
        CompletionReminderManager manager = CompletionReminderManager.getInstance();
        if (manager.isCurrentlyShowing(booking.getBookingId())) {
            return;
        }
        String nextId = manager.peekNext();
        if (nextId == null || !nextId.equals(booking.getBookingId())) {
            manager.remove(booking.getBookingId());
            manager.enqueueIfEligible(requireContext(), booking);
            nextId = manager.peekNext();
            if (nextId == null || !nextId.equals(booking.getBookingId())) {
                return;
            }
        }
        manager.pollNext();
        manager.setCurrentlyShowing(booking.getBookingId());
        manager.markShown(requireContext(), booking.getBookingId());

        String serviceName = booking.getServiceName();
        if ((serviceName == null || serviceName.isEmpty()) && booking.hasServiceDetails() && !booking.getServiceDetails().isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < booking.getServiceDetails().size(); i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                sb.append(booking.getServiceDetails().get(i).getName());
            }
            serviceName = sb.toString();
        }
        if (serviceName == null || serviceName.isEmpty()) {
            serviceName = "-";
        }
        String clientName = booking.getClientName();
        if (clientName == null || clientName.isEmpty()) {
            clientName = "Unknown client";
        }
        String start = "-";
        String end = "-";
        if (booking.getDateTime() != null) {
            SimpleDateFormat fmt = new SimpleDateFormat("HH:mm", Locale.getDefault());
            start = fmt.format(booking.getDateTime());
            end = fmt.format(new java.util.Date(booking.getEndTimeMillis()));
        }
        String message = "Service: " + serviceName + "\nClient: " + clientName + "\nScheduled time: " + start + " – " + end + "\n\nHas this appointment finished?";

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext());
        builder.setTitle("Is this service complete?");
        builder.setMessage(message);
        builder.setCancelable(false);
        builder.setPositiveButton("Yes, mark as complete", (dialog, which) -> {
            manager.clearCurrentlyShowing(booking.getBookingId());
            bookingManager.updateBookingStatus(booking.getBookingId(), "completed",
                    new BookingManager.BookingCallback() {
                        @Override
                        public void onSuccess() {
                            if (!isAdded()) {
                                return;
                            }
                            booking.setStatus("completed");
                            manager.remove(booking.getBookingId());
                            refreshList();
                            checkForCompletionReminders();
                        }

                        @Override
                        public void onFailure(String errorMessage) {
                            if (!isAdded()) {
                                return;
                            }
                            Toast.makeText(getContext(), "Could not update booking", Toast.LENGTH_SHORT).show();
                            // put back to queue to retry later
                            manager.enqueueIfEligible(requireContext(), booking);
                            checkForCompletionReminders();
                        }
                    });
            dialog.dismiss();
        });
        builder.setNeutralButton("Not yet — service is still in progress", (dialog, which) -> {
            manager.clearCurrentlyShowing(booking.getBookingId());
            manager.deferNotYet(requireContext(), booking.getBookingId());
            dialog.dismiss();
            checkForCompletionReminders();
        });
        builder.setNegativeButton("Remind me later", (dialog, which) -> {
            manager.clearCurrentlyShowing(booking.getBookingId());
            manager.deferRemindLater(requireContext(), booking.getBookingId());
            dialog.dismiss();
            checkForCompletionReminders();
        });
        AlertDialog dialog = builder.create();
        dialog.setCanceledOnTouchOutside(false);
        dialog.show();
    }
}