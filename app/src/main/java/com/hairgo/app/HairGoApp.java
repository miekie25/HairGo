package com.hairgo.app;

import android.app.Application;

import com.hairgo.app.utils.ThemeManager;

public class HairGoApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        // Apply the saved light/dark choice before any activity is created.
        ThemeManager.applySavedTheme(this);
    }
}
