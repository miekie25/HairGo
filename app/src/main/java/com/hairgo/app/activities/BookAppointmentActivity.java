package com.hairgo.app.activities;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.hairgo.app.R;
import com.hairgo.app.models.Salon;
import com.hairgo.app.utils.DummyData;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class BookAppointmentActivity extends AppCompatActivity {

    private Spinner spinnerService;
    private TextView tvSelectedDate, tvSelectedTime, tvError;

    private final Calendar selectedDateTime = Calendar.getInstance();
    private boolean dateChosen = false;
    private boolean timeChosen = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_book_appointment);

        String salonId = getIntent().getStringExtra("salonId");
        Salon salon = DummyData.getDummySalonById(salonId);

        if (salon == null) {
            Toast.makeText(this, "Salon not found.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        TextView headerTitle = findViewById(R.id.header).findViewById(R.id.tvHeaderTitle);
        headerTitle.setText(R.string.title_book_appointment);

        ImageButton backBtn = findViewById(R.id.header).findViewById(R.id.btnBack);
        backBtn.setOnClickListener(v -> onBackPressed());

        TextView tvSalonLabel = findViewById(R.id.tvSalonLabel);
        tvSalonLabel.setText(getString(R.string.title_book_appointment) + " — " + salon.getName());

        spinnerService = findViewById(R.id.spinnerService);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, R.layout.spinner_item, salon.getServices());
        adapter.setDropDownViewResource(R.layout.spinner_dropdown_item);
        spinnerService.setAdapter(adapter);

        tvSelectedDate = findViewById(R.id.tvSelectedDate);
        tvSelectedTime = findViewById(R.id.tvSelectedTime);
        tvError = findViewById(R.id.tvError);

        LinearLayout rowDate = findViewById(R.id.rowDate);
        LinearLayout rowTime = findViewById(R.id.rowTime);

        rowDate.setOnClickListener(v -> showDatePicker());
        rowTime.setOnClickListener(v -> showTimePicker());

        findViewById(R.id.btnConfirmBooking).setOnClickListener(v -> confirmBooking(salon));
    }

    private void showDatePicker() {
        Calendar today = Calendar.getInstance();

        DatePickerDialog dialog = new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
            selectedDateTime.set(Calendar.YEAR, year);
            selectedDateTime.set(Calendar.MONTH, month);
            selectedDateTime.set(Calendar.DAY_OF_MONTH, dayOfMonth);
            dateChosen = true;

            // Reset time when date changes — availability is different per day
            timeChosen = false;
            tvSelectedTime.setText(R.string.hint_choose_time);
            tvSelectedTime.setTextColor(getColor(R.color.grey_medium));

            SimpleDateFormat format = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());
            tvSelectedDate.setText(format.format(selectedDateTime.getTime()));
            tvSelectedDate.setTextColor(getColor(R.color.grey_dark));

        }, today.get(Calendar.YEAR), today.get(Calendar.MONTH), today.get(Calendar.DAY_OF_MONTH));

        dialog.getDatePicker().setMinDate(today.getTimeInMillis());
        dialog.show();
    }

    private void showTimePicker() {
        if (!dateChosen) {
            Toast.makeText(this, "Please select a date first", Toast.LENGTH_SHORT).show();
            return;
        }

        // 1. Build all 30-minute slots for the day (9 AM – 6 PM)
        List<String> allSlots = generateTimeSlots(9, 18);

        // 2. Get booked slots for this date (mock data for now)
        List<String> bookedSlots = getMockBookedTimesForDate(selectedDateTime);

        // 3. Keep only available slots
        List<String> availableSlots = new ArrayList<>();
        for (String slot : allSlots) {
            if (!bookedSlots.contains(slot)) {
                availableSlots.add(slot);
            }
        }

        // 4. Handle fully-booked day
        if (availableSlots.isEmpty()) {
            new AlertDialog.Builder(this)
                    .setTitle("Fully Booked")
                    .setMessage("No available slots for this date. Please choose another date.")
                    .setPositiveButton("OK", null)
                    .show();
            return;
        }

        // 5. Convert 24h slots to friendly 12h display strings
        String[] displaySlots = new String[availableSlots.size()];
        for (int i = 0; i < availableSlots.size(); i++) {
            displaySlots[i] = format24hTo12h(availableSlots.get(i));
        }

        // 6. Show picker dialog
        new AlertDialog.Builder(this)
                .setTitle("Select Time")
                .setItems(displaySlots, (dialog, which) -> {
                    String picked24h = availableSlots.get(which);

                    // Update Calendar object
                    String[] parts = picked24h.split(":");
                    selectedDateTime.set(Calendar.HOUR_OF_DAY, Integer.parseInt(parts[0]));
                    selectedDateTime.set(Calendar.MINUTE, Integer.parseInt(parts[1]));
                    timeChosen = true;

                    // Update UI
                    tvSelectedTime.setText(displaySlots[which]);
                    tvSelectedTime.setTextColor(getColor(R.color.grey_dark));
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    /**
     * Generates 30-minute intervals from startHour up to (but not including) endHour.
     * Returns times in "HH:mm" 24-hour format.
     */
    private List<String> generateTimeSlots(int startHour, int endHour) {
        List<String> slots = new ArrayList<>();
        for (int hour = startHour; hour < endHour; hour++) {
            slots.add(String.format(Locale.getDefault(), "%02d:00", hour));
            slots.add(String.format(Locale.getDefault(), "%02d:30", hour));
        }
        return slots;
    }

    /**
     * TEMP: Mock booked times for frontend testing.
     * Replace this with a real API/DB call in Phase 2.
     */
    private List<String> getMockBookedTimesForDate(Calendar date) {
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        String dateKey = df.format(date.getTime());

        List<String> booked = new ArrayList<>();

        // Demo: block some slots for "today" so you can test the filtering
        Calendar today = Calendar.getInstance();
        if (dateKey.equals(df.format(today.getTime()))) {
            booked.add("10:00");
            booked.add("10:30");
            booked.add("14:00");
            booked.add("14:30");
        }
        // Demo: block afternoon slots for "tomorrow"
        Calendar tomorrow = Calendar.getInstance();
        tomorrow.add(Calendar.DAY_OF_MONTH, 1);
        if (dateKey.equals(df.format(tomorrow.getTime()))) {
            booked.add("13:00");
            booked.add("13:30");
            booked.add("16:00");
            booked.add("16:30");
        }

        return booked;
    }

    /**
     * Converts "HH:mm" to "hh:mm a" (e.g., "14:30" → "02:30 PM").
     */
    private String format24hTo12h(String time24h) {
        try {
            SimpleDateFormat sdf24 = new SimpleDateFormat("HH:mm", Locale.getDefault());
            SimpleDateFormat sdf12 = new SimpleDateFormat("hh:mm a", Locale.getDefault());
            return sdf12.format(sdf24.parse(time24h));
        } catch (Exception e) {
            return time24h;
        }
    }

    private void confirmBooking(Salon salon) {
        if (spinnerService.getSelectedItem() == null || !dateChosen || !timeChosen) {
            showError(getString(R.string.error_incomplete_booking));
            return;
        }

        if (selectedDateTime.before(Calendar.getInstance())) {
            showError(getString(R.string.error_past_datetime));
            return;
        }

        tvError.setVisibility(android.view.View.GONE);

        String selectedService = spinnerService.getSelectedItem().toString();
        Toast.makeText(this,
                selectedService + " at " + salon.getName() + " — " + getString(R.string.booking_demo_success),
                Toast.LENGTH_LONG).show();

        finish();
    }

    private void showError(String message) {
        tvError.setText(message);
        tvError.setVisibility(android.view.View.VISIBLE);
    }
}