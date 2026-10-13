package com.hairgo.app;

import android.app.Application;

import com.hairgo.app.firebase.AuthManager;
import com.hairgo.app.utils.Notifications;
import com.hairgo.app.utils.ThemeManager;

public class HairGoApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        // Apply the saved light/dark choice before any activity is created.
        ThemeManager.applySavedTheme(this);
        // Register the notification channels once, up front.
        Notifications.createChannels(this);
        // Keep this device's push token current for a returning signed-in user.
        AuthManager authManager = new AuthManager();
        if (authManager.isLoggedIn()) {
            authManager.saveFcmToken();
        }
    }
}
