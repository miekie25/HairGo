package com.hairgo.app.activities;

import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.firebase.Timestamp;
import com.hairgo.app.R;
import com.hairgo.app.firebase.BookingManager;
import com.hairgo.app.fragments.LeaveReviewBottomSheet;
import com.hairgo.app.models.Booking;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map;

public class BookingDetailsActivity extends AppCompatActivity {

    private TextView tvStatusBadge;
    private androidx.appcompat.widget.AppCompatButton btnCancelBooking;
    private androidx.appcompat.widget.AppCompatButton btnLeaveReview;
    private BookingManager bookingManager;
    private String currentStatus;
    private String bookingId;
    private String currentSalonId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_booking_details);

        bookingId = getIntent().getStringExtra("bookingId");

        ImageButton backBtn = findViewById(R.id.header).findViewById(R.id.btnBack);
        backBtn.setOnClickListener(v -> onBackPressed());

        if (bookingId == null || bookingId.isEmpty()) {
            showMissingBooking();
            return;
        }

        bookingManager = new BookingManager();
        loadBooking();
    }

    private void loadBooking() {
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
        Object serviceName = raw.get("serviceName");
        Object salonId = raw.get("salonID");

        Date dateTime = null;
        Object dateValue = raw.get("dateTime");
        if (dateValue instanceof Timestamp) {
            dateTime = ((Timestamp) dateValue).toDate();
        }

        return new Booking(
                bookingId,
                salonId == null ? null : salonId.toString(),
                salonName == null ? "" : salonName.toString(),
                serviceName == null ? "" : serviceName.toString(),
                dateTime,
                status.toString());
    }

    private void bindBooking(Booking booking) {
        currentStatus = booking.getStatus();

        TextView headerTitle = findViewById(R.id.header).findViewById(R.id.tvHeaderTitle);
        headerTitle.setText(R.string.title_booking_details);

        TextView tvSalonName = findViewById(R.id.tvSalonName);
        TextView tvServiceName = findViewById(R.id.tvServiceName);
        TextView tvDateTime = findViewById(R.id.tvDateTime);
        tvStatusBadge = findViewById(R.id.tvStatusBadge);
        btnCancelBooking = findViewById(R.id.btnCancelBooking);
        btnLeaveReview = findViewById(R.id.btnLeaveReview);
        currentSalonId = booking.getSalonId();

        btnLeaveReview.setOnClickListener(v -> showLeaveReview());

        tvSalonName.setText(booking.getSalonName());
        tvServiceName.setText(booking.getServiceName());

        if (booking.getDateTime() != null) {
            SimpleDateFormat format = new SimpleDateFormat(
                    getString(R.string.booking_date_format), Locale.getDefault());
            tvDateTime.setText(format.format(booking.getDateTime()));
        } else {
            tvDateTime.setText("");
        }

        updateStatusBadge(currentStatus);
        updateCancelButtonVisibility(currentStatus);

        btnCancelBooking.setOnClickListener(v -> showCancelConfirmation());
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
        // A booking that is already completed or cancelled can no longer be cancelled.
        if ("completed".equals(status) || "cancelled".equals(status)) {
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
     * Opens the review sheet for this booking. Reached only when the status is
     * completed, because that is the only case where the button is visible.
     */
    private void showLeaveReview() {
        if (currentSalonId == null || currentSalonId.isEmpty()) {
            Toast.makeText(this, R.string.review_missing_booking, Toast.LENGTH_LONG).show();
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
                btnCancelBooking.setEnabled(true);
                Toast.makeText(BookingDetailsActivity.this,
                        R.string.booking_cancelled, Toast.LENGTH_LONG).show();
            }

            @Override
            public void onFailure(String errorMessage) {
                btnCancelBooking.setEnabled(true);
                Toast.makeText(BookingDetailsActivity.this,
                        getString(R.string.booking_cancel_failed, errorMessage),
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    private void showMissingBooking() {
        Toast.makeText(this, R.string.booking_not_found, Toast.LENGTH_SHORT).show();
        finish();
    }

    private String capitalize(String text) {
        if (text == null || text.isEmpty()) return text;
        return text.substring(0, 1).toUpperCase(Locale.getDefault()) + text.substring(1);
    }
}
