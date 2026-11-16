package com.hairgo.app.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CalendarView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.firestore.ListenerRegistration;
import com.hairgo.app.R;
import com.hairgo.app.adapters.OwnerCalendarAdapter;
import com.hairgo.app.firebase.OwnerManager;
import com.hairgo.app.models.OwnerBooking;
import com.hairgo.app.utils.OwnerSession;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Salon owner's day calendar. It uses the same live Firestore booking feed and
 * selected-salon session as the existing Owner Bookings tab.
 */
public class OwnerCalendarFragment extends Fragment {

    private final List<OwnerBooking> allBookings = new ArrayList<>();
    private final List<OwnerBooking> dayBookings = new ArrayList<>();

    private TextView tvSelectedDate;
    private TextView tvBookingCount;
    private TextView tvEmpty;
    private OwnerCalendarAdapter adapter;
    private OwnerManager ownerManager;
    private ListenerRegistration bookingsListener;
    private Calendar selectedDay;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_owner_calendar, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        ownerManager = new OwnerManager();
        CalendarView calendarView = view.findViewById(R.id.ownerCalendarView);
        tvSelectedDate = view.findViewById(R.id.tvCalendarSelectedDate);
        tvBookingCount = view.findViewById(R.id.tvCalendarBookingCount);
        tvEmpty = view.findViewById(R.id.tvCalendarEmpty);

        RecyclerView recyclerView = view.findViewById(R.id.rvCalendarBookings);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new OwnerCalendarAdapter(dayBookings);
        recyclerView.setAdapter(adapter);

        selectedDay = Calendar.getInstance();
        updateSelectedDateHeading();

        calendarView.setOnDateChangeListener((calendar, year, month, dayOfMonth) -> {
            selectedDay.set(Calendar.YEAR, year);
            selectedDay.set(Calendar.MONTH, month);
            selectedDay.set(Calendar.DAY_OF_MONTH, dayOfMonth);
            selectedDay.set(Calendar.HOUR_OF_DAY, 0);
            selectedDay.set(Calendar.MINUTE, 0);
            selectedDay.set(Calendar.SECOND, 0);
            selectedDay.set(Calendar.MILLISECOND, 0);
            updateSelectedDateHeading();
            applySelectedDay();
        });
    }

    @Override
    public void onStart() {
        super.onStart();
        startListening();
    }

    @Override
    public void onStop() {
        super.onStop();
        removeListener();
    }

    @Override
    public void onDestroyView() {
        removeListener();
        super.onDestroyView();
    }

    private void startListening() {
        removeListener();

        String salonId = OwnerSession.getSelectedSalonId();
        allBookings.clear();
        applySelectedDay();

        if (salonId == null || salonId.trim().isEmpty()) {
            showEmpty("Add or select a salon to view its calendar.");
            return;
        }

        bookingsListener = ownerManager.listenToSalonBookings(salonId,
                new com.hairgo.app.firebase.BookingManager.BookingListCallback() {
                    @Override
                    public void onSuccess(List<Map<String, Object>> rawBookings) {
                        if (!isAdded()) return;
                        allBookings.clear();
                        if (rawBookings != null) {
                            for (Map<String, Object> raw : rawBookings) {
                                OwnerBooking booking = OwnerBooking.fromMap(raw);
                                if (booking != null && booking.getDateTime() != null) {
                                    allBookings.add(booking);
                                    loadMissingClientName(booking);
                                }
                            }
                        }
                        applySelectedDay();
                    }

                    @Override
                    public void onFailure(String errorMessage) {
                        if (!isAdded()) return;
                        Toast.makeText(requireContext(),
                                "Could not load calendar bookings: " + errorMessage,
                                Toast.LENGTH_LONG).show();
                        showEmpty("Bookings could not be loaded. Please try again.");
                    }
                });
    }

    private void loadMissingClientName(OwnerBooking booking) {
        String clientId = booking.getClientId();
        if (booking.getClientName() != null && !booking.getClientName().trim().isEmpty()) return;
        if (clientId == null || clientId.trim().isEmpty()) return;

        String cached = OwnerSession.getCachedClientName(clientId);
        if (cached != null) {
            booking.setClientName(cached);
            int index = dayBookings.indexOf(booking);
            if (index >= 0) {
                adapter.notifyItemChanged(index);
            } else {
                adapter.notifyDataSetChanged();
            }
            return;
        }

        ownerManager.getClientName(clientId, new OwnerManager.OwnerCallback<>() {
            @Override
            public void onSuccess(String name) {
                if (!isAdded()) return;
                OwnerSession.cacheClientName(clientId, name);
                booking.setClientName(name);
                int index = dayBookings.indexOf(booking);
                if (index >= 0) {
                    adapter.notifyItemChanged(index);
                } else {
                    adapter.notifyDataSetChanged();
                }
            }

            @Override
            public void onFailure(String errorMessage) {
                // Keep the booking visible with the safe "Unknown client" fallback.
            }
        });
    }

    private void applySelectedDay() {
        if (adapter == null || selectedDay == null) return;

        dayBookings.clear();
        for (OwnerBooking booking : allBookings) {
            if (isSameDay(booking.getDateTime(), selectedDay)) {
                // Keep canceled bookings visible so the owner can see the full day's history.
                dayBookings.add(booking);
            }
        }

        dayBookings.sort(Comparator.comparing(OwnerBooking::getDateTime));

        adapter.notifyDataSetChanged();
        tvBookingCount.setText(dayBookings.size() == 1
                ? "1 booking" : dayBookings.size() + " bookings");

        if (dayBookings.isEmpty()) {
            showEmpty("No bookings for this day.");
        } else {
            tvEmpty.setVisibility(View.GONE);
        }
    }

    private boolean isSameDay(Date date, Calendar day) {
        if (date == null) return false;
        Calendar bookingDay = Calendar.getInstance();
        bookingDay.setTime(date);
        return bookingDay.get(Calendar.YEAR) == day.get(Calendar.YEAR)
                && bookingDay.get(Calendar.DAY_OF_YEAR) == day.get(Calendar.DAY_OF_YEAR);
    }

    private void updateSelectedDateHeading() {
        if (tvSelectedDate == null || selectedDay == null) return;
        SimpleDateFormat format = new SimpleDateFormat("EEEE, d MMMM yyyy", Locale.getDefault());
        tvSelectedDate.setText(format.format(selectedDay.getTime()));
    }

    private void showEmpty(String message) {
        if (tvEmpty != null) {
            tvEmpty.setText(message);
            tvEmpty.setVisibility(View.VISIBLE);
        }
        if (tvBookingCount != null) tvBookingCount.setText(getString(R.string.owner_zero_bookings));
    }

    private void removeListener() {
        if (bookingsListener != null) {
            bookingsListener.remove();
            bookingsListener = null;
        }
    }
}