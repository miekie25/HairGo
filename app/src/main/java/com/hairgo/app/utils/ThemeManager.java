package com.hairgo.app.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;

import androidx.appcompat.app.AppCompatDelegate;

/**
 * Stores the user's light/dark choice and applies it through AppCompatDelegate.
 *
 * The whole app uses a DayNight theme, so applying the mode here is enough for
 * every activity to pick up the right (day or night) resources.
 */
public final class ThemeManager {

    private static final String PREFS_NAME = "hairgo_prefs";
    private static final String KEY_NIGHT_MODE = "night_mode";

    private ThemeManager() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /**
     * Applies the saved theme. Called once at app startup so the choice is in
     * effect before the first activity is created.
     */
    public static void applySavedTheme(Context context) {
        AppCompatDelegate.setDefaultNightMode(getSavedMode(context));
    }

    private static int getSavedMode(Context context) {
        // Until the user picks a side, keep following the system setting.
        return prefs(context).getInt(KEY_NIGHT_MODE, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
    }

    /**
     * Saves and applies the user's choice. AppCompat recreates the running
     * activities so the new theme shows immediately.
     */
    public static void setNightMode(Context context, boolean dark) {
        int mode = dark ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO;
        prefs(context).edit().putInt(KEY_NIGHT_MODE, mode).apply();
        AppCompatDelegate.setDefaultNightMode(mode);
    }

    /**
     * True when dark colours are currently showing. Used to set the switch's
     * initial position, including when the app is still following the system.
     */
    public static boolean isDarkEnabled(Context context) {
        int mode = getSavedMode(context);

        if (mode == AppCompatDelegate.MODE_NIGHT_YES) {
            return true;
        }
        if (mode == AppCompatDelegate.MODE_NIGHT_NO) {
            return false;
        }

        int uiMode = context.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK;
        return uiMode == Configuration.UI_MODE_NIGHT_YES;
    }
}
