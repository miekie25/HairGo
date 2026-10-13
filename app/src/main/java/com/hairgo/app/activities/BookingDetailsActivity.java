package com.hairgo.app.activities;

import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.firebase.Timestamp;
import com.hairgo.app.R;
import com.hairgo.app.firebase.BookingManager;
import com.hairgo.app.fragments.LeaveReviewBottomSheet;
import com.hairgo.app.models.Booking;
import com.hairgo.app.utils.BookingSections;
import com.hairgo.app.utils.ReminderScheduler;
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
    private androidx.appcompat.widget.AppCompatButton btnCancelBooking;
    private androidx.appcompat.widget.AppCompatButton btnEditBooking;
    private androidx.appcompat.widget.AppCompatButton btnLeaveReview;
    private ProgressBar progress;
    private View contentGroup;
    private BookingManager bookingManager;
    private String currentStatus;
    private String bookingId;
    private String currentSalonId;

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

        return new Booking(
                bookingId,
                salonId == null ? null : salonId.toString(),
                salonName == null ? "" : salonName.toString(),
                serviceNames,
                dateTime,
                status.toString(),
                0);
    }

    private void bindBooking(Booking booking) {
        currentStatus = BookingSections.effectiveStatus(booking);

        progress.setVisibility(View.GONE);
        contentGroup.setVisibility(View.VISIBLE);

        TextView tvSalonName = findViewById(R.id.tvSalonName);
        TextView tvServiceName = findViewById(R.id.tvServiceName);
        TextView tvDateTime = findViewById(R.id.tvDateTime);
        tvStatusBadge = findViewById(R.id.tvStatusBadge);
        tvExpiredNote = findViewById(R.id.tvExpiredNote);
        btnCancelBooking = findViewById(R.id.btnCancelBooking);
        btnEditBooking = findViewById(R.id.btnEditBooking);
        btnLeaveReview = findViewById(R.id.btnLeaveReview);
        currentSalonId = booking.getSalonId();

        btnLeaveReview.setOnClickListener(v -> showLeaveReview());
        btnEditBooking.setOnClickListener(v -> onEditClicked(booking));

        tvSalonName.setText(booking.getSalonName());
        tvServiceName.setText(booking.getServiceLabel());

        if (booking.getDateTime() != null) {
            SimpleDateFormat format = new SimpleDateFormat(
                    getString(R.string.booking_date_format), Locale.getDefault());
            tvDateTime.setText(format.format(booking.getDateTime()));
        } else {
            tvDateTime.setText("");
        }

        updateStatusBadge(currentStatus);
        updateCancelButtonVisibility(currentStatus);
        updateExpiredNote(currentStatus);
        updateEditButton(currentStatus);

        btnCancelBooking.setOnClickListener(v -> showCancelConfirmation());
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

        int colorRes;
        switch (status == null ? "" : status) {
            case "confirmed":
                colorRes = R.color.teal;
                break;
            case "completed":
                colorRes = R.color.success;
                break;
            case "cancelled":
                colorRes = R.color.error;
                break;
            case "expired":
                colorRes = R.color.grey_medium;
                break;
            case "pending":
            default:
                colorRes = R.color.warning;
                break;
        }

        Drawable background = tvStatusBadge.getBackground();
        if (background instanceof GradientDrawable) {
            ((GradientDrawable) background.mutate())
                    .setColor(ContextCompat.getColor(this, colorRes));
        }
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
