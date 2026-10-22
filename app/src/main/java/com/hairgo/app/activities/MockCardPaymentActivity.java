package com.hairgo.app.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatButton;

import com.hairgo.app.R;
import com.hairgo.app.models.PaymentDetails;

import java.util.Calendar;
import java.util.Locale;

public class MockCardPaymentActivity extends AppCompatActivity {

    public static final String EXTRA_AMOUNT = "amount";
    public static final String EXTRA_PAYMENT_TYPE = "paymentType";
    public static final String EXTRA_IS_ADDITIONAL_PAYMENT = "isAdditionalPayment";

    private EditText etCardholderName;
    private EditText etCardNumber;
    private EditText etExpiry;
    private EditText etCvv;
    private TextView tvError;
    private AppCompatButton btnPay;

    private double amount;

    private final ActivityResultLauncher<Intent> paymentResultLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK) {
                    setResult(RESULT_OK);
                    finish();
                    return;
                }

                btnPay.setEnabled(true);
                showError(getString(R.string.payment_failed_no_booking));
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mock_card_payment);

        ImageButton backBtn = findViewById(R.id.header).findViewById(R.id.btnBack);
        backBtn.setOnClickListener(v -> finish());

        TextView headerTitle = findViewById(R.id.header).findViewById(R.id.tvHeaderTitle);
        headerTitle.setText(R.string.title_card_payment);

        amount = getIntent().getDoubleExtra(EXTRA_AMOUNT, 0.0);
        if (amount <= 0) {
            finish();
            return;
        }

        String paymentType = getIntent().getStringExtra(EXTRA_PAYMENT_TYPE);
        boolean additionalPayment = getIntent().getBooleanExtra(EXTRA_IS_ADDITIONAL_PAYMENT, false);

        TextView tvPaymentType = findViewById(R.id.tvPaymentType);
        if (PaymentDetails.TYPE_DEPOSIT.equals(paymentType)) {
            tvPaymentType.setText(additionalPayment
                    ? R.string.payment_additional_deposit_label
                    : R.string.payment_deposit_label);
        } else {
            tvPaymentType.setText(additionalPayment
                    ? R.string.payment_remaining_balance_label
                    : R.string.payment_full_label);
        }

        TextView tvAmount = findViewById(R.id.tvAmount);
        tvAmount.setText(getString(R.string.service_price_rand, formatPrice(amount)));

        etCardholderName = findViewById(R.id.etCardholderName);
        etCardNumber = findViewById(R.id.etCardNumber);
        etExpiry = findViewById(R.id.etExpiry);
        etCvv = findViewById(R.id.etCvv);
        tvError = findViewById(R.id.tvError);
        btnPay = findViewById(R.id.btnPay);

        btnPay.setText(getString(R.string.btn_pay_amount,
                getString(R.string.service_price_rand, formatPrice(amount))));
        btnPay.setOnClickListener(v -> submitPayment());
    }

    private void submitPayment() {
        String name = etCardholderName.getText().toString().trim();
        String cardNumber = etCardNumber.getText().toString().replaceAll("\\s+", "");
        String expiry = etExpiry.getText().toString().trim();
        String cvv = etCvv.getText().toString().trim();

        if (name.isEmpty()) {
            showError(getString(R.string.error_cardholder_required));
            return;
        }

        if (!cardNumber.matches("\\d{16}")) {
            showError(getString(R.string.error_card_number_invalid));
            return;
        }

        if (!isValidExpiry(expiry)) {
            showError(getString(R.string.error_card_expiry_invalid));
            return;
        }

        if (!cvv.matches("\\d{3}")) {
            showError(getString(R.string.error_card_cvv_invalid));
            return;
        }

        tvError.setVisibility(View.GONE);
        btnPay.setEnabled(false);

        Intent intent = new Intent(this, PaymentResultActivity.class);
        intent.putExtra(PaymentResultActivity.EXTRA_AMOUNT, amount);
        intent.putExtra(PaymentResultActivity.EXTRA_LAST4,
                cardNumber.substring(cardNumber.length() - 4));
        intent.putExtra(PaymentResultActivity.EXTRA_SIMULATE_FAILURE,
                cardNumber.endsWith("0000"));
        paymentResultLauncher.launch(intent);
    }

    private boolean isValidExpiry(String expiry) {
        if (!expiry.matches("^(0[1-9]|1[0-2])/\\d{2}$")) {
            return false;
        }

        String[] parts = expiry.split("/");
        int month = Integer.parseInt(parts[0]);
        int year = 2000 + Integer.parseInt(parts[1]);

        Calendar now = Calendar.getInstance();
        int currentYear = now.get(Calendar.YEAR);
        int currentMonth = now.get(Calendar.MONTH) + 1;

        return year > currentYear || (year == currentYear && month >= currentMonth);
    }

    private void showError(String message) {
        tvError.setText(message);
        tvError.setVisibility(View.VISIBLE);
    }

    private String formatPrice(double price) {
        if (price == Math.rint(price)) {
            return String.valueOf((long) price);
        }
        return String.format(Locale.US, "%.2f", price);
    }
}
