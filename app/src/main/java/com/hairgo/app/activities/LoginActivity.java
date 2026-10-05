package com.hairgo.app.activities;

import android.content.Intent;
import android.os.Bundle;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.util.Patterns;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.textfield.TextInputLayout;
import com.hairgo.app.R;
import com.hairgo.app.firebase.AuthManager;

import java.util.Locale;

public class LoginActivity extends AppCompatActivity {

    private EditText etUsername;
    private EditText etPassword;
    private TextInputLayout tilUsername;
    private TextInputLayout tilPassword;
    private TextView tvLoginError;
    private Button btnLogin;
    private TextView tvForgotPassword;
    private TextView tvSignUp;

    private AuthManager authManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_login);

        authManager = new AuthManager();

        etUsername = findViewById(R.id.etUsername);
        etPassword = findViewById(R.id.etPassword);
        tilUsername = findViewById(R.id.tilUsername);
        tilPassword = findViewById(R.id.tilPassword);
        tvLoginError = findViewById(R.id.tvLoginError);
        btnLogin = findViewById(R.id.btnLogin);
        tvForgotPassword = findViewById(R.id.tvForgotPassword);
        tvSignUp = findViewById(R.id.tvSignUp);

        highlightSignUpText();

        btnLogin.setOnClickListener(v -> {

            clearErrors();

            String email = etUsername.getText().toString().trim();
            String password = etPassword.getText().toString().trim();

            if (email.isEmpty()) {

                tilUsername.setError(
                        getString(R.string.hint_email_required)
                );
                tilUsername.requestFocus();
                return;
            }

            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {

                tilUsername.setError(
                        getString(R.string.hint_email_invalid)
                );
                tilUsername.requestFocus();
                return;
            }

            if (password.isEmpty()) {

                tilPassword.setError(
                        getString(R.string.hint_password_required)
                );
                tilPassword.requestFocus();
                return;
            }

            setLoading(true);

            authManager.loginUser(
                    email,
                    password,
                    new AuthManager.AuthCallback() {

                        @Override
                        public void onSuccess(String role) {

                            setLoading(false);

                            if ("owner".equals(role)) {

                                startActivity(
                                        new Intent(
                                                LoginActivity.this,
                                                OwnerDashboardActivity.class
                                        )
                                );

                            } else if ("admin".equals(role)) {

                                startActivity(
                                        new Intent(
                                                LoginActivity.this,
                                                AdminDashboardActivity.class
                                        )
                                );

                            } else if ("client".equals(role)) {

                                startActivity(
                                        new Intent(
                                                LoginActivity.this,
                                                ClientDashboardActivity.class
                                        )
                                );

                            } else {

                                // Authentication succeeded but there is no
                                // dashboard for this role. Drop the session,
                                // otherwise the user is left signed in with no
                                // way to reach a dashboard or log out.
                                authManager.signOut();

                                showFormError(
                                        getString(R.string.login_error_unknown_role)
                                );

                                return;
                            }

                            finish();
                        }

                        @Override
                        public void onFailure(String errorMessage) {

                            setLoading(false);
                            showLoginError(errorMessage);
                        }
                    }
            );
        });

        tvForgotPassword.setOnClickListener(v ->
                startActivity(
                        new Intent(
                                LoginActivity.this,
                                ForgotPasswordActivity.class
                        )
                )
        );

        tvSignUp.setOnClickListener(v ->
                startActivity(
                        new Intent(
                                LoginActivity.this,
                                RoleSelectionActivity.class
                        )
                )
        );
    }

    private void setLoading(boolean loading) {

        btnLogin.setEnabled(!loading);
        btnLogin.setText(
                loading ? R.string.btn_logging_in : R.string.btn_login
        );
    }

    private void clearErrors() {
        tilUsername.setError(null);
        tilPassword.setError(null);
        tvLoginError.setText("");
        tvLoginError.setVisibility(View.GONE);
    }

    private void showFormError(String message) {
        tilUsername.setError(null);
        tilPassword.setError(null);
        tvLoginError.setText(message);
        tvLoginError.setVisibility(View.VISIBLE);
    }

    private void showLoginError(@Nullable String raw) {

        if (raw == null || raw.trim().isEmpty()) {
            showFormError(getString(R.string.login_error_generic));
            return;
        }

        String message = raw.toLowerCase(Locale.getDefault());

        if (message.contains("invalid credential")
                || message.contains("password is invalid")
                || message.contains("malformed or has expired")
                || message.contains("no user record")
                || message.contains("wrong password")
                || message.contains("user not found")
                || message.contains("email/password")) {

            tilPassword.setError(
                    getString(R.string.login_error_invalid_credentials)
            );
            tilPassword.requestFocus();
            return;
        }

        if (message.contains("badly formatted")
                || message.contains("invalid email")) {

            tilUsername.setError(
                    getString(R.string.hint_email_invalid)
            );
            tilUsername.requestFocus();
            return;
        }

        if (message.contains("network")
                || message.contains("timeout")) {

            showFormError(getString(R.string.login_error_network));
            return;
        }

        if (message.contains("too many")) {

            showFormError(
                    getString(R.string.login_error_too_many_attempts)
            );
            return;
        }

        if (message.contains("has been disabled")
                || message.contains("deactivated")) {

            showFormError(
                    getString(R.string.login_error_account_disabled)
            );
            return;
        }

        if (message.contains("not found in firestore")) {

            showFormError(getString(R.string.login_error_profile_not_found));
            return;
        }

        if (message.contains("role is missing")) {

            showFormError(getString(R.string.login_error_role_missing));
            return;
        }

        if (message.contains("could not retrieve")) {

            showFormError(getString(R.string.login_error_no_account));
            return;
        }

        // Catches "Could not load your user profile: <Firebase message>", which
        // carries the raw SDK exception. Never shown to the user.
        if (message.contains("user profile")) {

            showFormError(getString(R.string.login_error_generic));
            return;
        }

        showFormError(getString(R.string.login_error_generic));
    }

    private void highlightSignUpText() {

        String fullText = tvSignUp.getText().toString();
        String highlight = "Sign Up";

        int startIndex = fullText
                .toLowerCase(Locale.getDefault())
                .indexOf(
                        highlight.toLowerCase(Locale.getDefault())
                );

        if (startIndex == -1) {
            return;
        }

        SpannableString spannable =
                new SpannableString(fullText);

        spannable.setSpan(
                new ForegroundColorSpan(
                        ContextCompat.getColor(
                                this,
                                R.color.teal
                        )
                ),
                startIndex,
                startIndex + highlight.length(),
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        );

        tvSignUp.setText(spannable);
    }
}
