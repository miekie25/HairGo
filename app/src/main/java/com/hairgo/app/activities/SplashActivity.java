package com.hairgo.app.activities;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.splashscreen.SplashScreen;

import com.hairgo.app.firebase.AuthManager;

public class SplashActivity extends AppCompatActivity {

    private static final long SPLASH_DELAY_MS = 2700;

    private boolean isReady = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        SplashScreen splashScreen = SplashScreen.installSplashScreen(this);
        super.onCreate(savedInstanceState);
        splashScreen.setKeepOnScreenCondition(() -> !isReady);

        new Handler(Looper.getMainLooper()).postDelayed(this::route, SPLASH_DELAY_MS);
    }

    /**
     * Decides where to go once the splash finishes. If a session is still alive
     * (for example after tapping a notification) the user skips Welcome/Login and
     * goes straight to the dashboard for their role. Otherwise they see Welcome
     * as before. Any failure falls back to Welcome so the app is never stuck.
     */
    private void route() {
        AuthManager authManager = new AuthManager();

        if (!authManager.isLoggedIn()) {
            finishTo(WelcomeActivity.class);
            return;
        }

        authManager.loadCurrentUserProfile(new AuthManager.ProfileCallback() {
            @Override
            public void onSuccess(String name, String role) {
                if ("client".equals(role)) {
                    finishTo(ClientDashboardActivity.class);
                } else if ("owner".equals(role)) {
                    finishTo(OwnerDashboardActivity.class);
                } else if ("admin".equals(role)) {
                    finishTo(AdminDashboardActivity.class);
                } else {
                    // Signed in but no dashboard for this role: drop the session
                    // rather than trapping the user with nowhere to go.
                    authManager.signOut();
                    finishTo(WelcomeActivity.class);
                }
            }

            @Override
            public void onFailure(String errorMessage) {
                finishTo(WelcomeActivity.class);
            }
        });
    }

    private void finishTo(Class<?> destination) {
        isReady = true;
        startActivity(new Intent(SplashActivity.this, destination));
        // Remove splash from the back stack so back does not return to it.
        finish();
    }
}
