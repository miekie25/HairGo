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
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.hairgo.app.R;
import com.hairgo.app.activities.BookingDetailsActivity;
import com.hairgo.app.adapters.BookingAdapter;
import com.hairgo.app.models.Booking;
import com.hairgo.app.utils.DummyData;

import java.util.List;

public class BookingsFragment extends Fragment {

    private RecyclerView rvBookings;
    private TextView tvEmptyState;

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

        // TEMP: dummy data for design phase — replaced with BookingManager.getBookingsForClient() in Phase 2
        List<Booking> dummyBookings = DummyData.getDummyBookings();

        if (dummyBookings.isEmpty()) {
            rvBookings.setVisibility(View.GONE);
            tvEmptyState.setVisibility(View.VISIBLE);
        } else {
            rvBookings.setAdapter(new BookingAdapter(dummyBookings, booking -> {
                Intent intent = new Intent(getContext(), BookingDetailsActivity.class);
                intent.putExtra("bookingId", booking.getBookingId());
                startActivity(intent);
            }));
        }
    }
}