package com.hairgo.app.activities;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.imageview.ShapeableImageView;
import com.google.firebase.Timestamp;
import com.hairgo.app.R;
import com.hairgo.app.firebase.BookingManager;
import com.hairgo.app.firebase.SalonManager;
import com.hairgo.app.fragments.LeaveReviewBottomSheet;
import com.hairgo.app.fragments.ReportProblemBottomSheet;
import com.hairgo.app.models.Booking;
import com.hairgo.app.models.PaymentDetails;
import com.hairgo.app.models.ServiceDetail;
import com.hairgo.app.utils.BookingSections;
import com.hairgo.app.utils.MapsHelper;
import com.hairgo.app.utils.ReminderScheduler;
import com.hairgo.app.utils.SalonPhotos;
import com.hairgo.app.utils.Snackbars;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class BookingDetailsActivity extends AppCompatActivity {

    private TextView tvStatusBadge;
    private TextView tvExpiredNote;
    private TextView tvSalonLocation;
    private androidx.appcompat.widget.AppCompatButton btnMessageSalon;
    private androidx.appcompat.widget.AppCompatButton btnCancelBooking;
    private androidx.appcompat.widget.AppCompatButton btnEditBooking;
    private androidx.appcompat.widget.AppCompatButton btnLeaveReview;
    private androidx.appcompat.widget.AppCompatButton btnReportSalon;
    private ProgressBar progress;
    private View contentGroup;
    private BookingManager bookingManager;
    private String currentStatus;
    private String bookingId;
    private String currentSalonId;
    private ShapeableImageView ivSalonPhoto;
    private TextView tvPaymentBadge;
    private TextView tvTotalLabel;

    private final ActivityResultLauncher<Intent> editLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() == RESULT_OK && bookingManager != null) {
                            loadBooking();
                        }
                    });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_booking_details);

        bookingId = getIntent().getStringExtra("bookingId");

        ImageButton backBtn = findViewById(R.id.header).findViewById(R.id.btnBack);
        backBtn.setOnClickListener(v -> onBackPressed());

        TextView headerTitle = findViewById(R.id.header).findViewById(R.id.tvHeaderTitle);
        headerTitle.setText(R.string.title_booking_details);

        progress = findViewById(R.id.progress);
        contentGroup = findViewById(R.id.contentGroup);

        if (bookingId == null || bookingId.isEmpty()) {
            showMissingBooking();
            return;
        }

        bookingManager = new BookingManager();
        loadBooking();
    }

    private void loadBooking() {
        progress.setVisibility(View.VISIBLE);
        contentGroup.setVisibility(View.INVISIBLE);

        bookingManager.getBookingById(bookingId, new BookingManager.BookingDataCallback() {
            @Override
            public void onSuccess(Map<String, Object> rawBooking) {
                Booking booking = toBooking(rawBooking);
                if (booking == null) {
                    showMissingBooking();
                    return;
                }
                bindBooking(booking);
            }

            @Override
            public void onFailure(String errorMessage) {
                showMissingBooking();
            }
        });
    }

    /**
     * Converts the raw Firestore map into our Booking model. Every field is
     * null-checked because a booking document may be missing any of them.
     */
    private Booking toBooking(Map<String, Object> raw) {
        if (raw == null) return null;

        Object status = raw.get("status");
        if (status == null) return null;

        Object salonName = raw.get("salonName");
        Object salonId = raw.get("salonID");

        List<String> serviceNames = new ArrayList<>();
        Object namesValue = raw.get("serviceNames");
        if (namesValue instanceof List) {
            for (Object item : (List<?>) namesValue) {
                if (item != null && !item.toString().isEmpty()) serviceNames.add(item.toString());
            }
        }
        if (serviceNames.isEmpty()) {
            // Legacy bookings stored a single serviceName string.
            Object single = raw.get("serviceName");
            if (single != null && !single.toString().isEmpty()) serviceNames.add(single.toString());
        }

        Date dateTime = null;
        Object dateValue = raw.get("dateTime");
        if (dateValue instanceof Timestamp) {
            dateTime = ((Timestamp) dateValue).toDate();
        }

        Booking booking = new Booking(
                bookingId,
                salonId == null ? null : salonId.toString(),
                salonName == null ? "" : salonName.toString(),
                serviceNames,
                dateTime,
                status.toString(),
                0);

        Object notes = raw.get("notes");
        booking.setNotes(notes == null ? "" : notes.toString());
        booking.setPaymentDetails(BookingManager.getPaymentDetails(raw));

        // New bookings keep a copy of the chosen services. Old bookings have
        // none, so the saved totals are only used when they are there.
        booking.setServiceDetails(BookingManager.getServiceDetails(raw));
        Double savedPrice = BookingManager.getTotalPrice(raw);
        if (savedPrice != null) {
            booking.setTotalPrice(savedPrice);
        }
        int savedDuration = BookingManager.getTotalDuration(raw);
        if (savedDuration > 0) {
            booking.setTotalDurationMinutes(savedDuration);
        }
        return booking;
    }

    private void bindBooking(Booking booking) {
        currentStatus = BookingSections.effectiveStatus(booking);

        progress.setVisibility(View.GONE);
        contentGroup.setVisibility(View.VISIBLE);

        TextView tvSalonName = findViewById(R.id.tvSalonName);
        TextView tvServiceName = findViewById(R.id.tvServiceName);
        TextView tvBookingTotal = findViewById(R.id.tvBookingTotal);
        TextView tvBookingNote = findViewById(R.id.tvBookingNote);
        TextView tvDateTime = findViewById(R.id.tvDateTime);
        tvStatusBadge = findViewById(R.id.tvStatusBadge);
        tvExpiredNote = findViewById(R.id.tvExpiredNote);
        tvSalonLocation = findViewById(R.id.tvSalonLocation);
        btnCancelBooking = findViewById(R.id.btnCancelBooking);
        btnMessageSalon = findViewById(R.id.btnMessageSalon);
        btnEditBooking = findViewById(R.id.btnEditBooking);
        btnLeaveReview = findViewById(R.id.btnLeaveReview);
        btnReportSalon = findViewById(R.id.btnReportSalon);
        ivSalonPhoto = findViewById(R.id.ivSalonPhoto);
        tvPaymentBadge = findViewById(R.id.tvPaymentBadge);
        tvTotalLabel = findViewById(R.id.tvTotalLabel);
        currentSalonId = booking.getSalonId();

        btnLeaveReview.setOnClickListener(v -> showLeaveReview());
        btnEditBooking.setOnClickListener(v -> onEditClicked(booking));
        btnReportSalon.setOnClickListener(v -> showReportSalon(booking));
        btnMessageSalon.setOnClickListener(v -> openChat());

        tvSalonName.setText(booking.getSalonName());

        // Load salon photo
        if (currentSalonId != null && !currentSalonId.isEmpty()) {
            SalonPhotos.fetchPhoto(currentSalonId, bitmap -> {
                if (bitmap != null && !isFinishing() && !isDestroyed()) {
                    ivSalonPhoto.setImageBitmap(bitmap);
                    ivSalonPhoto.setBackground(null);
                    ivSalonPhoto.setImageTintList(null);
                }
            });
        }

        bindServices(booking, tvServiceName, tvBookingTotal, tvBookingNote);
        bindPayment(booking);

        // Location is loaded separately because it lives on the salon, not the
        // booking. The row hides itself if the salon cannot be resolved.
        loadSalonLocation();

        if (booking.getDateTime() != null) {
            SimpleDateFormat format = new SimpleDateFormat(
                    getString(R.string.booking_date_format), Locale.getDefault());
            tvDateTime.setText(format.format(booking.getDateTime()));
        } else {
            tvDateTime.setText("");
        }

        bindNotes(booking.getNotes());

        updateStatusBadge(currentStatus);
        updateCancelButtonVisibility(currentStatus);
        updateExpiredNote(currentStatus);
        updateEditButton(currentStatus);

        btnCancelBooking.setOnClickListener(v -> showCancelConfirmation());
    }

    /**
     * Loads the salon's location and turns the row into a tap-to-open-Google-Maps
     * link. The row stays hidden when the salon cannot be resolved (deleted or
     * offline), so the screen never shows an empty location line.
     */
    private void loadSalonLocation() {
        if (currentSalonId == null || currentSalonId.isEmpty()) {
            tvSalonLocation.setVisibility(View.GONE);
            return;
        }

        new SalonManager().getSalonById(currentSalonId, new SalonManager.SalonDataCallback() {
            @Override
            public void onSuccess(Map<String, Object> salonData) {
                bindLocation(salonData == null ? null : asString(salonData.get("location")));
            }

            @Override
            public void onFailure(String errorMessage) {
                tvSalonLocation.setVisibility(View.GONE);
            }
        });
    }

    private void bindLocation(String location) {
        if (location == null || location.trim().isEmpty()) {
            tvSalonLocation.setVisibility(View.GONE);
            return;
        }

        tvSalonLocation.setText(location);
        tvSalonLocation.setOnClickListener(v -> MapsHelper.openLocation(this, location));
        tvSalonLocation.setVisibility(View.VISIBLE);
    }

    /** Shows the client's own note on the booking, hiding the row when empty. */
    private void bindNotes(String notes) {
        TextView tvNotesLabel = findViewById(R.id.tvNotesLabel);
        TextView tvNotes = findViewById(R.id.tvNotes);
        com.google.android.material.card.MaterialCardView cardNotes = findViewById(R.id.cardNotes);

        if (notes == null || notes.trim().isEmpty()) {
            tvNotesLabel.setVisibility(View.GONE);
            tvNotes.setVisibility(View.GONE);
            if (cardNotes != null) cardNotes.setVisibility(View.GONE);
            return;
        }

        tvNotes.setText(notes.trim());
        tvNotesLabel.setVisibility(View.VISIBLE);
        tvNotes.setVisibility(View.VISIBLE);
        if (cardNotes != null) cardNotes.setVisibility(View.VISIBLE);
    }

    /**
     * Shows one line per saved service with its price and duration, then the
     * booking total. Old bookings have no saved services, so they keep the
     * plain list of names and hide the total.
     */
    private void bindServices(Booking booking, TextView tvServiceName,
                              TextView tvBookingTotal, TextView tvBookingNote) {
        if (!booking.hasServiceDetails()) {
            tvServiceName.setText(booking.getServiceLabel());
            if (tvBookingNote != null) tvBookingNote.setVisibility(View.GONE);
            return;
        }

        List<String> lines = new ArrayList<>();
        for (ServiceDetail detail : booking.getServiceDetails()) {
            lines.add(getString(R.string.service_chip_text,
                    detail.getName(),
                    formatServicePrice(detail),
                    getString(R.string.service_duration_minutes, detail.getDurationMinutes())));
        }
        tvServiceName.setText(TextUtils.join("\n", lines));

        Double totalPrice = booking.getTotalPrice();
        int totalDuration = booking.getTotalDurationMinutes();
        if (totalPrice == null) {
            if (totalDuration > 0) {
                if (tvTotalLabel != null) tvTotalLabel.setText(R.string.total);
                tvBookingTotal.setText(getString(R.string.booking_total_no_price, totalDuration));
                tvBookingTotal.setVisibility(View.VISIBLE);
            } else {
                tvBookingTotal.setVisibility(View.GONE);
            }
            if (tvBookingNote != null) tvBookingNote.setVisibility(View.GONE);
            return;
        }

        if (tvTotalLabel != null) tvTotalLabel.setText(R.string.total);
        tvBookingTotal.setText(getString(R.string.service_total,
                getString(R.string.service_price_rand, formatPriceValue(totalPrice)),
                totalDuration));
        tvBookingTotal.setVisibility(View.VISIBLE);
        if (tvBookingNote != null) tvBookingNote.setVisibility(booking.hasUnpricedServices() ? View.VISIBLE : View.GONE);
    }

    private void bindPayment(Booking booking) {
        TextView label = findViewById(R.id.tvPaymentLabel);
        TextView value = findViewById(R.id.tvPayment);
        String summary = paymentSummary(booking.getPaymentDetails());
        String badgeText = paymentBadgeText(booking.getPaymentDetails());

        if (summary == null) {
            label.setVisibility(View.GONE);
            value.setVisibility(View.GONE);
            if (tvPaymentBadge != null) tvPaymentBadge.setVisibility(View.GONE);
            return;
        }

        value.setText(summary);
        label.setVisibility(View.VISIBLE);
        value.setVisibility(View.VISIBLE);

        if (tvPaymentBadge != null) {
            if (badgeText != null && !badgeText.isEmpty()) {
                tvPaymentBadge.setText(badgeText);
                tvPaymentBadge.setVisibility(View.VISIBLE);
            } else {
                tvPaymentBadge.setVisibility(View.GONE);
            }
        }
    }

    private String paymentBadgeText(PaymentDetails payment) {
        if (payment == null) return null;

        if (PaymentDetails.TYPE_CASH.equals(payment.getType())) {
            return getString(R.string.payment_badge_cash);
        }

        if (PaymentDetails.TYPE_DEPOSIT.equals(payment.getType())) {
            return getString(R.string.payment_badge_deposit);
        }

        if (PaymentDetails.TYPE_FULL.equals(payment.getType())) {
            return getString(R.string.payment_badge_full);
        }

        return null;
    }

    private String paymentSummary(PaymentDetails payment) {
        if (payment == null) return null;

        if (PaymentDetails.TYPE_CASH.equals(payment.getType())) {
            if (payment.getAmountOutstanding() == null) {
                return getString(R.string.payment_cash_summary_unknown);
            }
            return getString(R.string.payment_cash_summary,
                    formatPriceValue(payment.getAmountOutstanding()));
        }

        if (PaymentDetails.TYPE_DEPOSIT.equals(payment.getType())) {
            if (payment.getAmountPaid() == null || payment.getAmountOutstanding() == null) {
                return null;
            }
            return getString(R.string.payment_deposit_summary,
                    formatPriceValue(payment.getAmountPaid()),
                    formatPriceValue(payment.getAmountOutstanding()));
        }

        if (PaymentDetails.TYPE_FULL.equals(payment.getType())) {
            if (payment.getAmountPaid() == null) return null;
            return getString(R.string.payment_full_summary,
                    formatPriceValue(payment.getAmountPaid()));
        }

        return null;
    }

    /** "R150.50" when priced, otherwise the "Price not set" text. */
    private String formatServicePrice(ServiceDetail detail) {
        if (!detail.hasPrice()) {
            return getString(R.string.service_price_not_set);
        }
        return getString(R.string.service_price_rand, formatPriceValue(detail.getPrice()));
    }

    /** Whole rands show no decimals; cents always show two. */
    private String formatPriceValue(double price) {
        if (price == Math.rint(price)) {
            return String.valueOf((long) price);
        }
        return String.format(Locale.US, "%.2f", price);
    }

    private String asString(Object value) {
        return value == null ? null : value.toString();
    }

    /**
     * Only a pending or confirmed booking can be changed. Pending is a plain
     * edit; confirmed is a reschedule that returns the booking to pending.
     */
    private void updateEditButton(String status) {
        boolean canEdit = "pending".equals(status) || "confirmed".equals(status);
        btnEditBooking.setVisibility(canEdit ? View.VISIBLE : View.GONE);
        btnEditBooking.setText("confirmed".equals(status)
                ? R.string.btn_reschedule
                : R.string.btn_edit_booking);
    }

    private void onEditClicked(Booking booking) {
        if ("confirmed".equals(currentStatus)) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.reschedule_confirm_title)
                    .setMessage(R.string.reschedule_confirm_message)
                    .setPositiveButton(R.string.btn_reschedule, (dialog, which) -> launchEdit(booking))
                    .setNegativeButton(R.string.btn_keep_booking, null)
                    .show();
        } else {
            launchEdit(booking);
        }
    }

    private void launchEdit(Booking booking) {
        Intent intent = new Intent(this, BookAppointmentActivity.class);
        intent.putExtra("salonId", booking.getSalonId());
        intent.putExtra("bookingId", booking.getBookingId());
        intent.putExtra("serviceNames", new ArrayList<>(booking.getServiceNames()));
        intent.putExtra("dateTimeMillis",
                booking.getDateTime() == null ? 0L : booking.getDateTime().getTime());
        editLauncher.launch(intent);
    }

    private void updateStatusBadge(String status) {
        tvStatusBadge.setText(capitalize(status));

        int pillRes;
        int textColorRes;
        switch (status == null ? "" : status) {
            case "confirmed":
                pillRes = R.drawable.bg_booking_status_confirmed;
                textColorRes = R.color.booking_status_confirmed_text;
                break;
            case "completed":
                pillRes = R.drawable.bg_booking_status_completed;
                textColorRes = R.color.booking_status_completed_text;
                break;
            case "cancelled":
                pillRes = R.drawable.bg_booking_status_cancelled;
                textColorRes = R.color.booking_status_cancelled_text;
                break;
            case "expired":
                pillRes = R.drawable.bg_booking_status_expired;
                textColorRes = R.color.booking_status_expired_text;
                break;
            case "pending":
            default:
                pillRes = R.drawable.bg_booking_status_pending;
                textColorRes = R.color.booking_status_pending_text;
                break;
        }

        tvStatusBadge.setBackgroundResource(pillRes);
        tvStatusBadge.setTextColor(ContextCompat.getColor(this, textColorRes));
    }

    private void updateCancelButtonVisibility(String status) {
        // A booking that is already completed, cancelled or expired can no longer be cancelled.
        if ("completed".equals(status) || "cancelled".equals(status)
                || BookingSections.STATUS_EXPIRED.equals(status)) {
            btnCancelBooking.setVisibility(View.GONE);
        } else {
            btnCancelBooking.setVisibility(View.VISIBLE);
        }

        // Only a completed booking may be reviewed, so the button only exists for
        // that status. This is what enforces the rule, rather than a check inside
        // the review screen.
        btnLeaveReview.setVisibility("completed".equals(status) ? View.VISIBLE : View.GONE);
    }

    /**
     * Explains, on an expired booking, why it is no longer actionable: the salon
     * never confirmed or cancelled it before the appointment date passed.
     */
    private void updateExpiredNote(String status) {
        tvExpiredNote.setVisibility(
                BookingSections.STATUS_EXPIRED.equals(status) ? View.VISIBLE : View.GONE);
    }

    /**
     * Opens the review sheet for this booking. Reached only when the status is
     * completed, because that is the only case where the button is visible.
     */
    private void showLeaveReview() {
        if (currentSalonId == null || currentSalonId.isEmpty()) {
            Snackbars.show(findViewById(android.R.id.content), R.string.review_missing_booking);
            return;
        }
        LeaveReviewBottomSheet
                .newInstance(bookingId, currentSalonId)
                .show(getSupportFragmentManager(), "leave_review");
    }

    /**
     * Opens the report sheet pre-filled with this booking's salon and booking
     * id. Falls back to a message if the booking has no resolvable salon.
     */
    private void showReportSalon(Booking booking) {
        if (currentSalonId == null || currentSalonId.isEmpty()) {
            Snackbars.show(findViewById(android.R.id.content), R.string.report_salon_unavailable);
            return;
        }
        ReportProblemBottomSheet
                .newInstanceForSalon(currentSalonId, booking.getSalonName(), bookingId)
                .show(getSupportFragmentManager(), "report_problem");
    }

    private void showCancelConfirmation() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.cancel_booking_confirm_title)
                .setMessage(R.string.cancel_booking_confirm_message)
                .setPositiveButton(R.string.btn_cancel_booking, (dialog, which) -> cancelBooking())
                .setNegativeButton(R.string.btn_keep_booking, null)
                .show();
    }

    /**
     * Cancels the booking. cancelBooking() writes status "cancelled" and the
     * soft-delete flag together, so the owner sees the real status and the
     * booking cannot be cancelled a second time.
     */
    private void cancelBooking() {
        btnCancelBooking.setEnabled(false);

        bookingManager.cancelBooking(bookingId, new BookingManager.BookingCallback() {
            @Override
            public void onSuccess() {
                currentStatus = "cancelled";
                updateStatusBadge(currentStatus);
                updateCancelButtonVisibility(currentStatus);
                updateEditButton(currentStatus);
                btnCancelBooking.setEnabled(true);
                // Drop any pending reminders for a booking that is now cancelled.
                ReminderScheduler.cancel(BookingDetailsActivity.this, bookingId);
                Snackbars.show(findViewById(android.R.id.content), R.string.booking_cancelled);
            }

            @Override
            public void onFailure(String errorMessage) {
                btnCancelBooking.setEnabled(true);
                Snackbars.show(findViewById(android.R.id.content),
                        getString(R.string.booking_cancel_failed, errorMessage));
            }
        });
    }

    /** Opens the chat with the salon for this booking. */
    private void openChat() {
        Intent intent = new Intent(this, ChatActivity.class);
        intent.putExtra("bookingId", bookingId);
        startActivity(intent);
    }

    private void showMissingBooking() {
        progress.setVisibility(View.GONE);
        Snackbars.showThen(findViewById(android.R.id.content),
                R.string.booking_not_found, this::finish);
    }

    private String capitalize(String text) {
        if (text == null || text.isEmpty()) return text;
        return text.substring(0, 1).toUpperCase(Locale.getDefault()) + text.substring(1);
    }
}
