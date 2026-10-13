package com.hairgo.app.utils;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.hairgo.app.R;

/**
 * The single place that builds notification channels and posts notifications.
 *
 * <p>Notifications are shown through NotificationManagerCompat, which quietly
 * does nothing when the user has not granted POST_NOTIFICATIONS on Android 13+,
 * so every caller can post without checking the permission first.
 */
public final class Notifications {

    public static final String CHANNEL_REMINDERS = "hairgo_reminders";
    public static final String CHANNEL_UPDATES = "hairgo_updates";

    public static final int PERMISSION_REQUEST_CODE = 7101;

    private Notifications() {}

    /**
     * Registers the app's notification channels. Safe to call more than once:
     * creating a channel that already exists is a no-op that keeps the user's
     * own importance/behaviour choices.
     */
    public static void createChannels(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;

        NotificationManager manager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;

        NotificationChannel reminders = new NotificationChannel(
                CHANNEL_REMINDERS,
                context.getString(R.string.notification_channel_reminders),
                NotificationManager.IMPORTANCE_HIGH);
        reminders.setDescription(context.getString(R.string.notification_channel_reminders_desc));

        NotificationChannel updates = new NotificationChannel(
                CHANNEL_UPDATES,
                context.getString(R.string.notification_channel_updates),
                NotificationManager.IMPORTANCE_DEFAULT);
        updates.setDescription(context.getString(R.string.notification_channel_updates_desc));

        manager.createNotificationChannel(reminders);
        manager.createNotificationChannel(updates);
    }

    /**
     * Posts a notification that, when tapped, reopens the app.
     *
     * @param id any stable int; reusing an id replaces the previous notification.
     */
    public static void show(Context context, int id, String channelId,
                            String title, @Nullable String body) {
        show(context, id, channelId, title, body, null);
    }

    /**
     * Same as {@link #show(Context, int, String, String, String)} but also links
     * the notification to a booking, so the in-app list can open that booking when
     * the entry is tapped.
     */
    public static void show(Context context, int id, String channelId, String title,
                            @Nullable String body, @Nullable String bookingId) {
        if (context == null) return;

        // Keep an on-device copy so the bell list can show the same history.
        NotificationStore.add(context, String.valueOf(id), title,
                body == null ? "" : body, channelId, bookingId);

        PendingIntent contentIntent = appLaunchIntent(context);

        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(context, channelId)
                        .setSmallIcon(R.drawable.ic_bell)
                        .setContentTitle(title)
                        .setContentText(body)
                        .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setAutoCancel(true);

        if (contentIntent != null) builder.setContentIntent(contentIntent);

        if (hasPermission(context)) {
            NotificationManagerCompat.from(context).notify(id, builder.build());
        }
    }

    /** True when a notification is allowed to be posted on this device. */
    public static boolean hasPermission(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                    == PackageManager.PERMISSION_GRANTED;
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled();
    }

    /** Asks for POST_NOTIFICATIONS on Android 13+. A no-op on older versions. */
    public static void requestPermission(AppCompatActivity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasPermission(activity)) {
            ActivityCompat.requestPermissions(activity,
                    new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    PERMISSION_REQUEST_CODE);
        }
    }

    @Nullable
    private static PendingIntent appLaunchIntent(Context context) {
        Intent intent = context.getPackageManager()
                .getLaunchIntentForPackage(context.getPackageName());
        if (intent == null) return null;

        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        return PendingIntent.getActivity(context, 0, intent, flags);
    }
}
