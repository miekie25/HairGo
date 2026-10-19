package com.hairgo.app.activities;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.Timestamp;
import com.hairgo.app.R;
import com.hairgo.app.adapters.ServiceAdapter;
import com.hairgo.app.firebase.BookingManager;
import com.hairgo.app.firebase.SalonManager;
import com.hairgo.app.fragments.ReportProblemBottomSheet;
import com.hairgo.app.models.Salon;
import com.hairgo.app.utils.Snackbars;

import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class SalonProfileActivity extends AppCompatActivity {

    private static final String TAG = "SalonProfileActivity";

    /** How far ahead to look for a free day before giving up on showing a date. */
    private static final int AVAILABILITY_LOOKAHEAD_DAYS = 30;

    private TextView tvNextAvailable;
    private ProgressBar progress;
    private View contentGroup;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_salon_profile);

        String salonId = getIntent().getStringExtra("salonId");

        ImageButton backBtn = findViewById(R.id.header).findViewById(R.id.btnBack);
        backBtn.setOnClickListener(v -> onBackPressed());

        progress = findViewById(R.id.progress);
        contentGroup = findViewById(R.id.contentGroup);

        if (salonId == null || salonId.isEmpty()) {
            showMissingSalon();
            return;
        }

        progress.setVisibility(View.VISIBLE);
        contentGroup.setVisibility(View.INVISIBLE);

        new SalonManager().getSalonById(salonId, new SalonManager.SalonDataCallback() {
            @Override
            public void onSuccess(Map<String, Object> salonData) {
                Salon salon = toSalon(salonId, salonData);
                if (salon == null) {
                    showMissingSalon();
                    return;
                }
                bindSalon(salon);
                loadNextAvailableDate(salonId);
            }

            @Override
            public void onFailure(String errorMessage) {
                showMissingSalon();
            }
        });
    }

    /**
     * Converts the raw Firestore map into our Salon model. Every field is
     * null-checked because a document is allowed to be missing any of them.
     */
    private Salon toSalon(String salonId, Map<String, Object> data) {
        if (data == null) return null;

        Object name = data.get("name");
        if (name == null || name.toString().trim().isEmpty()) return null;

        Object location = data.get("location");
        double avgRating = 0.0;
        Object ratingValue = data.get("avgRating");
        if (ratingValue instanceof Number) {
            avgRating = ((Number) ratingValue).doubleValue();
        }

        List<String> services = new ArrayList<>();
        Object servicesValue = data.get("services");
        if (servicesValue instanceof List) {
            for (Object item : (List<?>) servicesValue) {
                if (item != null) services.add(item.toString());
            }
        }

        return new Salon(salonId, name.toString(),
                location == null ? "" : location.toString(),
                avgRating, services);
    }

    private void bindSalon(Salon salon) {
        progress.setVisibility(View.GONE);
        contentGroup.setVisibility(View.VISIBLE);

        TextView headerTitle = findViewById(R.id.header).findViewById(R.id.tvHeaderTitle);
        headerTitle.setText(salon.getName());

        TextView tvSalonName = findViewById(R.id.tvSalonName);
        TextView tvSalonLocation = findViewById(R.id.tvSalonLocation);
        TextView tvSalonRating = findViewById(R.id.tvSalonRating);

        tvSalonName.setText(salon.getName());
        tvSalonLocation.setText(salon.getLocation());
        tvSalonRating.setText(String.format(Locale.getDefault(), "%.1f", salon.getAvgRating()));

        RecyclerView rvServices = findViewById(R.id.rvServices);
        rvServices.setLayoutManager(new LinearLayoutManager(this));
        rvServices.setAdapter(new ServiceAdapter(salon.getServices()));

        findViewById(R.id.btnBook).setOnClickListener(v -> {
            Intent intent = new Intent(this, BookAppointmentActivity.class);
            intent.putExtra("salonId", salon.getSalonId());
            startActivity(intent);
        });

        // Report the salon to the platform, pre-filled with this salon.
        findViewById(R.id.btnReportSalon).setOnClickListener(v ->
                ReportProblemBottomSheet
                        .newInstanceForSalon(salon.getSalonId(), salon.getName(), null)
                        .show(getSupportFragmentManager(), "report_problem"));
    }

    /**
     * Shows the earliest slot the salon can actually be booked for.
     *
     * <p>The value comes from the same rules as the booking screen: the shared
     * 9:00–17:30 / 30-minute grid, the same "cancelled/expired do not hold a
     * slot" filter, and the same minimum-notice window — so this line can never
     * advertise a day while the picker refuses it, or hide a day the picker
     * offers.
     *
     * <p>The row stays hidden if the lookup fails, for example when the
     * composite index on salonID + isDeleted + dateTime has not been created.
     */
    private void loadNextAvailableDate(String salonId) {
            tvNextAvailable = findViewById(R.id.tvNextAvailable);
        tvNextAvailable.setVisibility(View.GONE);

        Calendar startOfToday = Calendar.getInstance();
        startOfToday.set(Calendar.HOUR_OF_DAY, 0);
        startOfToday.set(Calendar.MINUTE, 0);
        startOfToday.set(Calendar.SECOND, 0);
        startOfToday.set(Calendar.MILLISECOND, 0);

        new BookingManager().getFutureBookingsForSalon(salonId, startOfToday.getTime(),
                new BookingManager.BookingListCallback() {
                    @Override
                    public void onSuccess(List<Map<String, Object>> bookings) {
                        LocalDateTime next = findNextFreeSlot(bookings);
                        DateTimeFormatter formatter =
                                DateTimeFormatter.ofPattern("EEE d MMM, hh:mm a", Locale.getDefault());
                        if (next == null) {
                            tvNextAvailable.setText(getString(
                                    R.string.salon_no_available_slots, AVAILABILITY_LOOKAHEAD_DAYS));
                        } else {
                            tvNextAvailable.setText(getString(
                                    R.string.salon_next_available, next.format(formatter)));
                        }
                        tvNextAvailable.setVisibility(View.VISIBLE);
                    }

                    @Override
                    public void onFailure(String errorMessage) {
                        // Availability is extra information, so a failed lookup
                        // leaves the rest of the screen working.
                        Log.w(TAG, "Could not load availability for salon " + salonId
                                + ": " + errorMessage);
                    }
                });
    }

    /**
     * Walks forward from today and returns the first slot that is not held by a
     * booking and is at least BookingManager.MIN_NOTICE_MINUTES away — i.e. the
     * first slot the booking screen would actually offer — or null if the
     * lookahead window has nothing bookable left.
     */
    private LocalDateTime findNextFreeSlot(List<Map<String, Object>> bookings) {
        Set<String> heldSlots = new HashSet<>();

        if (bookings != null) {
            SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
            for (Map<String, Object> booking : bookings) {
                if (booking == null) continue;
                Object dateTime = booking.get("dateTime");
                Date date = null;
                if (dateTime instanceof Timestamp) date = ((Timestamp) dateTime).toDate();
                else if (dateTime instanceof Date) date = (Date) dateTime;
                if (date == null) continue;

                heldSlots.add(toDayKey(date) + " " + timeFormat.format(date));
            }
        }

        // Same cutoff as the booking screen: nothing before now + notice.
        LocalDateTime cutoff =
                LocalDateTime.now().plusMinutes(BookingManager.MIN_NOTICE_MINUTES);

        LocalDate today = LocalDate.now();
        for (int offset = 0; offset < AVAILABILITY_LOOKAHEAD_DAYS; offset++) {
            LocalDate day = today.plusDays(offset);
            for (String slot : BookingManager.generateTimeSlots()) {
                String[] parts = slot.split(":");
                LocalDateTime candidate = LocalDateTime.of(day,
                        LocalTime.of(Integer.parseInt(parts[0]), Integer.parseInt(parts[1])));
                if (candidate.isBefore(cutoff)) continue;
                if (!heldSlots.contains(day + " " + slot)) return candidate;
            }
        }
        return null;
    }

    private String toDayKey(Date date) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        return String.format(Locale.US, "%04d-%02d-%02d",
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH) + 1,
                calendar.get(Calendar.DAY_OF_MONTH));
    }

    private void showMissingSalon() {
        progress.setVisibility(View.GONE);
        Snackbars.showThen(findViewById(android.R.id.content),
                R.string.salon_not_found, this::finish);
    }
}
