package com.hairgo.app.activities;

import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.hairgo.app.R;
import com.hairgo.app.firebase.BookingManager;
import com.hairgo.app.firebase.OwnerManager;
import com.hairgo.app.models.OwnerBooking;
import com.hairgo.app.utils.Snackbars;

import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.Map;

/**
 * The owner's view of one booking: client name and phone, the service, when it
 * is, and the note the client left. Also carries the same actions the list card
 * has - confirm, decline, mark completed - so the owner can act from here too.
 */
public class OwnerBookingDetailsActivity extends AppCompatActivity {

    private ProgressBar progress;
    private View contentGroup;

    private TextView tvStatusBadge;
    private TextView tvClientName;
    private TextView tvPhone;
    private TextView tvServiceName;
    private TextView tvDateTime;
    private TextView tvNotesLabel;
    private TextView tvNotes;

    private androidx.appcompat.widget.AppCompatButton btnMessageClient;
    private androidx.appcompat.widget.AppCompatButton btnConfirm;
    private androidx.appcompat.widget.AppCompatButton btnDecline;
    private androidx.appcompat.widget.AppCompatButton btnMarkCompleted;

    private BookingManager bookingManager;
    private OwnerManager ownerManager;

    private String bookingId;
    private String currentStatus;
    private String clientId;
    private boolean cancelledByClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_owner_booking_details);

        bookingId = getIntent().getStringExtra("bookingId");

        ImageButton backBtn = findViewById(R.id.header).findViewById(R.id.btnBack);
        backBtn.setOnClickListener(v -> onBackPressed());

        TextView headerTitle = findViewById(R.id.header).findViewById(R.id.tvHeaderTitle);
        headerTitle.setText(R.string.title_owner_booking_details);

        progress = findViewById(R.id.progress);
        contentGroup = findViewById(R.id.contentGroup);

        tvStatusBadge = findViewById(R.id.tvStatusBadge);
        tvClientName = findViewById(R.id.tvClientName);
        tvPhone = findViewById(R.id.tvPhone);
        tvServiceName = findViewById(R.id.tvServiceName);
        tvDateTime = findViewById(R.id.tvDateTime);
        tvNotesLabel = findViewById(R.id.tvNotesLabel);
        tvNotes = findViewById(R.id.tvNotes);

        btnMessageClient = findViewById(R.id.btnMessageClient);
        btnConfirm = findViewById(R.id.btnConfirm);
        btnDecline = findViewById(R.id.btnDecline);
        btnMarkCompleted = findViewById(R.id.btnMarkCompleted);

        bookingManager = new BookingManager();
        ownerManager = new OwnerManager();

        if (bookingId == null || bookingId.isEmpty()) {
            showMissingBooking();
            return;
        }

        loadBooking();
    }

    private void loadBooking() {
        progress.setVisibility(View.VISIBLE);
        contentGroup.setVisibility(View.INVISIBLE);

        // includeDeleted: the owner's list keeps client-cancelled bookings
        // visible, so tapping one of those must not 404 here.
        bookingManager.getBookingById(bookingId, true, new BookingManager.BookingDataCallback() {
            @Override
            public void onSuccess(Map<String, Object> rawBooking) {
                OwnerBooking booking = OwnerBooking.fromMap(rawBooking);
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

    private void bindBooking(OwnerBooking booking) {
        currentStatus = booking.getStatus();
        clientId = booking.getClientId();
        cancelledByClient = booking.isCancelledByClient();

        progress.setVisibility(View.GONE);
        contentGroup.setVisibility(View.VISIBLE);

        String clientName = booking.getClientName();
        tvClientName.setText(clientName.isEmpty() ? "Unknown client" : clientName);
        tvServiceName.setText(booking.getServiceName());

        if (booking.getDateTime() != null) {
            SimpleDateFormat format = new SimpleDateFormat("EEE, dd MMM yyyy • HH:mm", Locale.getDefault());
            tvDateTime.setText(format.format(booking.getDateTime()));
        } else {
            tvDateTime.setText("");
        }

        bindNotes(booking.getNotes());
        updateStatusBadge(currentStatus);
        updateActionButtons(currentStatus);
        loadClientPhone();

        btnMessageClient.setOnClickListener(v -> openChat());
        btnConfirm.setOnClickListener(v -> updateStatus("confirmed"));
        btnDecline.setOnClickListener(v -> confirmDecline());
        btnMarkCompleted.setOnClickListener(v -> updateStatus("completed"));
    }

    /** The phone lives on the client's profile, not the booking, so it is a separate read. */
    private void loadClientPhone() {
        if (clientId == null || clientId.isEmpty()) {
            hidePhone();
            return;
        }

        ownerManager.getClientPhone(clientId, new OwnerManager.OwnerCallback<String>() {
            @Override
            public void onSuccess(String phone) {
                if (!isFinishing() && !isDestroyed()) bindPhone(phone);
            }

            @Override
            public void onFailure(String errorMessage) {
                if (!isFinishing() && !isDestroyed()) hidePhone();
            }
        });
    }

    private void bindPhone(String phone) {
        if (phone == null || phone.trim().isEmpty()) {
            hidePhone();
            return;
        }

        String shown = phone.trim();
        tvPhone.setText(shown);
        // Restore the tappable styling in case hidePhone() ran first.
        tvPhone.setTextColor(ContextCompat.getColor(this, R.color.teal_primary));
        tvPhone.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, R.drawable.ic_chevron_right, 0);
        tvPhone.setVisibility(View.VISIBLE);
        tvPhone.setOnClickListener(v -> {
            Intent dial = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + shown));
            startActivity(dial);
        });
    }

    /** Keeps the label but swaps in the "no number" line when nothing is on file. */
    private void hidePhone() {
        tvPhone.setText(R.string.phone_unavailable);
        tvPhone.setTextColor(ContextCompat.getColor(this, R.color.grey_medium));
        tvPhone.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, 0, 0);
        tvPhone.setOnClickListener(null);
        tvPhone.setVisibility(View.VISIBLE);
    }

    private void bindNotes(String notes) {
        if (notes == null || notes.trim().isEmpty()) {
            tvNotesLabel.setVisibility(View.GONE);
            tvNotes.setVisibility(View.GONE);
            return;
        }

        tvNotes.setText(notes.trim());
        tvNotesLabel.setVisibility(View.VISIBLE);
        tvNotes.setVisibility(View.VISIBLE);
    }

    /** Pending: Confirm + Decline.  Confirmed: Mark completed.  Anything else: none. */
    private void updateActionButtons(String status) {
        boolean isPending = "pending".equals(status);
        boolean canComplete = "confirmed".equals(status);

        btnConfirm.setVisibility(isPending ? View.VISIBLE : View.GONE);
        btnDecline.setVisibility(isPending ? View.VISIBLE : View.GONE);
        btnMarkCompleted.setVisibility(canComplete ? View.VISIBLE : View.GONE);
    }

    private void updateStatusBadge(String status) {
        String label;
        if ("cancelled".equals(status)) {
            // The card tells the two kinds of cancellation apart; match it so
            // the owner knows whether the client cancelled or they declined.
            label = cancelledByClient ? "Client cancelled" : "Declined";
        } else {
            label = capitalize(status);
        }
        tvStatusBadge.setText(label);

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

    private void updateStatus(String newStatus) {
        bookingManager.updateBookingStatus(bookingId, newStatus,
                new BookingManager.BookingCallback() {
                    @Override
                    public void onSuccess() {
                        if (isFinishing() || isDestroyed()) return;
                        currentStatus = newStatus;
                        updateStatusBadge(currentStatus);
                        updateActionButtons(currentStatus);
                        Snackbars.show(findViewById(android.R.id.content),
                                newStatus.equals("completed")
                                        ? R.string.booking_marked_completed
                                        : R.string.booking_confirmed);
                    }

                    @Override
                    public void onFailure(String errorMessage) {
                        if (isFinishing() || isDestroyed()) return;
                        Snackbars.show(findViewById(android.R.id.content),
                                getString(R.string.booking_update_failed, errorMessage));
                    }
                });
    }

    private void confirmDecline() {
        new AlertDialog.Builder(this)
                .setTitle("Decline this booking?")
                .setMessage("The client will see it as cancelled, and the time slot becomes available again.")
                .setPositiveButton("Decline", (dialog, which) -> updateStatus("cancelled"))
                .setNegativeButton("Keep", null)
                .show();
    }

    /** Opens the chat with this booking's client. */
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
