package com.hairgo.app.fragments;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import com.hairgo.app.R;
import com.hairgo.app.adapters.OwnerBookingAdapter;
import com.hairgo.app.models.OwnerBooking;
import com.hairgo.app.utils.DashboardData;
import java.util.List;

public class OwnerBookingsFragment extends Fragment {

    private List<OwnerBooking> bookingList;
    private OwnerBookingAdapter adapter;
    private TextView tvEmpty;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_owner_bookings, container, false);

        RecyclerView rv = view.findViewById(R.id.rvOwnerBookings);
        tvEmpty = view.findViewById(R.id.tvBookingsEmpty);
        SwipeRefreshLayout swipeRefresh = view.findViewById(R.id.swipeRefreshBookings);

        bookingList = DashboardData.getBookings();

        adapter = new OwnerBookingAdapter(bookingList, new OwnerBookingAdapter.OnBookingActionListener() {
            @Override
            public void onConfirm(OwnerBooking booking, int position) {
                booking.setStatus("confirmed");
                adapter.notifyItemChanged(position);
            }

            @Override
            public void onDecline(OwnerBooking booking, int position) {
                booking.setStatus("cancelled");
                adapter.notifyItemChanged(position);
            }
        });

        rv.setLayoutManager(new LinearLayoutManager(getContext()));
        rv.setAdapter(adapter);

        swipeRefresh.setOnRefreshListener(() ->
                new Handler(Looper.getMainLooper()).postDelayed(
                        () -> swipeRefresh.setRefreshing(false), 800));

        updateEmptyState();
        return view;
    }

    private void updateEmptyState() {
        tvEmpty.setVisibility(bookingList.isEmpty() ? View.VISIBLE : View.GONE);
    }
}