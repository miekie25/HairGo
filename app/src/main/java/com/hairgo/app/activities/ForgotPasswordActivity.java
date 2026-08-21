package com.hairgo.app.activities;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.hairgo.app.R;

public class ForgotPasswordActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_forgot_password);

        // Local variables (only used in onCreate)
        ImageButton btnBack = findViewById(R.id.btnBack);
        TextInputEditText etEmail = findViewById(R.id.etEmail);
        MaterialButton btnResetPassword = findViewById(R.id.btnResetPassword);
        TextView tvBackToLogin = findViewById(R.id.tvBackToLogin);

        // Back button → finish activity
        btnBack.setOnClickListener(v -> finish());

        // Back to login text → navigate to LoginActivity
        tvBackToLogin.setOnClickListener(v -> {
            Intent intent = new Intent(ForgotPasswordActivity.this, LoginActivity.class);
            startActivity(intent);
            finish();
        });

        // Reset password button → validate and show toast (logic to be added later)
        btnResetPassword.setOnClickListener(v -> {
            String email = etEmail.getText() != null ? etEmail.getText().toString().trim() : "";

            if (email.isEmpty()) {
                etEmail.setError("Please enter your email address");
                etEmail.requestFocus();
                return;
            }

            if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                etEmail.setError("Please enter a valid email address");
                etEmail.requestFocus();
                return;
            }

            // TODO: Call Firebase Auth sendPasswordResetEmail() here
            Toast.makeText(this, "Reset link sent to " + email, Toast.LENGTH_LONG).show();
        });
    }
}