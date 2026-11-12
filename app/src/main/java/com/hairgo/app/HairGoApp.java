
package com.hairgo.app;

import android.app.Application;

import com.hairgo.app.firebase.AuthManager;
import com.hairgo.app.utils.Notifications;
import com.hairgo.app.utils.ThemeManager;
import com.hairgo.app.utils.LocaleHelper;

public class HairGoApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        // Force HairGo to use English.
        LocaleHelper.INSTANCE.apply(this, "en");

        ThemeManager.applySavedTheme(this);
        Notifications.createChannels(this);

        AuthManager authManager = new AuthManager();
        if (authManager.isLoggedIn()) {
            authManager.saveFcmToken();
        }
    }
}
