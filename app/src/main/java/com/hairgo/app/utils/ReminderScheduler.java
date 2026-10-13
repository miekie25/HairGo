package com.hairgo.app.utils;

import android.content.Context;

import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import com.hairgo.app.R;
import com.hairgo.app.notifications.ReminderWorker;

import java.util.Date;
import java.util.concurrent.TimeUnit;

/**
 * Schedules (and cancels) the "1 day before" and "1 hour before" appointment
 * reminders as background work.
 *
 * <p>WorkManager is used rather than AlarmManager so that no exact-alarm
 * permission is needed and so the reminders are automatically restored after a
 * reboot. Each reminder is stored under a unique name derived from the booking
 * id, so re-scheduling simply replaces the previous one.
 */
public final class ReminderScheduler {

    public static final long ONE_HOUR_MS = 60L * 60L * 1000L;
    public static final long ONE_DAY_MS = 24L * ONE_HOUR_MS;

    private static final String SUFFIX_DAY = "_1d";
    private static final String SUFFIX_HOUR = "_1h";

    private ReminderScheduler() {}

    /**
     * Schedules both reminders for a booking. A reminder whose fire time is
     * already in the past (for example a booking made for later today) is
     * skipped.
     */
    public static void schedule(Context context, String bookingId, String salonName, Date dateTime) {
        if (context == null || bookingId == null || bookingId.isEmpty() || dateTime == null) return;

        long now = System.currentTimeMillis();
        long appointment = dateTime.getTime();

        scheduleOne(context, bookingId, SUFFIX_DAY,
                appointment - ONE_DAY_MS - now,
                context.getString(R.string.reminder_body, safe(salonName),
                        context.getString(R.string.reminder_when_tomorrow)));

        scheduleOne(context, bookingId, SUFFIX_HOUR,
                appointment - ONE_HOUR_MS - now,
                context.getString(R.string.reminder_body, safe(salonName),
                        context.getString(R.string.reminder_when_hour)));
    }

    /** Cancels both reminders for a booking. */
    public static void cancel(Context context, String bookingId) {
        if (context == null || bookingId == null || bookingId.isEmpty()) return;

        WorkManager manager = WorkManager.getInstance(context);
        manager.cancelUniqueWork(bookingId + SUFFIX_DAY);
        manager.cancelUniqueWork(bookingId + SUFFIX_HOUR);
    }

    private static void scheduleOne(Context context, String bookingId, String suffix,
                                    long delayMs, String body) {
        if (delayMs <= 0L) return;

        Data input = new Data.Builder()
                .putString(ReminderWorker.KEY_BOOKING_ID, bookingId)
                .putString(ReminderWorker.KEY_TITLE, context.getString(R.string.reminder_title))
                .putString(ReminderWorker.KEY_BODY, body)
                .build();

        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(ReminderWorker.class)
                .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
                .setInputData(input)
                .build();

        WorkManager.getInstance(context)
                .enqueueUniqueWork(bookingId + suffix, ExistingWorkPolicy.REPLACE, request);
    }

    private static String safe(String value) {
        return value == null || value.isEmpty() ? "" : value;
    }
}
