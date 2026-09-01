package com.hairgo.app.activities;

import android.content.Intent;
import android.os.Bundle;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.hairgo.app.R;
import com.hairgo.app.firebase.AuthManager;

import java.util.Locale;

public class LoginActivity extends AppCompatActivity {

    private EditText etUsername;
    private EditText etPassword;
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
        btnLogin = findViewById(R.id.btnLogin);
        tvForgotPassword = findViewById(R.id.tvForgotPassword);
        tvSignUp = findViewById(R.id.tvSignUp);

        highlightSignUpText();

        btnLogin.setOnClickListener(v -> {
            String email = etUsername.getText().toString().trim();
            String password = etPassword.getText().toString().trim();

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(LoginActivity.this,
                        "Please fill in all fields", Toast.LENGTH_SHORT).show();
                return;
            }

            authManager.loginUser(email, password, new AuthManager.AuthCallback() {
                @Override
                public void onSuccess() {
                    startActivity(new Intent(LoginActivity.this, ClientDashboardActivity.class));
                    finish();
                }

                @Override
                public void onFailure(String errorMessage) {
                    Toast.makeText(LoginActivity.this, errorMessage, Toast.LENGTH_SHORT).show();
                }
            });
        });

        tvForgotPassword.setOnClickListener(v ->
                startActivity(new Intent(LoginActivity.this, ForgotPasswordActivity.class))
        );
    }

    private void highlightSignUpText() {
        String fullText = tvSignUp.getText().toString();
        String highlight = "Sign Up";

        int startIndex = fullText.toLowerCase(Locale.getDefault())
                .indexOf(highlight.toLowerCase(Locale.getDefault()));

        if (startIndex == -1) return;

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