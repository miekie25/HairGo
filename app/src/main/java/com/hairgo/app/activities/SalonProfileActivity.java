package com.hairgo.app.activities;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.Timestamp;
import com.hairgo.app.R;
import com.hairgo.app.adapters.ServiceAdapter;
import com.hairgo.app.firebase.BookingManager;
import com.hairgo.app.firebase.SalonManager;
import com.hairgo.app.models.Salon;

import java.time.LocalDate;
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_salon_profile);

        String salonId = getIntent().getStringExtra("salonId");

        ImageButton backBtn = findViewById(R.id.header).findViewById(R.id.btnBack);
        backBtn.setOnClickListener(v -> onBackPressed());

        if (salonId == null || salonId.isEmpty()) {
            showMissingSalon();
            return;
        }

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
    }

    /**
     * Shows the earliest coming day this salon has no booking on.
     *
     * <p>The booking screen is what actually stops a past date being chosen
     * (setMinDate on the picker plus a check before confirming). Surfacing the
     * next free day here means the rule is visible before the user commits to
     * booking, instead of them discovering it in a rejection message.
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
                        LocalDate next = findNextFreeDay(bookings);
                        if (next == null) return;

                        DateTimeFormatter formatter =
                                DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault());
                        tvNextAvailable.setText(getString(
                                R.string.salon_next_available, next.format(formatter)));
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
     * Walks forward from today and returns the first day with no booking on it,
     * or null if the salon is fully booked for the whole lookahead window.
     */
    private LocalDate findNextFreeDay(List<Map<String, Object>> bookings) {
        Set<String> bookedDays = new HashSet<>();

        if (bookings != null) {
            for (Map<String, Object> booking : bookings) {
                if (booking == null) continue;
                Object dateTime = booking.get("dateTime");
                if (!(dateTime instanceof Timestamp)) continue;

                Date date = ((Timestamp) dateTime).toDate();
                bookedDays.add(toDayKey(date));
            }
        }

        LocalDate today = LocalDate.now();
        for (int offset = 0; offset < AVAILABILITY_LOOKAHEAD_DAYS; offset++) {
            LocalDate candidate = today.plusDays(offset);
            if (!bookedDays.contains(candidate.toString())) {
                return candidate;
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
        Toast.makeText(this, R.string.salon_not_found, Toast.LENGTH_SHORT).show();
        finish();
    }
}
