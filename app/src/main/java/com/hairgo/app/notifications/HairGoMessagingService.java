package com.hairgo.app.notifications;

import androidx.annotation.NonNull;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import com.hairgo.app.R;
import com.hairgo.app.firebase.AuthManager;
import com.hairgo.app.utils.Notifications;

import java.util.Map;

/**
 * Receives Firebase Cloud Messaging messages and new device tokens.
 *
 * <p>The token is written to the signed-in user's profile so a backend (or the
 * Firebase Console for a demo) can address this device.
 */
public class HairGoMessagingService extends FirebaseMessagingService {

    @Override
    public void onNewToken(@NonNull String token) {
        super.onNewToken(token);
        // Only meaningful once someone is signed in; a token obtained while
        // logged out is re-read and saved the next time the app is opened.
        new AuthManager().saveFcmToken(token);
    }

    /**
     * Called when a data/notification message arrives while the app is in the
     * foreground. Background notification-type messages are shown by the system
     * using the default channel declared in the manifest, so they never reach
     * here.
     */
    @Override
    public void onMessageReceived(@NonNull RemoteMessage message) {
        super.onMessageReceived(message);

        String title = null;
        String body = null;
        String channel = Notifications.CHANNEL_UPDATES;

        if (message.getNotification() != null) {
            title = message.getNotification().getTitle();
            body = message.getNotification().getBody();
        }

        Map<String, String> data = message.getData();
        if (data.containsKey("title")) title = data.get("title");
        if (data.containsKey("body")) body = data.get("body");
        if (data.containsKey("channel")) channel = data.get("channel");
        String bookingId = data.get("bookingId");

        if (title == null || title.isEmpty()) {
            title = getString(R.string.notification_default_title);
        }

        Notifications.createChannels(this);
        Notifications.show(this, (int) System.currentTimeMillis(), channel, title, body, bookingId);
    }
}
