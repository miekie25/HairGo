package com.hairgo.app.activities;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.Timestamp;
import com.hairgo.app.R;
import com.hairgo.app.firebase.BookingManager;
import com.hairgo.app.firebase.SalonManager;
import com.hairgo.app.models.Salon;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class BookAppointmentActivity extends AppCompatActivity {

    private TextView tvSelectedServices, tvSelectedDate, tvSelectedTime, tvError;

    private final List<String> availableServices = new ArrayList<>();
    private final List<String> selectedServices = new ArrayList<>();

    private final Calendar selectedDateTime = Calendar.getInstance();
    private boolean dateChosen = false;
    private boolean timeChosen = false;

    /** Non-null when editing/rescheduling an existing booking rather than creating one. */
    private String editingBookingId;
    private List<String> editingServiceNames;
    private long editingDateTimeMillis;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_book_appointment);

        String salonId = getIntent().getStringExtra("salonId");
        if (salonId == null || salonId.isEmpty()) {
            Toast.makeText(this, R.string.salon_not_found, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        editingBookingId = getIntent().getStringExtra("bookingId");
        editingServiceNames = getIntent().getStringArrayListExtra("serviceNames");
        if (editingServiceNames == null) {
            // Older callers only passed a single serviceName.
            String single = getIntent().getStringExtra("serviceName");
            editingServiceNames = new ArrayList<>();
            if (single != null && !single.isEmpty()) editingServiceNames.add(single);
        }
        editingDateTimeMillis = getIntent().getLongExtra("dateTimeMillis", 0L);

        TextView headerTitle = findViewById(R.id.header).findViewById(R.id.tvHeaderTitle);
        headerTitle.setText(isEditing()
                ? R.string.title_reschedule_booking
                : R.string.title_book_appointment);

        ImageButton backBtn = findViewById(R.id.header).findViewById(R.id.btnBack);
        backBtn.setOnClickListener(v -> onBackPressed());

        tvSelectedServices = findViewById(R.id.tvSelectedServices);
        tvSelectedDate = findViewById(R.id.tvSelectedDate);
        tvSelectedTime = findViewById(R.id.tvSelectedTime);
        tvError = findViewById(R.id.tvError);

        LinearLayout rowService = findViewById(R.id.rowService);
        LinearLayout rowDate = findViewById(R.id.rowDate);
        LinearLayout rowTime = findViewById(R.id.rowTime);

        rowService.setOnClickListener(v -> showServicePicker());
        rowDate.setOnClickListener(v -> showDatePicker());
        rowTime.setOnClickListener(v -> showTimePicker());

        new SalonManager().getSalonById(salonId, new SalonManager.SalonDataCallback() {
            @Override
            public void onSuccess(Map<String, Object> salonData) {
                Salon salon = toSalon(salonId, salonData);
                if (salon == null) {
                    showMissingSalon();
                    return;
                }
                bindSalon(salon);
            }

            @Override
            public void onFailure(String errorMessage) {
                Log.w("BookAppointment", "getSalonById failed: " + errorMessage);
                showMissingSalon();
            }
        });
    }

    private Salon toSalon(String salonId, Map<String, Object> data) {
        if (data == null) return null;
        Object name = data.get("name");
        if (name == null || name.toString().trim().isEmpty()) return null;
        Object location = data.get("location");
        double avgRating = 0.0;
        Object ratingValue = data.get("avgRating");
        if (ratingValue instanceof Number) avgRating = ((Number) ratingValue).doubleValue();
        List<String> services = new ArrayList<>();
        Object servicesValue = data.get("services");
        if (servicesValue instanceof List) {
            for (Object item : (List<?>) servicesValue) {
                if (item != null) services.add(item.toString());
            }
        }
        return new Salon(salonId, name.toString(), location == null ? "" : location.toString(), avgRating, services);
    }

    private void bindSalon(Salon salon) {
        TextView tvSalonLabel = findViewById(R.id.tvSalonLabel);
        tvSalonLabel.setText(getString(isEditing()
                ? R.string.title_reschedule_booking
                : R.string.title_book_appointment) + " — " + salon.getName());

        availableServices.clear();
        availableServices.addAll(salon.getServices());
        // A booked service may no longer be listed by the salon; keep it
        // selectable so editing does not silently change what was booked.
        if (isEditing()) {
            for (String booked : editingServiceNames) {
                if (booked != null && !booked.isEmpty() && !availableServices.contains(booked)) {
                    availableServices.add(0, booked);
                }
            }
        }

        if (isEditing()) {
            prefillFromBooking();
        }
        updateServiceSummary();

        findViewById(R.id.btnConfirmBooking).setOnClickListener(v -> confirmBooking(salon));
    }

    private boolean isEditing() {
        return editingBookingId != null && !editingBookingId.isEmpty();
    }

    /** Opens a checkbox list so one or more services can be selected. */
    private void showServicePicker() {
        if (availableServices.isEmpty()) return;

        final boolean[] checked = new boolean[availableServices.size()];
        for (int i = 0; i < availableServices.size(); i++) {
            checked[i] = selectedServices.contains(availableServices.get(i));
        }
        String[] labels = availableServices.toArray(new String[0]);

        new AlertDialog.Builder(this)
                .setTitle(R.string.select_services_title)
                .setMultiChoiceItems(labels, checked,
                        (dialog, which, isChecked) -> checked[which] = isChecked)
                .setPositiveButton(R.string.ok, (dialog, which) -> {
                    selectedServices.clear();
                    for (int i = 0; i < availableServices.size(); i++) {
                        if (checked[i]) selectedServices.add(availableServices.get(i));
                    }
                    updateServiceSummary();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void updateServiceSummary() {
        if (selectedServices.isEmpty()) {
            tvSelectedServices.setText(R.string.hint_choose_services);
            tvSelectedServices.setTextColor(getColor(R.color.grey_medium));
        } else {
            tvSelectedServices.setText(TextUtils.join(", ", selectedServices));
            tvSelectedServices.setTextColor(getColor(R.color.grey_dark));
        }
    }

    /** Restores the existing booking's services, date and time into the form. */
    private void prefillFromBooking() {
        selectedServices.clear();
        for (String booked : editingServiceNames) {
            if (booked != null && !booked.isEmpty()) selectedServices.add(booked);
        }

        if (editingDateTimeMillis > 0L) {
            selectedDateTime.setTimeInMillis(editingDateTimeMillis);
            dateChosen = true;
            timeChosen = true;

            SimpleDateFormat dateFormat = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());
            tvSelectedDate.setText(dateFormat.format(selectedDateTime.getTime()));
            tvSelectedDate.setTextColor(getColor(R.color.grey_dark));

            SimpleDateFormat timeFormat = new SimpleDateFormat("hh:mm a", Locale.getDefault());
            tvSelectedTime.setText(timeFormat.format(selectedDateTime.getTime()));
            tvSelectedTime.setTextColor(getColor(R.color.grey_dark));
        }

        ((TextView) findViewById(R.id.btnConfirmBooking)).setText(R.string.btn_save_changes);
    }

    private void showMissingSalon() {
        Toast.makeText(this, R.string.salon_not_found, Toast.LENGTH_SHORT).show();
        finish();
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
            Toast.makeText(this, R.string.please_select_date_first, Toast.LENGTH_SHORT).show();
            return;
        }

        // Query only the selected day: midnight now, midnight tomorrow.
        Calendar dayStart = (Calendar) selectedDateTime.clone();
        dayStart.set(Calendar.HOUR_OF_DAY, 0);
        dayStart.set(Calendar.MINUTE, 0);
        dayStart.set(Calendar.SECOND, 0);
        dayStart.set(Calendar.MILLISECOND, 0);

        Calendar dayEnd = (Calendar) dayStart.clone();
        dayEnd.add(Calendar.DAY_OF_MONTH, 1);

        new BookingManager().getBookingsForSalonOnDate(
                getIntent().getStringExtra("salonId"),
                dayStart.getTime(),
                dayEnd.getTime(),
                new BookingManager.BookingListCallback() {
                    @Override
                    public void onSuccess(List<Map<String, Object>> bookings) {
                        showAvailableTimes(bookings);
                    }

                    @Override
                    public void onFailure(String errorMessage) {
                        // A missing composite index arrives here first time only.
                        Log.w("BookAppointment", "Availability query failed: " + errorMessage);
                        new AlertDialog.Builder(BookAppointmentActivity.this)
                                .setTitle(R.string.couldnt_load_times_title)
                                .setMessage(String.valueOf(errorMessage))
                                .setPositiveButton(R.string.ok, null)
                                .show();
                    }
                });
    }

    /** Filters the already-booked times out of the day's 30-minute slots. */
    private void showAvailableTimes(List<Map<String, Object>> bookings) {
        List<String> bookedSlots = new ArrayList<>();
        if (bookings != null) {
            SimpleDateFormat hourFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
            for (Map<String, Object> booking : bookings) {
                // Ignore the booking being edited so its own slot stays selectable.
                if (isEditing()) {
                    Object id = booking.get("bookingID");
                    if (id != null && editingBookingId.equals(id.toString())) continue;
                }
                Date when = toDate(booking.get("dateTime"));
                if (when != null) bookedSlots.add(hourFormat.format(when));
            }
        }

        List<String> availableSlots = new ArrayList<>();
        for (String slot : generateTimeSlots(9, 18)) {
            if (!bookedSlots.contains(slot)) {
                availableSlots.add(slot);
            }
        }

        if (availableSlots.isEmpty()) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.fully_booked_title)
                    .setMessage(R.string.fully_booked_message)
                    .setPositiveButton(R.string.ok, null)
                    .show();
            return;
        }

        String[] displaySlots = new String[availableSlots.size()];
        for (int i = 0; i < availableSlots.size(); i++) {
            displaySlots[i] = format24hTo12h(availableSlots.get(i));
        }

        new AlertDialog.Builder(this)
                .setTitle(R.string.select_time_title)
                .setItems(displaySlots, (dialog, which) -> {
                    String picked24h = availableSlots.get(which);

                    String[] parts = picked24h.split(":");
                    selectedDateTime.set(Calendar.HOUR_OF_DAY, Integer.parseInt(parts[0]));
                    selectedDateTime.set(Calendar.MINUTE, Integer.parseInt(parts[1]));
                    timeChosen = true;

                    tvSelectedTime.setText(displaySlots[which]);
                    tvSelectedTime.setTextColor(getColor(R.color.grey_dark));
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    /** Firestore returns a Timestamp, but a Date is accepted too. */
    private Date toDate(Object value) {
        if (value instanceof Timestamp) return ((Timestamp) value).toDate();
        if (value instanceof Date) return (Date) value;
        return null;
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
        if (selectedServices.isEmpty() || !dateChosen || !timeChosen) {
            showError(getString(R.string.error_incomplete_booking));
            return;
        }

        if (selectedDateTime.before(Calendar.getInstance())) {
            showError(getString(R.string.error_past_datetime));
            return;
        }

        String clientId = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid()
                : null;
        if (clientId == null) {
            showError(getString(R.string.salon_not_found));
            return;
        }

        tvError.setVisibility(android.view.View.GONE);
        findViewById(R.id.btnConfirmBooking).setEnabled(false);

        if (isEditing()) {
            updateBooking();
            return;
        }

        new BookingManager().createBooking(
                clientId,
                salon.getSalonId(),
                salon.getName(),
                new ArrayList<>(selectedServices),
                selectedDateTime.getTime(),
                new BookingManager.BookingCreatedCallback() {
                    @Override
                    public void onSuccess(String bookingId) {
                        Toast.makeText(BookAppointmentActivity.this,
                                R.string.booking_created_success, Toast.LENGTH_SHORT).show();
                        openBookingsTab();
                    }

                    @Override
                    public void onFailure(String errorMessage) {
                        Log.w("BookAppointment", "createBooking failed: " + errorMessage);
                        findViewById(R.id.btnConfirmBooking).setEnabled(true);
                        showError(errorMessage);
                    }
                });
    }

    /**
     * Saves an edit or reschedule. The booking keeps its id but returns to
     * pending, so the salon re-confirms the new details.
     */
    private void updateBooking() {
        new BookingManager().updateBookingDetails(
                editingBookingId,
                new ArrayList<>(selectedServices),
                selectedDateTime.getTime(),
                new BookingManager.BookingCallback() {
                    @Override
                    public void onSuccess() {
                        Toast.makeText(BookAppointmentActivity.this,
                                R.string.booking_updated, Toast.LENGTH_SHORT).show();
                        setResult(RESULT_OK);
                        finish();
                    }

                    @Override
                    public void onFailure(String errorMessage) {
                        Log.w("BookAppointment", "updateBookingDetails failed: " + errorMessage);
                        findViewById(R.id.btnConfirmBooking).setEnabled(true);
                        showError(errorMessage);
                    }
                });
    }

    /** Returns to the dashboard already showing the bookings tab. */
    private void openBookingsTab() {
        Intent intent = new Intent(this, ClientDashboardActivity.class);
        intent.putExtra(ClientDashboardActivity.EXTRA_SELECTED_TAB, R.id.nav_bookings);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }

    private void showError(String message) {
        tvError.setText(message);
        tvError.setVisibility(android.view.View.VISIBLE);
    }
}