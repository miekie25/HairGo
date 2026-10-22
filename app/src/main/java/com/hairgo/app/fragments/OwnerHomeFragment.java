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
import com.hairgo.app.R;
import com.hairgo.app.activities.OwnerDashboardActivity;
import com.hairgo.app.firebase.BookingManager;
import com.hairgo.app.models.OwnerBooking;
import com.hairgo.app.models.PaymentDetails;
import com.hairgo.app.utils.OwnerSession;

import java.math.BigDecimal;
import java.util.Calendar;
import java.util.List;
import java.util.Map;

public class OwnerHomeFragment extends Fragment {

    private BookingManager bookingManager;

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

    @Override
    public void onResume() {
        super.onResume();
        refreshStats();
    }

    private void setGreeting(View view) {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        String greeting;
        if (hour < 12) greeting = "Good morning";
        else if (hour < 17) greeting = "Good afternoon";
        else greeting = "Good evening";
        ((TextView) view.findViewById(R.id.tvGreeting)).setText(greeting);
    }

    private void refreshStats() {
        String salonId = OwnerSession.getSelectedSalonId();
        if (salonId == null) return; // layout already shows zeros

        bookingManager.getBookingsForSalon(salonId, new BookingManager.BookingListCallback() {
            @Override
            public void onSuccess(List<Map<String, Object>> raw) {
                if (!isAdded() || getView() == null) return;

                int today = 0, pending = 0, confirmed = 0, completed = 0;
                BigDecimal totalValue = BigDecimal.ZERO;
                BigDecimal amountPaid = BigDecimal.ZERO;
                BigDecimal outstandingBalance = BigDecimal.ZERO;
                int bookingsWithOutstanding = 0;

                for (Map<String, Object> map : raw) {
                    OwnerBooking booking = OwnerBooking.fromMap(map);
                    if (booking == null) continue;

                    // Cancelled and expired bookings stay in the list now, so they must not count.
                    boolean live = !"cancelled".equals(booking.getStatus())
                            && !"expired".equals(booking.getStatus());
                    if (booking.isToday() && live) today++;
                    if ("pending".equals(booking.getStatus())) pending++;
                    if ("confirmed".equals(booking.getStatus()) && !booking.isPast()) confirmed++;
                    if ("completed".equals(booking.getStatus())) completed++;

                    // Payment summary calculations (only for bookings with known prices)
                    Double bookingTotal = booking.getTotalPrice();
                    if (bookingTotal != null) {
                        totalValue = totalValue.add(BigDecimal.valueOf(bookingTotal));
                    }

                    PaymentDetails payment = booking.getPaymentDetails();
                    if (payment != null) {
                        Double paid = payment.getAmountPaid();
                        if (paid != null) {
                            amountPaid = amountPaid.add(BigDecimal.valueOf(paid));
                        }
                        Double outstanding = payment.getAmountOutstanding();
                        if (outstanding != null && outstanding > 0) {
                            outstandingBalance = outstandingBalance.add(BigDecimal.valueOf(outstanding));
                            bookingsWithOutstanding++;
                        }
                    }
                }
                setText(R.id.tvTodayBookingsCount, String.valueOf(today));
                setText(R.id.tvPendingCount, String.valueOf(pending));
                setText(R.id.tvConfirmedCount, String.valueOf(confirmed));
                setText(R.id.tvCompletedCount, String.valueOf(completed));

                // Payment summary
                setText(R.id.tvTotalValue, formatPrice(totalValue));
                setText(R.id.tvAmountPaid, formatPrice(amountPaid));
                setText(R.id.tvOutstandingBalance, formatPrice(outstandingBalance));
                setText(R.id.tvBookingsWithOutstanding, String.valueOf(bookingsWithOutstanding));
            }

            @Override
            public void onFailure(String errorMessage) {
                // Leave the zeros; the Bookings tab reports load errors.
            }
        });
    }

    private String formatPrice(BigDecimal value) {
        if (value == null || value.compareTo(BigDecimal.ZERO) == 0) {
            return "R0";
        }
        // Strip trailing zeros: 150.0 -> "R150", 150.50 -> "R150.5"
        BigDecimal stripped = value.stripTrailingZeros();
        return "R" + stripped.toPlainString();
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

        // Each stat card opens the Bookings tab pre-filtered to its slice.
        view.findViewById(R.id.cardTodayBookings).setOnClickListener(v ->
                openBookingsWithFilter(OwnerBookingsFragment.FILTER_TODAY));
        view.findViewById(R.id.cardPending).setOnClickListener(v ->
                openBookingsWithFilter(OwnerBookingsFragment.FILTER_PENDING));
        view.findViewById(R.id.cardConfirmed).setOnClickListener(v ->
                openBookingsWithFilter(OwnerBookingsFragment.FILTER_UPCOMING));
        view.findViewById(R.id.cardCompleted).setOnClickListener(v ->
                openBookingsWithFilter(OwnerBookingsFragment.FILTER_COMPLETED));
    }

    private void openBookingsWithFilter(int filter) {
        ((OwnerDashboardActivity) requireActivity()).setPendingBookingsFilter(filter);
        BottomNavigationView nav = requireActivity().findViewById(R.id.ownerBottomNav);
        nav.setSelectedItemId(R.id.nav_owner_bookings);
    }
}