package com.hairgo.app.activities;

import android.app.DatePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.Timestamp;
import com.hairgo.app.R;

import com.hairgo.app.firebase.BookingManager;
import com.hairgo.app.firebase.SalonManager;
import com.hairgo.app.models.Booking;
import com.hairgo.app.models.PaymentDetails;
import com.hairgo.app.models.Salon;
import com.hairgo.app.models.ServiceDetail;
import com.hairgo.app.utils.Notifications;
import com.hairgo.app.utils.ReminderScheduler;
import com.hairgo.app.utils.SalonHours;
import com.hairgo.app.utils.Snackbars;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class BookAppointmentActivity extends AppCompatActivity {

    private static final String NO_INTERNET_MESSAGE =
            "No internet connection. Please check your connection and try again.";
    private static final String GENERIC_ERROR_MESSAGE =
            "Something went wrong. Please try again.";

    private TextView tvSelectedServices, tvSelectedDate, tvSelectedTime, tvError;
    private TextView tvServiceTotal, tvServicePriceNote;
    private View llServiceSummary;
    private View cardPaymentOptions;
    private TextView tvPaymentNote;
    private RadioButton rbDeposit, rbFull, rbCash;
    private EditText etNotes;

    // The services the salon offers, with their price and duration.
    private final List<ServiceDetail> availableServices = new ArrayList<>();
    // The services the client has ticked so far.
    private final List<ServiceDetail> selectedServices = new ArrayList<>();
    // The saved services of the booking being edited, by name. Old bookings
    // have no saved copy, so this map can be empty.
    private final Map<String, ServiceDetail> editingServiceDetails = new HashMap<>();

    private final Calendar selectedDateTime = Calendar.getInstance();
    private boolean dateChosen = false;
    private boolean timeChosen = false;

    /** The salon's own opening hours; defaults to the classic 09:00–18:00 grid until loaded. */
    private SalonHours salonHours = SalonHours.defaults();

    /** Non-null when editing/rescheduling an existing booking rather than creating one. */
    private String editingBookingId;
    private List<String> editingServiceNames;
    private long editingDateTimeMillis;

    private PaymentDetails editingPaymentDetails;
    private boolean editingBookingLoadSucceeded = false;
    private boolean pendingEditPayment = false;
    private PaymentDetails pendingCardPayment;
    private Salon pendingCardSalon;
    private String pendingCardClientId;
    private AlertDialog savingDialog;


    private final ActivityResultLauncher<Intent> cardPaymentLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                PaymentDetails payment = pendingCardPayment;
                Salon salon = pendingCardSalon;
                String clientId = pendingCardClientId;
                pendingCardPayment = null;
                pendingCardSalon = null;
                pendingCardClientId = null;

                if (result.getResultCode() == RESULT_OK
                        && payment != null && salon != null && clientId != null) {
                    // Add payment timestamp for successful card payments
                    PaymentDetails paymentWithTimestamp = new PaymentDetails(
                            payment.getType(),
                            payment.getStatus(),
                            payment.getAmountPaid(),
                            payment.getAmountOutstanding(),
                            com.google.firebase.Timestamp.now()
                    );
                    if (pendingEditPayment) {
                        completeEditWithPayment(salon, paymentWithTimestamp);
                    } else {
                        createBooking(salon, clientId, paymentWithTimestamp);
                    }
                } else {
                    enableConfirmButton();
                    pendingEditPayment = false;
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_book_appointment);

        String salonId = getIntent().getStringExtra("salonId");
        if (salonId == null || salonId.isEmpty()) {
            Snackbars.showThen(findViewById(android.R.id.content),
                    R.string.salon_not_found, this::finish);
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
        etNotes = findViewById(R.id.etNotes);
        tvServiceTotal = findViewById(R.id.tvServiceTotal);
        tvServicePriceNote = findViewById(R.id.tvServicePriceNote);
        llServiceSummary = findViewById(R.id.llServiceSummary);
        cardPaymentOptions = findViewById(R.id.cardPaymentOptions);
        tvPaymentNote = findViewById(R.id.tvPaymentNote);
        rbDeposit = findViewById(R.id.rbDeposit);
        rbFull = findViewById(R.id.rbFull);
        rbCash = findViewById(R.id.rbCash);

        LinearLayout rowService = findViewById(R.id.rowService);
        LinearLayout rowDate = findViewById(R.id.rowDate);
        LinearLayout rowTime = findViewById(R.id.rowTime);

        rowService.setOnClickListener(v -> showServicePicker());
        rowDate.setOnClickListener(v -> showDatePicker());
        rowTime.setOnClickListener(v -> showTimePicker());

        if (isEditing()) {
            // Read the booking first so services that the salon no longer
            // offers keep the price and duration saved on the booking.
            loadBookingServiceDetailsThenSalon(salonId);
        } else {
            loadSalon(salonId);
        }
    }

    private void loadBookingServiceDetailsThenSalon(String salonId) {
        new BookingManager().getBookingById(editingBookingId,
                new BookingManager.BookingDataCallback() {
                    @Override
                    public void onSuccess(Map<String, Object> booking) {
                        for (ServiceDetail detail : BookingManager.getServiceDetails(booking)) {
                            editingServiceDetails.put(detail.getName(), detail);
                        }
                        editingPaymentDetails = BookingManager.getPaymentDetails(booking);
                        editingBookingLoadSucceeded = true;

                        loadSalon(salonId);
                    }

                    @Override
                    public void onFailure(String errorMessage) {
                        // The form still works. Services without a saved copy
                        // simply show no price.
                        Log.w("BookAppointment", "getBookingById failed: " + errorMessage);
                        loadSalon(salonId);
                    }
                });
    }

    private void loadSalon(String salonId) {
        new SalonManager().getSalonById(salonId, new SalonManager.SalonDataCallback() {
            @Override
            public void onSuccess(Map<String, Object> salonData) {
                Salon salon = toSalon(salonId, salonData);
                if (salon == null) {
                    showMissingSalon();
                    return;
                }
                salonHours = SalonHours.fromDoc(salonData);
                bindSalon(salon, SalonManager.getServiceDetails(salonData));
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

    private void bindSalon(Salon salon, List<ServiceDetail> serviceDetails) {
        TextView tvSalonLabel = findViewById(R.id.tvSalonLabel);
        tvSalonLabel.setText(getString(isEditing()
                ? R.string.title_reschedule_booking
                : R.string.title_book_appointment) + " — " + salon.getName());

        availableServices.clear();
        availableServices.addAll(serviceDetails);
        // A booked service may no longer be listed by the salon; keep it
        // selectable so editing does not silently change what was booked.
        // For services that were booked, the saved copy wins, so a reschedule
        // keeps the price and duration the client booked.
        if (isEditing()) {
            for (String booked : editingServiceNames) {
                if (booked == null || booked.isEmpty()) continue;
                ServiceDetail saved = editingServiceDetails.get(booked);
                int index = indexOfService(availableServices, booked);
                if (saved != null) {
                    if (index >= 0) {
                        availableServices.set(index, saved);
                    } else {
                        availableServices.add(0, saved);
                    }
                } else if (index < 0) {
                    // No saved copy, so keep the name with no price.
                    availableServices.add(0, new ServiceDetail(booked, null,
                            ServiceDetail.DEFAULT_DURATION_MINUTES));
                }
            }
        }

        if (isEditing()) {
            prefillFromBooking();
            // A reschedule keeps the original note (the update path does not
            // touch it), so the field is hidden rather than shown and ignored.
            findViewById(R.id.tvNotesLabel).setVisibility(View.GONE);
            etNotes.setVisibility(View.GONE);
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

        final int n = availableServices.size();
        final CharSequence[] items = new CharSequence[n];
        final boolean[] checked = new boolean[n];

        for (int i = 0; i < n; i++) {
            ServiceDetail s = availableServices.get(i);
            String priceText = s.hasPrice()
                    ? getString(R.string.service_price_rand, formatPrice(s.getPrice()))
                    : getString(R.string.service_price_not_set);
            String durationText = getString(R.string.service_duration_minutes, s.getDurationMinutes());
            items[i] = getString(R.string.service_chip_text, s.getName(), priceText, durationText);
            checked[i] = isSelected(s.getName());
        }

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.select_services_title)
                .setMultiChoiceItems(items, checked, (dialog, which, isChecked) -> {
                    checked[which] = isChecked;
                });

        builder.setNegativeButton(R.string.cancel, (dialog, which) -> dialog.dismiss());

        builder.setPositiveButton(R.string.done, null);

        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                int count = 0;
                for (boolean b : checked) if (b) count++;
                if (count == 0) {
                    android.widget.Toast.makeText(BookAppointmentActivity.this,
                            R.string.please_choose_at_least_one_service,
                            android.widget.Toast.LENGTH_SHORT).show();
                    return;
                }
                selectedServices.clear();
                for (int i = 0; i < n; i++) {
                    if (checked[i]) selectedServices.add(availableServices.get(i));
                }
                updateServiceSummary();
                dialog.dismiss();
            });
        });

        dialog.show();
    }

    private void updateServiceSummary() {
        if (selectedServices.isEmpty()) {
            tvSelectedServices.setText(R.string.hint_choose_services);
            tvSelectedServices.setTextColor(getColor(R.color.grey_medium));
            llServiceSummary.setVisibility(View.GONE);
            if (tvServicePriceNote != null) {
                tvServicePriceNote.setVisibility(View.GONE);
                tvServicePriceNote.setText(R.string.service_missing_price_note);
            }
            updatePaymentOptions();
            return;
        }

        List<String> names = new ArrayList<>();
        double totalPrice = 0;
        boolean anyPrice = false;
        boolean missingPrice = false;
        int totalDuration = 0;
        List<String> unpricedNames = new ArrayList<>();

        for (ServiceDetail detail : selectedServices) {
            names.add(detail.getName());
            totalDuration += detail.getDurationMinutes();
            if (detail.hasPrice()) {
                // Only services with a price count. A missing price is never R0.
                totalPrice += detail.getPrice();
                anyPrice = true;
            } else {
                missingPrice = true;
                if (detail.getName() != null && !detail.getName().trim().isEmpty()) {
                    unpricedNames.add(detail.getName().trim());
                } else {
                    unpricedNames.add(detail.getName());
                }
            }
        }

        tvSelectedServices.setText(TextUtils.join(", ", names));
        tvSelectedServices.setTextColor(getColor(R.color.grey_dark));

        if (anyPrice) {
            tvServiceTotal.setText(getString(R.string.service_total,
                    getString(R.string.service_price_rand, formatPrice(totalPrice)),
                    totalDuration));
        } else {
            // No selected service has a price, so only the duration is shown.
            tvServiceTotal.setText(getString(R.string.service_total_duration, totalDuration));
        }

        if (tvServicePriceNote != null) {
            if (missingPrice && !unpricedNames.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                sb.append(getString(R.string.service_missing_price_note)).append("\n");
                for (String unpriced : unpricedNames) {
                    sb.append("- ").append(unpriced).append("\n");
                }
                // Remove trailing newline if present
                if (sb.length() > 0 && sb.charAt(sb.length() - 1) == '\n') {
                    sb.setLength(sb.length() - 1);
                }
                tvServicePriceNote.setText(sb.toString());
                tvServicePriceNote.setVisibility(View.VISIBLE);
            } else if (missingPrice) {
                tvServicePriceNote.setText(R.string.service_missing_price_note);
                tvServicePriceNote.setVisibility(View.VISIBLE);
            } else {
                tvServicePriceNote.setText(R.string.service_missing_price_note);
                tvServicePriceNote.setVisibility(View.GONE);
            }
        }
        llServiceSummary.setVisibility(View.VISIBLE);
        updatePaymentOptions();

        // When services change, the total duration changes, so any previously
        // picked time may no longer fit. Clear the time selection.
        timeChosen = false;
        tvSelectedTime.setText(R.string.hint_choose_time);
        tvSelectedTime.setTextColor(getColor(R.color.grey_medium));
    }

    private void updatePaymentOptions() {
        if (isEditing() || selectedServices.isEmpty()) {
            cardPaymentOptions.setVisibility(View.GONE);
            return;
        }

        Double totalPrice = Booking.sumPrice(selectedServices);
        boolean hasCompletePrice = totalPrice != null && allSelectedServicesPriced();
        boolean canPayByCard = hasCompletePrice && totalPrice > 0;

        if (canPayByCard) {
            PaymentDetails deposit = PaymentDetails.depositByCard(totalPrice);
            rbDeposit.setText(getString(R.string.payment_deposit_option,
                    formatPrice(deposit.getAmountPaid()),
                    formatPrice(deposit.getAmountOutstanding())));
            rbFull.setText(getString(R.string.payment_full_option,
                    formatPrice(totalPrice)));
            rbDeposit.setVisibility(View.VISIBLE);
            rbFull.setVisibility(View.VISIBLE);
            tvPaymentNote.setVisibility(View.GONE);
        } else {
            rbDeposit.setVisibility(View.GONE);
            rbFull.setVisibility(View.GONE);
            tvPaymentNote.setVisibility(View.VISIBLE);
        }

        rbCash.setText(hasCompletePrice
                ? getString(R.string.payment_cash_option, formatPrice(totalPrice))
                : getString(R.string.payment_cash_option_unknown));

        if (rbDeposit.getVisibility() != View.VISIBLE) {
            rbCash.setChecked(true);
        } else if (!rbDeposit.isChecked() && !rbFull.isChecked() && !rbCash.isChecked()) {
            rbDeposit.setChecked(true);
        }

        cardPaymentOptions.setVisibility(View.VISIBLE);
    }

    /** True when a service with this name is already ticked. */
    private boolean isSelected(String name) {
        for (ServiceDetail detail : selectedServices) {
            if (detail.getName().equals(name)) return true;
        }
        return false;
    }

    private ServiceDetail findServiceByName(List<ServiceDetail> list, String name) {
        int index = indexOfService(list, name);
        return index < 0 ? null : list.get(index);
    }

    // Returns the position of the service with this name, or -1.
    private int indexOfService(List<ServiceDetail> list, String name) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).getName().equals(name)) return i;
        }
        return -1;
    }

    /** Builds one line, for example "Haircut - R150 - 45min". */
    private String serviceLine(ServiceDetail service) {
        String price = service.hasPrice()
                ? getString(R.string.service_price_rand, formatPrice(service.getPrice()))
                : getString(R.string.service_price_not_set);
        String duration = getString(R.string.service_duration_minutes,
                service.getDurationMinutes());
        return getString(R.string.service_chip_text, service.getName(), price, duration);
    }

    // Whole numbers show no cents. Cents always show two digits, like 150.50.
    private String formatPrice(Double price) {
        if (price == null) {
            return "";
        }
        if (price == Math.rint(price)) {
            return String.valueOf((long) (double) price);
        }
        return String.format(Locale.US, "%.2f", price);
    }

    /** Restores the existing booking's services, date and time into the form. */
    private void prefillFromBooking() {
        selectedServices.clear();
        for (String booked : editingServiceNames) {
            if (booked == null || booked.isEmpty()) continue;
            ServiceDetail detail = findServiceByName(availableServices, booked);
            if (detail != null) selectedServices.add(detail);
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
        Snackbars.showThen(findViewById(android.R.id.content),
                R.string.salon_not_found, this::finish);
    }

    private void showDatePicker() {
        Calendar today = Calendar.getInstance();

        DatePickerDialog dialog = new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
            // Native date pickers cannot grey out days, so reject a closed day here
            // and leave any previously chosen date untouched.
            Calendar picked = Calendar.getInstance();
            picked.set(year, month, dayOfMonth);
            if (!salonHours.isOpenOn(picked.get(Calendar.DAY_OF_WEEK))) {
                new MaterialAlertDialogBuilder(this)
                        .setTitle(R.string.closed_day_title)
                        .setMessage(getString(R.string.closed_day_message,
                                SalonHours.dayName(picked.get(Calendar.DAY_OF_WEEK))))
                        .setPositiveButton(R.string.ok, null)
                        .show();
                return;
            }

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

        // Block past dates but keep today selectable: pass midnight of today
        // rather than "now", which some DatePicker versions read as a date
        // that has not started yet.
        Calendar minDate = (Calendar) today.clone();
        minDate.set(Calendar.HOUR_OF_DAY, 0);
        minDate.set(Calendar.MINUTE, 0);
        minDate.set(Calendar.SECOND, 0);
        minDate.set(Calendar.MILLISECOND, 0);
        dialog.getDatePicker().setMinDate(minDate.getTimeInMillis());
        dialog.show();
    }

    private void showTimePicker() {
        if (!dateChosen) {
            Snackbars.show(findViewById(android.R.id.content), R.string.please_select_date_first);
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

    /** Filters slots that overlap an existing booking or run past closing. */
    private void showAvailableTimes(List<Map<String, Object>> bookings) {
        List<int[]> bookedRanges = new ArrayList<>();
        if (bookings != null) {
            for (Map<String, Object> booking : bookings) {
                // Ignore the booking being edited so its own slot stays selectable.
                if (isEditing()) {
                    Object id = booking.get("bookingID");
                    if (id != null && editingBookingId.equals(id.toString())) continue;
                }
                Date when = toDate(booking.get("dateTime"));
                if (when == null) continue;
                int start = BookingManager.minutesOfDay(when);
                int duration = BookingManager.getEffectiveDurationMinutes(booking);
                bookedRanges.add(new int[]{start, start + duration});
            }
        }

        // Drop anything starting before now + the notice period. On today this
        // removes both already-past slots and slots too close to book; on later
        // dates the cutoff has no effect.
        Calendar cutoff = Calendar.getInstance();
        cutoff.add(Calendar.MINUTE, BookingManager.MIN_NOTICE_MINUTES);

        // The new booking must fit before closing, not just its first 30 minutes.
        int newDuration = Booking.sumDuration(selectedServices);
        if (newDuration <= 0) newDuration = BookingManager.SLOT_MINUTES;
        int dayOfWeek = selectedDateTime.get(Calendar.DAY_OF_WEEK);
        int closeMinutes = salonHours.closeMinutes(dayOfWeek);

        boolean removedByNotice = false;
        List<String> availableSlots = new ArrayList<>();
        // The salon's grid for the selected weekday; empty when it is closed that day.
        List<String> daySlots = salonHours.slotsFor(dayOfWeek);
        for (String slot : daySlots) {
            int slotStart = BookingManager.minutesOfDay(slot);
            int slotEnd = slotStart + newDuration;
            if (slotEnd > closeMinutes) continue;
            if (BookingManager.overlapsAny(slotStart, slotEnd, bookedRanges)) continue;
            if (slotBefore(slot, cutoff)) {
                removedByNotice = true;
                continue;
            }
            availableSlots.add(slot);
        }

        if (availableSlots.isEmpty()) {
            if (removedByNotice) {
                new AlertDialog.Builder(this)
                        .setTitle(R.string.no_times_notice_title)
                        .setMessage(getString(R.string.no_times_notice_message,
                                BookingManager.MIN_NOTICE_MINUTES))
                        .setPositiveButton(R.string.ok, null)
                        .show();
            } else {
                new AlertDialog.Builder(this)
                        .setTitle(R.string.fully_booked_title)
                        .setMessage(R.string.fully_booked_message)
                        .setPositiveButton(R.string.ok, null)
                        .show();
            }
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

    /** Reports whether the given "HH:mm" slot on the selected date starts before the cutoff. */
    private boolean slotBefore(String slot24h, Calendar cutoff) {
        String[] parts = slot24h.split(":");
        Calendar slotTime = (Calendar) selectedDateTime.clone();
        slotTime.set(Calendar.HOUR_OF_DAY, Integer.parseInt(parts[0]));
        slotTime.set(Calendar.MINUTE, Integer.parseInt(parts[1]));
        slotTime.set(Calendar.SECOND, 0);
        slotTime.set(Calendar.MILLISECOND, 0);
        return slotTime.before(cutoff);
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

        // The picked slot may have become too close to now while this screen
        // sat open — re-check the notice period before doing any write.
        Calendar earliest = Calendar.getInstance();
        earliest.add(Calendar.MINUTE, BookingManager.MIN_NOTICE_MINUTES);
        if (selectedDateTime.before(earliest)) {
            showError(getString(R.string.error_notice_period, BookingManager.MIN_NOTICE_MINUTES));
            return;
        }

        // The salon's hours may have changed since this screen loaded, or a
        // pre-filled reschedule slot may now fall outside them.
        int dayOfWeek = selectedDateTime.get(Calendar.DAY_OF_WEEK);
        int startMinutes = selectedDateTime.get(Calendar.HOUR_OF_DAY) * 60
                + selectedDateTime.get(Calendar.MINUTE);
        int newDuration = Booking.sumDuration(selectedServices);
        if (newDuration <= 0) newDuration = BookingManager.SLOT_MINUTES;
        if (!salonHours.isOpenOn(dayOfWeek)
                || startMinutes < salonHours.openMinutes(dayOfWeek)
                || startMinutes + newDuration > salonHours.closeMinutes(dayOfWeek)) {
            showError(getString(R.string.error_outside_hours));
            return;
        }

        String clientId = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid()
                : null;
        if (clientId == null) {
            showError(getString(R.string.salon_not_found));
            return;
        }

        if (!hasInternetConnection()) {
            showError(NO_INTERNET_MESSAGE);
            return;
        }

        tvError.setVisibility(android.view.View.GONE);
        findViewById(R.id.btnConfirmBooking).setEnabled(false);

        if (isEditing()) {
            updateBooking(salon);
            return;
        }

        PaymentDetails payment = selectedPayment();
        if (PaymentDetails.TYPE_CASH.equals(payment.getType())) {
            createBooking(salon, clientId, payment);
        } else {
            startCardPayment(salon, clientId, payment);
        }
    }

    private PaymentDetails selectedPayment() {
        Double totalPrice = Booking.sumPrice(selectedServices);
        boolean hasCompletePrice = totalPrice != null && allSelectedServicesPriced();
        if (hasCompletePrice && totalPrice > 0) {
            if (rbDeposit.isChecked()) return PaymentDetails.depositByCard(totalPrice);
            if (rbFull.isChecked()) return PaymentDetails.fullByCard(totalPrice);
        }
        return PaymentDetails.cashAtSalon(hasCompletePrice ? totalPrice : null);
    }

    private void startCardPayment(Salon salon, String clientId, PaymentDetails payment) {
        pendingCardPayment = payment;
        pendingCardSalon = salon;
        pendingCardClientId = clientId;

        Intent intent = new Intent(this, MockCardPaymentActivity.class);
        intent.putExtra(MockCardPaymentActivity.EXTRA_AMOUNT,
                payment.getAmountPaid() == null ? 0.0 : payment.getAmountPaid());
        intent.putExtra(MockCardPaymentActivity.EXTRA_PAYMENT_TYPE, payment.getType());
        cardPaymentLauncher.launch(intent);
    }

    private void createBooking(Salon salon, String clientId, PaymentDetails payment) {
        if (!hasInternetConnection()) {
            enableConfirmButton();
            showError(NO_INTERNET_MESSAGE);
            return;
        }

        showSavingDialog();
        new BookingManager().createBookingWithDetails(
                clientId,
                salon.getSalonId(),
                salon.getName(),
                new ArrayList<>(selectedServices),
                selectedDateTime.getTime(),
                etNotes.getText().toString(),
                payment,
                new BookingManager.BookingCreatedCallback() {
                    @Override
                    public void onSuccess(String bookingId) {
                        dismissSavingDialog();

                        // Tell the user, then line up the 1 day / 1 hour reminders.
                        Notifications.requestPermission(BookAppointmentActivity.this);
                        Notifications.show(BookAppointmentActivity.this, bookingId.hashCode(),
                                Notifications.CHANNEL_UPDATES,
                                getString(R.string.booking_confirmation_title),
                                getString(R.string.booking_confirmation_body, salon.getName()),
                                bookingId);
                        ReminderScheduler.schedule(BookAppointmentActivity.this, bookingId,
                                salon.getName(), selectedDateTime.getTime());

                        openConfirmation(bookingId, salon, payment);
                    }

                    @Override
                    public void onFailure(String errorMessage) {
                        Log.w("BookAppointment", "createBooking failed: " + errorMessage);
                        dismissSavingDialog();
                        enableConfirmButton();
                        showError(bookingFailureMessage(errorMessage));
                    }
                });
    }

    private boolean allSelectedServicesPriced() {
        if (selectedServices.isEmpty()) return false;
        for (ServiceDetail detail : selectedServices) {
            if (!detail.hasPrice()) return false;
        }
        return true;
    }

    private void enableConfirmButton() {
        findViewById(R.id.btnConfirmBooking).setEnabled(true);
    }

    private void showSavingDialog() {
        if (savingDialog != null && savingDialog.isShowing()) return;

        View view = getLayoutInflater().inflate(R.layout.dialog_saving_booking, null);
        savingDialog = new MaterialAlertDialogBuilder(this)
                .setView(view)
                .setCancelable(false)
                .create();

        if (savingDialog.getWindow() != null) {
            savingDialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        savingDialog.show();
    }

    private void dismissSavingDialog() {
        if (savingDialog != null && savingDialog.isShowing()) {
            savingDialog.dismiss();
        }
    }

    @Override
    protected void onDestroy() {
        dismissSavingDialog();
        super.onDestroy();
    }

    /**
     * Saves an edit or reschedule. The booking keeps its id but returns to
     * pending, so the salon re-confirms the new details.
     */
    private void updateBooking(Salon salon) {
        if (!editingBookingLoadSucceeded) {
            showError(getString(R.string.error_edit_payment_load_failed));
            enableConfirmButton();
            return;
        }

        Double newTotal = Booking.sumPrice(selectedServices);
        boolean hasCompletePrice = newTotal != null && allSelectedServicesPriced();

        if (editingPaymentDetails != null && editingPaymentDetails.isCardPayment()) {
            if (!hasCompletePrice) {
                showError(getString(R.string.error_edit_card_needs_prices));
                enableConfirmButton();
                return;
            }

            double additionalAmount = editingPaymentDetails.additionalPaymentForNewTotal(newTotal);
            if (additionalAmount > 0) {
                PaymentDetails updatedPayment = editingPaymentDetails.recalculateForNewTotal(newTotal);
                String message;
                if (PaymentDetails.TYPE_DEPOSIT.equals(editingPaymentDetails.getType())) {
                    double outstanding = updatedPayment.getAmountOutstanding() == null ? 0.0 : updatedPayment.getAmountOutstanding();
                    message = getString(R.string.edit_deposit_payment_required_message,
                            formatPrice(newTotal), formatPrice(additionalAmount), formatPrice(outstanding));
                } else {
                    message = getString(R.string.edit_full_payment_required_message,
                            formatPrice(newTotal), formatPrice(additionalAmount));
                }

                new AlertDialog.Builder(this)
                        .setTitle(R.string.edit_payment_required_title)
                        .setMessage(message)
                        .setPositiveButton(R.string.ok, (dialog, which) -> {
                            pendingCardPayment = updatedPayment;
                            pendingCardSalon = salon;
                            pendingCardClientId = FirebaseAuth.getInstance().getCurrentUser() != null
                                    ? FirebaseAuth.getInstance().getCurrentUser().getUid() : null;
                            pendingEditPayment = true;

                            Intent intent = new Intent(BookAppointmentActivity.this, MockCardPaymentActivity.class);
                            intent.putExtra(MockCardPaymentActivity.EXTRA_AMOUNT, additionalAmount);
                            intent.putExtra(MockCardPaymentActivity.EXTRA_PAYMENT_TYPE, updatedPayment.getType());
                            intent.putExtra(MockCardPaymentActivity.EXTRA_IS_ADDITIONAL_PAYMENT, true);
                            cardPaymentLauncher.launch(intent);
                        })
                        .setNegativeButton(R.string.cancel, (dialog, which) -> enableConfirmButton())
                        .show();
                return;
            }

            PaymentDetails updatedPayment = editingPaymentDetails.recalculateForNewTotal(newTotal);
            completeEditWithPayment(salon, updatedPayment);
        } else {
            PaymentDetails updatedPayment = null;
            if (editingPaymentDetails != null && PaymentDetails.TYPE_CASH.equals(editingPaymentDetails.getType())) {
                updatedPayment = editingPaymentDetails.recalculateForNewTotal(newTotal == null ? 0.0 : newTotal);
            }
            completeEditWithPayment(salon, updatedPayment);
        }
    }

    private void completeEditWithPayment(Salon salon, PaymentDetails payment) {
        if (!hasInternetConnection()) {
            enableConfirmButton();
            pendingEditPayment = false;
            showError(NO_INTERNET_MESSAGE);
            return;
        }

        new BookingManager().updateBookingWithDetails(
                editingBookingId,
                new ArrayList<>(selectedServices),
                selectedDateTime.getTime(),
                payment,
                new BookingManager.BookingCallback() {
                    @Override
                    public void onSuccess() {
                        ReminderScheduler.cancel(BookAppointmentActivity.this, editingBookingId);
                        ReminderScheduler.schedule(BookAppointmentActivity.this, editingBookingId,
                                salon.getName(), selectedDateTime.getTime());

                        Snackbars.showThen(findViewById(android.R.id.content),
                                R.string.booking_updated, () -> {
                                    setResult(RESULT_OK);
                                    finish();
                                });
                    }

                    @Override
                    public void onFailure(String errorMessage) {
                        Log.w("BookAppointment", "updateBookingWithDetails failed: " + errorMessage);
                        enableConfirmButton();
                        pendingEditPayment = false;
                        showError(bookingFailureMessage(errorMessage));
                    }
                });
    }

    private void openConfirmation(String bookingId, Salon salon, PaymentDetails payment) {
        Double totalPrice = Booking.sumPrice(selectedServices);
        int totalDuration = Booking.sumDuration(selectedServices);

        Intent intent = new Intent(this, BookingConfirmationActivity.class);
        intent.putExtra(BookingConfirmationActivity.EXTRA_BOOKING_ID, bookingId);
        intent.putExtra(BookingConfirmationActivity.EXTRA_SALON_NAME, salon.getName());
        intent.putExtra(BookingConfirmationActivity.EXTRA_SERVICE_LABEL,
                TextUtils.join(", ", ServiceDetail.toNames(selectedServices)));
        intent.putExtra(BookingConfirmationActivity.EXTRA_DATE_TIME,
                selectedDateTime.getTimeInMillis());
        intent.putExtra(BookingConfirmationActivity.EXTRA_TOTAL_PRICE,
                totalPrice == null ? Double.NaN : totalPrice);
        intent.putExtra(BookingConfirmationActivity.EXTRA_TOTAL_DURATION, totalDuration);
        intent.putExtra(BookingConfirmationActivity.EXTRA_PAYMENT_TYPE, payment.getType());
        intent.putExtra(BookingConfirmationActivity.EXTRA_AMOUNT_PAID,
                payment.getAmountPaid() == null ? Double.NaN : payment.getAmountPaid());
        intent.putExtra(BookingConfirmationActivity.EXTRA_AMOUNT_OUTSTANDING,
                payment.getAmountOutstanding() == null
                        ? Double.NaN : payment.getAmountOutstanding());
        startActivity(intent);
        finish();
    }

    private boolean hasInternetConnection() {
        ConnectivityManager connectivityManager =
                (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager == null) return false;

        NetworkCapabilities capabilities =
                connectivityManager.getNetworkCapabilities(connectivityManager.getActiveNetwork());
        return capabilities != null
                && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
    }

    private String bookingFailureMessage(String errorMessage) {
        if (isNetworkError(errorMessage)) return NO_INTERNET_MESSAGE;
        if (errorMessage != null && (errorMessage.contains("just booked")
                || errorMessage.contains("minutes in advance"))) {
            return errorMessage;
        }
        return GENERIC_ERROR_MESSAGE;
    }

    private boolean isNetworkError(String errorMessage) {
        if (errorMessage == null) return false;

        String message = errorMessage.toLowerCase(Locale.US);
        return message.contains("network")
                || message.contains("offline")
                || message.contains("unavailable")
                || message.contains("unable to resolve")
                || message.contains("failed to connect")
                || message.contains("connection")
                || message.contains("timeout")
                || message.contains("timed out");
    }

    private void showError(String message) {
        tvError.setText(message);
        tvError.setVisibility(android.view.View.VISIBLE);
    }
}