package com.hairgo.app.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatButton;

import com.hairgo.app.R;
import com.hairgo.app.models.PaymentDetails;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class BookingConfirmationActivity extends AppCompatActivity {

    public static final String EXTRA_BOOKING_ID = "bookingId";
    public static final String EXTRA_SALON_NAME = "salonName";
    public static final String EXTRA_SERVICE_LABEL = "serviceLabel";
    public static final String EXTRA_DATE_TIME = "dateTime";
    public static final String EXTRA_TOTAL_PRICE = "totalPrice";
    public static final String EXTRA_TOTAL_DURATION = "totalDuration";
    public static final String EXTRA_PAYMENT_TYPE = "paymentType";
    public static final String EXTRA_AMOUNT_PAID = "amountPaid";
    public static final String EXTRA_AMOUNT_OUTSTANDING = "amountOutstanding";

    private String bookingId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_booking_confirmation);

        ImageButton backBtn = findViewById(R.id.header).findViewById(R.id.btnBack);
        backBtn.setOnClickListener(v -> goToBookings());

        TextView headerTitle = findViewById(R.id.header).findViewById(R.id.tvHeaderTitle);
        headerTitle.setText(R.string.title_confirmation);

        bookingId = getIntent().getStringExtra(EXTRA_BOOKING_ID);
        String salonName = getIntent().getStringExtra(EXTRA_SALON_NAME);
        String serviceLabel = getIntent().getStringExtra(EXTRA_SERVICE_LABEL);
        long dateTimeMillis = getIntent().getLongExtra(EXTRA_DATE_TIME, 0L);
        double totalPrice = getIntent().getDoubleExtra(EXTRA_TOTAL_PRICE, Double.NaN);
        int totalDuration = getIntent().getIntExtra(EXTRA_TOTAL_DURATION, 0);
        String paymentType = getIntent().getStringExtra(EXTRA_PAYMENT_TYPE);
        double amountPaid = getIntent().getDoubleExtra(EXTRA_AMOUNT_PAID, Double.NaN);
        double amountOutstanding = getIntent().getDoubleExtra(EXTRA_AMOUNT_OUTSTANDING, Double.NaN);

        TextView tvMessage = findViewById(R.id.tvConfirmationMessage);
        TextView tvSalon = findViewById(R.id.tvConfirmationSalon);
        TextView tvServices = findViewById(R.id.tvConfirmationServices);
        TextView tvDateTime = findViewById(R.id.tvConfirmationDateTime);
        TextView tvTotal = findViewById(R.id.tvConfirmationTotal);
        TextView tvPayment = findViewById(R.id.tvConfirmationPayment);
        AppCompatButton btnViewBooking = findViewById(R.id.btnViewBooking);
        AppCompatButton btnDone = findViewById(R.id.btnDone);

        if (salonName != null && !salonName.isEmpty()) {
            tvSalon.setText(salonName);
            tvMessage.setText(getString(R.string.booking_confirmation_body, salonName));
        } else {
            tvSalon.setVisibility(View.GONE);
            tvMessage.setVisibility(View.GONE);
        }

        if (serviceLabel != null && !serviceLabel.isEmpty()) {
            tvServices.setText(getString(R.string.confirmation_service_line, serviceLabel));
        } else {
            tvServices.setVisibility(View.GONE);
        }

        if (dateTimeMillis > 0) {
            SimpleDateFormat format = new SimpleDateFormat(
                    getString(R.string.booking_date_format), Locale.getDefault());
            tvDateTime.setText(getString(R.string.confirmation_date_time_line,
                    format.format(new Date(dateTimeMillis))));
        } else {
            tvDateTime.setVisibility(View.GONE);
        }

        if (!Double.isNaN(totalPrice)) {
            String price = getString(R.string.service_price_rand, formatPrice(totalPrice));
            if (totalDuration > 0) {
                tvTotal.setText(getString(R.string.service_total, price, totalDuration));
            } else {
                tvTotal.setText(getString(R.string.confirmation_total, price));
            }
        } else {
            tvTotal.setVisibility(View.GONE);
        }

        String paymentSummary = paymentSummary(paymentType, amountPaid, amountOutstanding);
        if (paymentSummary != null) {
            tvPayment.setText(paymentSummary);
        } else {
            tvPayment.setVisibility(View.GONE);
        }

        if (bookingId == null || bookingId.isEmpty()) {
            btnViewBooking.setVisibility(View.GONE);
        } else {
            btnViewBooking.setOnClickListener(v -> {
                Intent intent = new Intent(this, BookingDetailsActivity.class);
                intent.putExtra("bookingId", bookingId);
                startActivity(intent);
            });
        }

        btnDone.setOnClickListener(v -> goToBookings());
    }

    private String paymentSummary(String paymentType, double amountPaid, double amountOutstanding) {
        if (PaymentDetails.TYPE_CASH.equals(paymentType)) {
            if (Double.isNaN(amountOutstanding)) {
                return getString(R.string.payment_cash_summary_unknown);
            }
            return getString(R.string.payment_cash_summary, formatPrice(amountOutstanding));
        }

        if (PaymentDetails.TYPE_DEPOSIT.equals(paymentType)) {
            if (Double.isNaN(amountPaid) || Double.isNaN(amountOutstanding)) {
                return null;
            }
            return getString(R.string.payment_deposit_summary,
                    formatPrice(amountPaid), formatPrice(amountOutstanding));
        }

        if (PaymentDetails.TYPE_FULL.equals(paymentType)) {
            if (Double.isNaN(amountPaid)) {
                return null;
            }
            return getString(R.string.payment_full_summary, formatPrice(amountPaid));
        }

        return null;
    }

    private void goToBookings() {
        Intent intent = new Intent(this, ClientDashboardActivity.class);
        intent.putExtra(ClientDashboardActivity.EXTRA_SELECTED_TAB, R.id.nav_bookings);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }

    @Override
    public void onBackPressed() {
        goToBookings();
    }

    private String formatPrice(double price) {
        if (price == Math.rint(price)) {
            return String.valueOf((long) price);
        }
        return String.format(Locale.US, "%.2f", price);
    }
}
