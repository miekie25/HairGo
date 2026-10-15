package com.hairgo.app.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.firestore.ListenerRegistration;
import com.hairgo.app.R;
import com.hairgo.app.firebase.BookingManager;
import com.hairgo.app.models.OwnerBooking;
import com.hairgo.app.utils.OwnerSession;

import java.util.Calendar;
import java.util.List;
import java.util.Map;

public class OwnerHomeFragment extends Fragment {

    private BookingManager bookingManager;
    private ListenerRegistration statsListener;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_owner_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        bookingManager = new BookingManager();
        setGreeting(view);
        wireQuickActions(view);
    }

    /**
     * Listens while the fragment is on screen so the stat cards stay current in
     * real time (a new booking or a status change updates the counts). The
     * listener is removed in onStop so it does not keep reading in the background.
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
            // No salon yet: the cards have nothing to count.
            resetStats();
            return;
        }

        statsListener = bookingManager.listenToBookingsForSalon(salonId, new BookingManager.BookingListCallback() {
            @Override
            public void onSuccess(List<Map<String, Object>> raw) {
                if (!isAdded() || getView() == null) return;

                int today = 0, pending = 0, confirmed = 0, completed = 0;
                for (Map<String, Object> map : raw) {
                    OwnerBooking booking = OwnerBooking.fromMap(map);
                    if (booking == null) continue;

                    if (booking.isToday()) today++;
                    if ("pending".equals(booking.getStatus())) pending++;
                    if ("confirmed".equals(booking.getStatus()) && !booking.isPast()) confirmed++;
                    if ("completed".equals(booking.getStatus())) completed++;
                }
                setText(R.id.tvTodayBookingsCount, String.valueOf(today));
                setText(R.id.tvPendingCount, String.valueOf(pending));
                setText(R.id.tvConfirmedCount, String.valueOf(confirmed));
                setText(R.id.tvCompletedCount, String.valueOf(completed));
            }

            @Override
            public void onFailure(String errorMessage) {
                // Leave the current values; the Bookings tab reports load errors.
            }
        });
    }

    private void stopListening() {
        if (statsListener != null) {
            statsListener.remove();
            statsListener = null;
        }
    }

    private void resetStats() {
        setText(R.id.tvTodayBookingsCount, "0");
        setText(R.id.tvPendingCount, "0");
        setText(R.id.tvConfirmedCount, "0");
        setText(R.id.tvCompletedCount, "0");
    }

    private void setGreeting(View view) {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        String greeting;
        if (hour < 12) greeting = "Good morning";
        else if (hour < 17) greeting = "Good afternoon";
        else greeting = "Good evening";
        ((TextView) view.findViewById(R.id.tvGreeting)).setText(greeting);
    }

    private void setText(int viewId, String value) {
        View root = getView();
        if (root == null) return;
        ((TextView) root.findViewById(viewId)).setText(value);
    }

    private void wireQuickActions(View view) {
        Button btnViewAllBookings = view.findViewById(R.id.btnViewAllBookings);

        // Selecting the tab on the shared bottom nav lets OwnerDashboardActivity swap the fragment.
        btnViewAllBookings.setOnClickListener(v -> {
            BottomNavigationView nav = requireActivity().findViewById(R.id.ownerBottomNav);
            nav.setSelectedItemId(R.id.nav_owner_bookings);
        });
    }
}