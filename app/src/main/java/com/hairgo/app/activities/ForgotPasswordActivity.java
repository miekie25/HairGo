package com.hairgo.app.activities;

import android.content.Intent;
import android.os.Bundle;
import android.util.Patterns;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.hairgo.app.R;

import java.util.Locale;

public class ForgotPasswordActivity extends AppCompatActivity {

    private TextInputLayout tilEmail;
    private TextInputEditText etEmail;
    private MaterialButton btnResetPassword;
    private TextView tvResetMessage;

    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_forgot_password);

        mAuth = FirebaseAuth.getInstance();

        ImageButton btnBack = findViewById(R.id.btnBack);
        tilEmail = findViewById(R.id.tilEmail);
        etEmail = findViewById(R.id.etEmail);
        btnResetPassword = findViewById(R.id.btnResetPassword);
        TextView tvBackToLogin = findViewById(R.id.tvBackToLogin);
        tvResetMessage = findViewById(R.id.tvResetMessage);

        btnBack.setOnClickListener(v -> finish());

        tvBackToLogin.setOnClickListener(v -> {
            Intent intent = new Intent(ForgotPasswordActivity.this, LoginActivity.class);
            startActivity(intent);
            finish();
        });

        btnResetPassword.setOnClickListener(v -> sendResetLink());
    }

    private void sendResetLink() {

        clearErrors();

        String email = etEmail.getText() != null
                ? etEmail.getText().toString().trim()
                : "";

        if (email.isEmpty()) {

            tilEmail.setError(getString(R.string.hint_email_required));
            tilEmail.requestFocus();
            return;
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {

            tilEmail.setError(getString(R.string.hint_email_invalid));
            tilEmail.requestFocus();
            return;
        }

        setLoading(true);

        mAuth.sendPasswordResetEmail(email)
                .addOnCompleteListener(task -> {

                    setLoading(false);

                    String error = task.getException() == null
                            ? ""
                            : task.getException().getMessage();

                    // Account enumeration guard: an unregistered address reports
                    // the same message as a successful send, so this form cannot
                    // be used to discover which emails exist in the system.
                    if (error.isEmpty() || isMissingAccount(error)) {
                        showSuccess();
                        return;
                    }

                    showError(mapError(error));
                });
    }

    private boolean isMissingAccount(String error) {

        String message = error.toLowerCase(Locale.getDefault());

        return message.contains("no user record")
                || message.contains("user not found");
    }

    @Nullable
    private String mapError(String error) {

        String message = error.toLowerCase(Locale.getDefault());

        if (message.contains("badly formatted")
                || message.contains("invalid email")) {
            return getString(R.string.hint_email_invalid);
        }

        if (message.contains("network")
                || message.contains("timeout")) {
            return getString(R.string.login_error_network);
        }

        if (message.contains("too many")) {
            return getString(R.string.login_error_too_many_attempts);
        }

        // Firebase Console: Email/Password provider disabled, or a custom
        // action-code handler URL that no longer resolves.
        if (message.contains("operation not allowed")
                || message.contains("not enabled")) {
            return getString(R.string.forgot_error_not_configured);
        }

        return getString(R.string.login_error_generic);
    }

    private void setLoading(boolean loading) {
        btnResetPassword.setEnabled(!loading);
        btnResetPassword.setText(
                loading ? R.string.btn_sending : R.string.send_reset_link
        );
    }

    private void clearErrors() {
        tilEmail.setError(null);
        tvResetMessage.setText("");
        tvResetMessage.setVisibility(View.GONE);
    }

    private void showError(String message) {
        tilEmail.setError(null);
        tvResetMessage.setTextColor(ContextCompat.getColor(this, R.color.error));
        tvResetMessage.setText(message);
        tvResetMessage.setVisibility(View.VISIBLE);
    }

    private void showSuccess() {
        tilEmail.setError(null);
        tvResetMessage.setTextColor(ContextCompat.getColor(this, R.color.teal));
        tvResetMessage.setText(getString(R.string.forgot_success));
        tvResetMessage.setVisibility(View.VISIBLE);
    }
}