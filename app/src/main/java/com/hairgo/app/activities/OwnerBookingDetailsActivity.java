package com.hairgo.app.activities;

import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
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
import com.hairgo.app.models.PaymentDetails;
import com.hairgo.app.models.ServiceDetail;
import com.hairgo.app.utils.Snackbars;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
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
    private TextView tvPaymentLabel;
    private TextView tvPayment;

    private androidx.appcompat.widget.AppCompatButton btnMessageClient;
    private androidx.appcompat.widget.AppCompatButton btnConfirm;
    private androidx.appcompat.widget.AppCompatButton btnDecline;
    private androidx.appcompat.widget.AppCompatButton btnMarkCompleted;
    private androidx.appcompat.widget.AppCompatButton btnRecordCashPayment;

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
        tvPaymentLabel = findViewById(R.id.tvPaymentLabel);
        tvPayment = findViewById(R.id.tvPayment);

        btnMessageClient = findViewById(R.id.btnMessageClient);
        btnConfirm = findViewById(R.id.btnConfirm);
        btnDecline = findViewById(R.id.btnDecline);
        btnMarkCompleted = findViewById(R.id.btnMarkCompleted);
        btnRecordCashPayment = findViewById(R.id.btnRecordCashPayment);

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

        TextView tvBookingTotal = findViewById(R.id.tvBookingTotal);
        TextView tvBookingNote = findViewById(R.id.tvBookingNote);
        bindServices(booking, tvServiceName, tvBookingTotal, tvBookingNote);
        bindPayment(booking);

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

    /**
     * Shows one line per saved service with its price and duration, then the
     * booking total. Old bookings have no saved services, so they keep the
     * plain service name and hide the total.
     */
    private void bindServices(OwnerBooking booking, TextView tvServiceName,
                              TextView tvBookingTotal, TextView tvBookingNote) {
        if (!booking.hasServiceDetails()) {
            tvServiceName.setText(booking.getServiceName());
            tvBookingTotal.setVisibility(View.GONE);
            tvBookingNote.setVisibility(View.GONE);
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
                tvBookingTotal.setText(getString(R.string.booking_total_no_price, totalDuration));
                tvBookingTotal.setVisibility(View.VISIBLE);
            } else {
                tvBookingTotal.setVisibility(View.GONE);
            }
            tvBookingNote.setVisibility(View.GONE);
            return;
        }

        tvBookingTotal.setText(getString(R.string.service_total,
                getString(R.string.service_price_rand, formatPriceValue(totalPrice)),
                totalDuration));
        tvBookingTotal.setVisibility(View.VISIBLE);
        tvBookingNote.setVisibility(booking.hasUnpricedServices() ? View.VISIBLE : View.GONE);
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

    private void bindPayment(OwnerBooking booking) {
        PaymentDetails payment = booking.getPaymentDetails();
        String summary = paymentSummary(payment);

        if (summary == null) {
            tvPaymentLabel.setVisibility(View.GONE);
            tvPayment.setVisibility(View.GONE);
            btnRecordCashPayment.setVisibility(View.GONE);
            return;
        }

        tvPayment.setText(summary);
        tvPaymentLabel.setVisibility(View.VISIBLE);
        tvPayment.setVisibility(View.VISIBLE);

        // Show Record Cash Payment button only for cash bookings with outstanding balance
        boolean canRecordCash = payment != null && payment.canRecordCashPayment();
        btnRecordCashPayment.setVisibility(canRecordCash ? View.VISIBLE : View.GONE);

        if (canRecordCash) {
            btnRecordCashPayment.setOnClickListener(v -> showRecordCashPaymentDialog(payment));
        }
    }

    private String paymentSummary(PaymentDetails payment) {
        if (payment == null) {
            return getString(R.string.payment_status_unknown);
        }

        if (PaymentDetails.TYPE_CASH.equals(payment.getType())) {
            if (payment.getAmountOutstanding() == null) {
                return getString(R.string.payment_cash_summary_unknown);
            }
            return getString(R.string.payment_cash_summary,
                    formatPriceValue(payment.getAmountOutstanding()));
        }

        if (PaymentDetails.TYPE_DEPOSIT.equals(payment.getType())) {
            if (payment.getAmountPaid() == null || payment.getAmountOutstanding() == null) {
                return getString(R.string.payment_status_unknown);
            }
            return getString(R.string.payment_deposit_summary_owner,
                    formatPriceValue(payment.getAmountPaid()),
                    formatPriceValue(payment.getAmountOutstanding()));
        }

        if (PaymentDetails.TYPE_FULL.equals(payment.getType())) {
            if (payment.getAmountPaid() == null) {
                return getString(R.string.payment_status_unknown);
            }
            return getString(R.string.payment_full_summary_owner,
                    formatPriceValue(payment.getAmountPaid()));
        }

        return getString(R.string.payment_status_unknown);
    }

    private void showRecordCashPaymentDialog(PaymentDetails payment) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.title_record_cash_payment);

        double outstanding = payment.getAmountOutstanding() == null ? 0.0 : payment.getAmountOutstanding();
        String message = getString(R.string.message_record_cash_payment, formatPriceValue(outstanding));
        builder.setMessage(message);

        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);
        input.setHint(R.string.hint_cash_amount);
        input.setPadding(48, 24, 48, 24);
        builder.setView(input);

        builder.setPositiveButton(R.string.ok, (dialog, which) -> {
            String amountStr = input.getText().toString().trim();
            if (amountStr.isEmpty()) {
                Snackbars.show(findViewById(android.R.id.content), R.string.error_cash_amount_required);
                return;
            }

            double amount;
            try {
                amount = Double.parseDouble(amountStr);
            } catch (NumberFormatException e) {
                Snackbars.show(findViewById(android.R.id.content), R.string.error_cash_amount_invalid);
                return;
            }

            if (amount <= 0) {
                Snackbars.show(findViewById(android.R.id.content), R.string.error_cash_amount_invalid);
                return;
            }

            if (amount > outstanding) {
                Snackbars.show(findViewById(android.R.id.content),
                        getString(R.string.error_cash_amount_exceeds_outstanding, formatPriceValue(outstanding)));
                return;
            }

            recordCashPayment(amount);
        });

        builder.setNegativeButton(R.string.cancel, null);
        builder.show();
    }

    private void recordCashPayment(double amount) {
        btnRecordCashPayment.setEnabled(false);
        bookingManager.recordCashPayment(bookingId, amount, new BookingManager.BookingCallback() {
            @Override
            public void onSuccess() {
                if (isFinishing() || isDestroyed()) return;
                btnRecordCashPayment.setEnabled(true);
                // Reload the booking to refresh payment display
                loadBooking();
                Snackbars.show(findViewById(android.R.id.content),
                        getString(R.string.cash_payment_recorded, formatPriceValue(amount)));
            }

            @Override
            public void onFailure(String errorMessage) {
                if (isFinishing() || isDestroyed()) return;
                btnRecordCashPayment.setEnabled(true);
                Snackbars.show(findViewById(android.R.id.content), errorMessage);
            }
        });
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
