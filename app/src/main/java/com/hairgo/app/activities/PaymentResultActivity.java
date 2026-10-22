package com.hairgo.app.activities;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatButton;

import com.hairgo.app.R;

import java.util.Locale;

public class PaymentResultActivity extends AppCompatActivity {

    public static final String EXTRA_AMOUNT = "amount";
    public static final String EXTRA_LAST4 = "last4";
    public static final String EXTRA_SIMULATE_FAILURE = "simulateFailure";

    private static final long PROCESSING_DELAY_MS = 1500L;

    private final Handler handler = new Handler(Looper.getMainLooper());

    private View progressGroup;
    private View resultGroup;
    private ImageView ivResultIcon;
    private TextView tvResultTitle;
    private TextView tvResultMessage;
    private TextView tvResultAmount;
    private AppCompatButton btnResultAction;

    private boolean processing = true;
    private boolean success;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_payment_result);

        ImageButton backBtn = findViewById(R.id.header).findViewById(R.id.btnBack);
        backBtn.setOnClickListener(v -> handleBack());

        TextView headerTitle = findViewById(R.id.header).findViewById(R.id.tvHeaderTitle);
        headerTitle.setText(R.string.title_payment_result);

        progressGroup = findViewById(R.id.progressGroup);
        resultGroup = findViewById(R.id.resultGroup);
        ivResultIcon = findViewById(R.id.ivResultIcon);
        tvResultTitle = findViewById(R.id.tvResultTitle);
        tvResultMessage = findViewById(R.id.tvResultMessage);
        tvResultAmount = findViewById(R.id.tvResultAmount);
        btnResultAction = findViewById(R.id.btnResultAction);

        double amount = getIntent().getDoubleExtra(EXTRA_AMOUNT, 0.0);
        String last4 = getIntent().getStringExtra(EXTRA_LAST4);
        boolean simulateFailure = getIntent().getBooleanExtra(EXTRA_SIMULATE_FAILURE, false);

        TextView tvProcessingAmount = findViewById(R.id.tvProcessingAmount);
        String amountText = getString(R.string.payment_processing_amount,
                getString(R.string.service_price_rand, formatPrice(amount)));
        tvProcessingAmount.setText(amountText);

        String cardText = last4 == null || last4.isEmpty()
                ? ""
                : getString(R.string.card_ending, last4);
        tvResultAmount.setText(cardText.isEmpty()
                ? amountText
                : amountText + "\n" + cardText);

        btnResultAction.setOnClickListener(v -> {
            setResult(success ? RESULT_OK : RESULT_CANCELED);
            finish();
        });

        handler.postDelayed(() -> {
            if (simulateFailure) {
                showFailure();
            } else {
                showSuccess();
            }
        }, PROCESSING_DELAY_MS);
    }

    private void showSuccess() {
        processing = false;
        success = true;

        progressGroup.setVisibility(View.GONE);
        resultGroup.setVisibility(View.VISIBLE);

        ivResultIcon.setImageResource(R.drawable.ic_check);
        ivResultIcon.setBackgroundResource(R.drawable.bg_circle_teal);
        tvResultTitle.setText(R.string.payment_success_title);
        tvResultMessage.setText(R.string.payment_success_message);
        btnResultAction.setText(R.string.continue_label);
    }

    private void showFailure() {
        processing = false;
        success = false;

        progressGroup.setVisibility(View.GONE);
        resultGroup.setVisibility(View.VISIBLE);

        ivResultIcon.setImageResource(R.drawable.ic_warning);
        ivResultIcon.setBackgroundResource(R.drawable.bg_circle_coral);
        tvResultTitle.setText(R.string.payment_failed_title);
        tvResultMessage.setText(R.string.payment_failed_message);
        btnResultAction.setText(R.string.btn_try_again);
    }

    private void handleBack() {
        if (processing) {
            return;
        }

        setResult(success ? RESULT_OK : RESULT_CANCELED);
        finish();
    }

    @Override
    public void onBackPressed() {
        handleBack();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    private String formatPrice(double price) {
        if (price == Math.rint(price)) {
            return String.valueOf((long) price);
        }
        return String.format(Locale.US, "%.2f", price);
    }
}
