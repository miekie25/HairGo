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
import com.hairgo.app.models.OwnerBooking;
import com.hairgo.app.models.StaffMember;
import com.hairgo.app.utils.DashboardData;
import java.util.Calendar;
import java.util.List;

public class OwnerHomeFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_owner_home, container, false);
        setGreeting(view);
        refreshStats(view);
        wireQuickActions(view);
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getView() != null) refreshStats(getView());
    }

    private void setGreeting(View view) {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        String greeting;
        if (hour < 12) greeting = "Good morning";
        else if (hour < 17) greeting = "Good afternoon";
        else greeting = "Good evening";
        ((TextView) view.findViewById(R.id.tvGreeting)).setText(greeting);
    }

    private void refreshStats(View view) {
        List<OwnerBooking> bookings = DashboardData.getBookings();
        List<StaffMember> staff = DashboardData.getStaff();

        int todayCount = 0, pendingCount = 0, revenue = 0;
        for (OwnerBooking b : bookings) {
            if (b.getDateTime().startsWith("Today")) todayCount++;
            if ("pending".equals(b.getStatus())) pendingCount++;
            if ("confirmed".equals(b.getStatus()) || "completed".equals(b.getStatus())) revenue += b.getPrice();
        }

        ((TextView) view.findViewById(R.id.tvTodayBookingsCount)).setText(String.valueOf(todayCount));
        ((TextView) view.findViewById(R.id.tvPendingCount)).setText(String.valueOf(pendingCount));
        ((TextView) view.findViewById(R.id.tvMonthRevenue)).setText("R" + revenue);
        ((TextView) view.findViewById(R.id.tvStaffCount)).setText(String.valueOf(staff.size()));
    }

    private void wireQuickActions(View view) {
        Button btnViewAllBookings = view.findViewById(R.id.btnViewAllBookings);
        Button btnManageStaff = view.findViewById(R.id.btnManageStaff);

        btnViewAllBookings.setOnClickListener(v -> {
            BottomNavigationView nav = requireActivity().findViewById(R.id.ownerBottomNav);
            nav.setSelectedItemId(R.id.nav_owner_bookings);
        });
        btnManageStaff.setOnClickListener(v -> {
            BottomNavigationView nav = requireActivity().findViewById(R.id.ownerBottomNav);
            nav.setSelectedItemId(R.id.nav_owner_staff);
        });
    }
}