
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
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;


import com.google.android.material.textfield.TextInputLayout;


import com.hairgo.app.R;
import com.hairgo.app.firebase.AuthManager;
import com.hairgo.app.utils.BiometricCredentialStore;
import com.hairgo.app.utils.WarningDialog;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executor;

public class LoginActivity extends AppCompatActivity {

    private static final int BIOMETRIC_AUTHENTICATORS =
            BiometricManager.Authenticators.BIOMETRIC_STRONG
                    | BiometricManager.Authenticators.BIOMETRIC_WEAK;

    private EditText etUsername;
    private EditText etPassword;
    private TextInputLayout tilUsername;
    private TextInputLayout tilPassword;
    private TextView tvLoginError;
    private TextView tvResendEmail;
    private Button btnLogin;
    private LinearLayout layoutBiometricContainer;
    private TextView tvBiometricLabel;
    private ImageView btnBiometricLogin;
    private TextView tvForgotPassword;
    private TextView tvSignUp;

    private AuthManager authManager;
    private BiometricPrompt biometricPrompt;
    private BiometricPrompt.PromptInfo promptInfo;


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
        tvResendEmail = findViewById(R.id.tvResendEmail);
        btnLogin = findViewById(R.id.btnLogin);
        layoutBiometricContainer = findViewById(R.id.layoutBiometricContainer);
        tvBiometricLabel = findViewById(R.id.tvBiometricLabel);
        btnBiometricLogin = findViewById(R.id.btnBiometricLogin);
        tvForgotPassword = findViewById(R.id.tvForgotPassword);
        tvSignUp = findViewById(R.id.tvSignUp);

        highlightSignUpText();
        setupBiometricLogin();

        btnLogin.setOnClickListener(v -> {
            clearErrors();

            String email = etUsername.getText().toString().trim();
            String password = etPassword.getText().toString();

            if (email.isEmpty()) {
                tilUsername.setError(getString(R.string.hint_email_required));
                tilUsername.requestFocus();
                return;
            }

            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                tilUsername.setError(getString(R.string.hint_email_invalid));
                tilUsername.requestFocus();
                return;
            }

            if (password.isEmpty()) {
                tilPassword.setError(getString(R.string.hint_password_required));
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
                            WarningDialog.showIfUnseen(
                                    LoginActivity.this,
                                    () -> routeToDashboard(role)
                            );
                        }

                        @Override
                        public void onFailure(String errorMessage) {
                            setLoading(false);
                            showLoginError(errorMessage);
                        }
                    }
            );
        });

        tvResendEmail.setOnClickListener(v -> resendVerificationEmail());

        tvForgotPassword.setOnClickListener(v ->
                startActivity(new Intent(LoginActivity.this, ForgotPasswordActivity.class))
        );

        tvSignUp.setOnClickListener(v ->
                startActivity(new Intent(LoginActivity.this, RoleSelectionActivity.class))
        );
    }

    @Override
    protected void onResume() {
        super.onResume();
        checkBiometricAvailability();
    }

    private void setupBiometricLogin() {
        Executor executor = ContextCompat.getMainExecutor(this);

        biometricPrompt = new BiometricPrompt(this, executor, new BiometricPrompt.AuthenticationCallback() {
            @Override
            public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                super.onAuthenticationSucceeded(result);
                if (isFinishing() || isDestroyed()) return;
                chooseStoredAccountThenLogin();
            }

            @Override
            public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                super.onAuthenticationError(errorCode, errString);
            }

            @Override
            public void onAuthenticationFailed() {
                super.onAuthenticationFailed();
                Toast.makeText(LoginActivity.this, "Fingerprint not recognized. Try again.", Toast.LENGTH_SHORT).show();
            }
        });

        promptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setTitle("HairGo Fingerprint Sign-In")
                .setSubtitle("Touch the fingerprint sensor to log in")
                .setAllowedAuthenticators(BIOMETRIC_AUTHENTICATORS)
                .setNegativeButtonText("Use Password")
                .build();
    }

    private void chooseStoredAccountThenLogin() {
        List<String> emails = linkedEmails();

        if (emails.isEmpty()) {
            Toast.makeText(this, "No fingerprint login is linked on this phone.", Toast.LENGTH_LONG).show();
            return;
        }

        if (emails.size() == 1) {
            loginWithStoredAccount(emails.get(0));
            return;
        }

        String[] items = emails.toArray(new String[0]);

        new AlertDialog.Builder(this)
                .setTitle("Choose an account")
                .setItems(items, (dialog, which) -> loginWithStoredAccount(items[which]))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void loginWithStoredAccount(String email) {
        if (email == null || email.trim().isEmpty()) {
            Toast.makeText(this, "No fingerprint login is linked on this phone.", Toast.LENGTH_LONG).show();
            return;
        }

        String password;

        try {
            password = BiometricCredentialStore.INSTANCE.password(this, email);
        } catch (Exception e) {
            password = null;
        }

        if (password == null || password.isEmpty()) {
            Toast.makeText(this, "No saved password for " + email, Toast.LENGTH_LONG).show();
            return;
        }

        etUsername.setText(email);
        clearErrors();
        setLoading(true);

        authManager.loginUser(email, password, new AuthManager.AuthCallback() {
            @Override
            public void onSuccess(String role) {
                setLoading(false);
                WarningDialog.showIfUnseen(
                        LoginActivity.this,
                        () -> routeToDashboard(role)
                );
            }

            @Override
            public void onFailure(String errorMessage) {
                setLoading(false);
                showLoginError("Fingerprint login failed: " + errorMessage);
            }
        });
    }
    private List<String> linkedEmails() {
        try {
            List<String> emails = BiometricCredentialStore.INSTANCE.emails(this);

            if (emails == null || emails.isEmpty()) {
                return new ArrayList<>();
            }

            return new ArrayList<>(emails);

        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private void checkBiometricAvailability() {
        if (layoutBiometricContainer == null) return;

        layoutBiometricContainer.setVisibility(View.VISIBLE);

        int canAuth = BiometricManager.from(this)
                .canAuthenticate(BIOMETRIC_AUTHENTICATORS);

        if (canAuth != BiometricManager.BIOMETRIC_SUCCESS) {
            tvBiometricLabel.setText("Unable to work on your device");
            btnBiometricLogin.setAlpha(0.4f);
            btnBiometricLogin.setEnabled(false);
            btnBiometricLogin.setOnClickListener(null);
            return;
        }

        tvBiometricLabel.setText("Sign in with fingerprint");

        btnBiometricLogin.setAlpha(1f);
        btnBiometricLogin.setEnabled(true);

        btnBiometricLogin.setOnClickListener(v ->
                biometricPrompt.authenticate(promptInfo)
        );
    }
    private void routeToDashboard(String role) {
        if ("owner".equals(role)) {
            startActivity(new Intent(LoginActivity.this, OwnerDashboardActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));

        } else if ("admin".equals(role)) {
            startActivity(new Intent(LoginActivity.this, AdminDashboardActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));

        } else if ("client".equals(role)) {
            startActivity(new Intent(LoginActivity.this, ClientDashboardActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));

        } else {
            authManager.signOut();
            showFormError(getString(R.string.login_error_unknown_role));
            return;
        }

        finish();
    }

    private void setLoading(boolean loading) {
        btnLogin.setEnabled(!loading);
        btnLogin.setText(loading ? R.string.btn_logging_in : R.string.btn_login);
    }

    private void resendVerificationEmail() {
        tvResendEmail.setEnabled(false);
        tvResendEmail.setText("Sending...");

        authManager.sendVerificationEmail(new AuthManager.OperationCallback() {
            @Override
            public void onSuccess() {
                restoreResendLink();
                Toast.makeText(LoginActivity.this, "Verification email sent. Check inbox and spam folder.", Toast.LENGTH_LONG).show();
            }

            @Override
            public void onFailure(String errorMessage) {
                restoreResendLink();
                Toast.makeText(LoginActivity.this, mapVerificationError(errorMessage), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void restoreResendLink() {
        tvResendEmail.setEnabled(true);
        tvResendEmail.setText("Resend verification email");
    }

    private String mapVerificationError(String rawMessage) {
        String message = rawMessage == null ? "" : rawMessage.toLowerCase(Locale.getDefault());

        if (message.contains("network") || message.contains("timeout")) {
            return getString(R.string.login_error_network);
        }

        if (message.contains("too many")) {
            return getString(R.string.login_error_too_many_attempts);
        }

        if (message.contains("not signed in")) {
            return "Please log in again, then tap Resend verification email.";
        }

        if (message.contains("no email address")) {
            return "This account has no email address to send to.";
        }

        if (message.contains("operation not allowed") || message.contains("not enabled")) {
            return "Email verification is not available right now. Please contact support.";
        }

        return getString(R.string.login_error_generic);
    }

    private void clearErrors() {
        tilUsername.setError(null);
        tilPassword.setError(null);
        tvLoginError.setText("");
        tvLoginError.setVisibility(View.GONE);
        tvResendEmail.setVisibility(View.GONE);
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

        String rawText = raw;
        String prefix = "Fingerprint login failed: ";

        if (rawText.startsWith(prefix)) {
            rawText = rawText.substring(prefix.length());
        }

        if (rawText.startsWith(AuthManager.EMAIL_NOT_VERIFIED)) {
            showFormError(
                    "Please verify your email address before logging in. "
                            + "Check your inbox and spam folder, then log in again."
            );

            tvResendEmail.setVisibility(View.VISIBLE);
            return;
        }

        String message = rawText.toLowerCase(Locale.getDefault());

        if (message.contains("invalid credential")
                || message.contains("password is invalid")
                || message.contains("malformed or has expired")
                || message.contains("no user record")
                || message.contains("wrong password")
                || message.contains("user not found")
                || message.contains("email/password")) {

            tilPassword.setError(getString(R.string.login_error_invalid_credentials));
            tilPassword.requestFocus();
            return;
        }

        if (message.contains("badly formatted") || message.contains("invalid email")) {
            tilUsername.setError(getString(R.string.hint_email_invalid));
            tilUsername.requestFocus();
            return;
        }

        if (message.contains("network") || message.contains("timeout")) {
            showFormError(getString(R.string.login_error_network));
            return;
        }

        if (message.contains("too many")) {
            showFormError(getString(R.string.login_error_too_many_attempts));
            return;
        }

        if (message.contains("suspended")) {
            showFormError(getString(R.string.login_error_account_suspended));
            return;
        }

        if (message.contains("has been disabled") || message.contains("deactivated")) {
            showFormError(getString(R.string.login_error_account_disabled));
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

        showFormError(getString(R.string.login_error_generic));
    }

    private void highlightSignUpText() {
        String fullText = tvSignUp.getText().toString();
        String highlight = "Sign Up";

        int startIndex = fullText
                .toLowerCase(Locale.getDefault())
                .indexOf(highlight.toLowerCase(Locale.getDefault()));

        if (startIndex == -1) {
            return;
        }

        SpannableString spannable = new SpannableString(fullText);

        spannable.setSpan(
                new ForegroundColorSpan(ContextCompat.getColor(this, R.color.teal)),
                startIndex,
                startIndex + highlight.length(),
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        );

        tvSignUp.setText(spannable);
    }
}
