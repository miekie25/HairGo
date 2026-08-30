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

import com.hairgo.app.R;
import com.hairgo.app.models.Booking;
import com.hairgo.app.utils.DummyData;

import java.text.SimpleDateFormat;
import java.util.Locale;

public class BookingDetailsActivity extends AppCompatActivity {

    private TextView tvStatusBadge;
    private androidx.appcompat.widget.AppCompatButton btnCancelBooking;
    private String currentStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_booking_details);

        String bookingId = getIntent().getStringExtra("bookingId");
        Booking booking = DummyData.getDummyBookingById(bookingId);

        if (booking == null) {
            Toast.makeText(this, "Booking not found.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        currentStatus = booking.getStatus();

        TextView headerTitle = findViewById(R.id.header).findViewById(R.id.tvHeaderTitle);
        headerTitle.setText(R.string.title_booking_details);

        ImageButton backBtn = findViewById(R.id.header).findViewById(R.id.btnBack);
        backBtn.setOnClickListener(v -> onBackPressed());

        TextView tvSalonName = findViewById(R.id.tvSalonName);
        TextView tvServiceName = findViewById(R.id.tvServiceName);
        TextView tvDateTime = findViewById(R.id.tvDateTime);
        tvStatusBadge = findViewById(R.id.tvStatusBadge);
        btnCancelBooking = findViewById(R.id.btnCancelBooking);

        tvSalonName.setText(booking.getSalonName());
        tvServiceName.setText(booking.getServiceName());

        SimpleDateFormat format = new SimpleDateFormat("dd MMM yyyy • hh:mm a", Locale.getDefault());
        tvDateTime.setText(format.format(booking.getDateTime()));

        updateStatusBadge(currentStatus);
        updateCancelButtonVisibility(currentStatus);

        btnCancelBooking.setOnClickListener(v -> showCancelConfirmation());
    }

    private void updateStatusBadge(String status) {
        tvStatusBadge.setText(capitalize(status));

        int colorRes;
        switch (status) {
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
        // Can't cancel a booking that's already completed or cancelled
        if (status.equals("completed") || status.equals("cancelled")) {
            btnCancelBooking.setVisibility(View.GONE);
        } else {
            btnCancelBooking.setVisibility(View.VISIBLE);
        }
    }

    private void showCancelConfirmation() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.cancel_booking_confirm_title)
                .setMessage(R.string.cancel_booking_confirm_message)
                .setPositiveButton(R.string.btn_cancel_booking, (dialog, which) -> {
                    // TEMP: local demo cancel — replaced with BookingManager.softDeleteBooking() in Phase 2
                    currentStatus = "cancelled";
                    updateStatusBadge(currentStatus);
                    updateCancelButtonVisibility(currentStatus);
                    Toast.makeText(this, R.string.booking_cancelled_demo, Toast.LENGTH_LONG).show();
                })
                .setNegativeButton(R.string.btn_keep_booking, null)
                .show();
    }

    private String capitalize(String text) {
        if (text == null || text.isEmpty()) return text;
        return text.substring(0, 1).toUpperCase(Locale.getDefault()) + text.substring(1);
    }
}