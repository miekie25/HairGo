package com.hairgo.app.activities;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
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
import com.hairgo.app.utils.CardFormatter;

import java.util.Locale;

public class MockCardPaymentActivity extends AppCompatActivity {

    public static final String EXTRA_AMOUNT = "amount";
    public static final String EXTRA_PAYMENT_TYPE = "paymentType";
    public static final String EXTRA_IS_ADDITIONAL_PAYMENT = "isAdditionalPayment";

    private EditText etCardholderName;
    private EditText etCardNumber;
    private EditText etExpiry;
    private EditText etCvv;
    private TextView tvNameError;
    private TextView tvCardNumberError;
    private TextView tvExpiryError;
    private TextView tvCvvError;
    private AppCompatButton btnPay;

    private double amount;

    private boolean nameValid = false;
    private boolean cardNumberValid = false;
    private boolean expiryValid = false;
    private boolean cvvValid = false;

    private final ActivityResultLauncher<Intent> paymentResultLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK) {
                    setResult(RESULT_OK);
                    finish();
                    return;
                }

                btnPay.setEnabled(true);
                showExpiryError(getString(R.string.payment_failed_no_booking));
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
        tvNameError = findViewById(R.id.tvNameError);
        tvCardNumberError = findViewById(R.id.tvCardNumberError);
        tvExpiryError = findViewById(R.id.tvExpiryError);
        tvCvvError = findViewById(R.id.tvCvvError);
        btnPay = findViewById(R.id.btnPay);

        btnPay.setText(getString(R.string.btn_pay_amount,
                getString(R.string.service_price_rand, formatPrice(amount))));
        btnPay.setEnabled(false);
        btnPay.setOnClickListener(v -> submitPayment());

        setupFormatters();
    }

    private void setupFormatters() {
        // Cardholder name: filter + capitalize
        CardFormatter.attachNameFormatter(etCardholderName, (isComplete, errorKey) -> {
            if (errorKey != null) {
                nameValid = false;
                showNameError(getString(R.string.error_name_invalid));
            } else {
                nameValid = true;
                hideNameError();
            }
            updatePayButton();
        });

        // Card number: auto-space every 4 digits
        CardFormatter.attachCardNumberFormatter(etCardNumber, (isComplete, errorKey) -> {
            if (errorKey != null) {
                cardNumberValid = false;
            } else {
                cardNumberValid = true;
                hideCardNumberError();
            }
            updatePayButton();
        });
        // Show "incomplete" error on focus loss
        etCardNumber.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus && !cardNumberValid) {
                showCardNumberError(getString(R.string.error_card_number_incomplete));
            }
        });

        // Expiry: auto-slash, validate month immediately, validate expiry when complete
        CardFormatter.attachExpiryFormatter(etExpiry, (isComplete, errorKey) -> {
            if (errorKey != null) {
                expiryValid = false;
                if ("month".equals(errorKey)) {
                    showExpiryError(getString(R.string.error_expiry_month_invalid));
                } else if ("expired".equals(errorKey)) {
                    showExpiryError(getString(R.string.error_expiry_expired));
                }
            } else {
                expiryValid = true;
                hideExpiryError();
            }
            updatePayButton();
        });
        // Show "invalid format" error on focus loss if incomplete
        etExpiry.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus && !expiryValid) {
                String text = etExpiry.getText().toString();
                if (!text.isEmpty()) {
                    showExpiryError(getString(R.string.error_card_expiry_invalid));
                }
            }
        });

        // CVV: validate length
        CardFormatter.attachCvvFormatter(etCvv, (isComplete, errorKey) -> {
            if (errorKey != null) {
                cvvValid = false;
            } else {
                cvvValid = true;
                hideCvvError();
            }
            updatePayButton();
        });
        etCvv.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus && !cvvValid) {
                showCvvError(getString(R.string.error_card_cvv_invalid));
            }
        });

        // Also validate name on focus loss
        etCardholderName.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus && !nameValid) {
                String text = etCardholderName.getText().toString().trim();
                if (!text.isEmpty()) {
                    showNameError(getString(R.string.error_name_invalid));
                }
            }
        });
    }

    private void updatePayButton() {
        btnPay.setEnabled(nameValid && cardNumberValid && expiryValid && cvvValid);
    }

    private void submitPayment() {
        String name = etCardholderName.getText().toString().trim();
        String cardNumber = etCardNumber.getText().toString().replaceAll("\\s+", "");
        String expiry = etExpiry.getText().toString().trim();
        String cvv = etCvv.getText().toString().trim();

        // Final validation with inline errors
        boolean allValid = true;

        if (name.isEmpty() || name.length() < 2) {
            showNameError(getString(R.string.error_name_invalid));
            allValid = false;
        }

        if (cardNumber.length() != 16) {
            showCardNumberError(getString(R.string.error_card_number_incomplete));
            allValid = false;
        }

        if (!CardFormatter.isValidExpiry(expiry)) {
            showExpiryError(getString(R.string.error_card_expiry_invalid));
            allValid = false;
        }

        if (cvv.length() != 3) {
            showCvvError(getString(R.string.error_card_cvv_invalid));
            allValid = false;
        }

        if (!allValid) return;

        btnPay.setEnabled(false);

        Intent intent = new Intent(this, PaymentResultActivity.class);
        intent.putExtra(PaymentResultActivity.EXTRA_AMOUNT, amount);
        intent.putExtra(PaymentResultActivity.EXTRA_LAST4,
                cardNumber.substring(cardNumber.length() - 4));
        intent.putExtra(PaymentResultActivity.EXTRA_SIMULATE_FAILURE,
                cardNumber.endsWith("0000"));
        paymentResultLauncher.launch(intent);
    }

    private void showNameError(String msg) {
        tvNameError.setText(msg);
        tvNameError.setVisibility(View.VISIBLE);
    }

    private void hideNameError() {
        tvNameError.setVisibility(View.GONE);
    }

    private void showCardNumberError(String msg) {
        tvCardNumberError.setText(msg);
        tvCardNumberError.setVisibility(View.VISIBLE);
    }

    private void hideCardNumberError() {
        tvCardNumberError.setVisibility(View.GONE);
    }

    private void showExpiryError(String msg) {
        tvExpiryError.setText(msg);
        tvExpiryError.setVisibility(View.VISIBLE);
    }

    private void hideExpiryError() {
        tvExpiryError.setVisibility(View.GONE);
    }

    private void showCvvError(String msg) {
        tvCvvError.setText(msg);
        tvCvvError.setVisibility(View.VISIBLE);
    }

    private void hideCvvError() {
        tvCvvError.setVisibility(View.GONE);
    }

    private String formatPrice(double price) {
        if (price == Math.rint(price)) {
            return String.valueOf((long) price);
        }
        return String.format(Locale.US, "%.2f", price);
    }
}