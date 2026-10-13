package com.hairgo.app.notifications;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.hairgo.app.firebase.BookingManager;
import com.hairgo.app.utils.Notifications;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Posts an appointment reminder, but only if the booking is still going ahead.
 *
 * <p>The booking is re-read from Firestore first, so a booking that has since
 * been cancelled, completed or expired does not produce a stale reminder. If
 * the read fails (for example with no network) the reminder is dropped rather
 * than shown on a guess.
 */
public class ReminderWorker extends Worker {

    public static final String KEY_BOOKING_ID = "bookingId";
    public static final String KEY_TITLE = "title";
    public static final String KEY_BODY = "body";

    public ReminderWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        String bookingId = getInputData().getString(KEY_BOOKING_ID);
        String title = getInputData().getString(KEY_TITLE);
        String body = getInputData().getString(KEY_BODY);
        if (bookingId == null || bookingId.isEmpty()) return Result.success();

        if (!isStillActive(bookingId)) return Result.success();

        Context context = getApplicationContext();
        Notifications.createChannels(context);
        Notifications.show(context, bookingId.hashCode(),
                Notifications.CHANNEL_REMINDERS, title, body, bookingId);
        return Result.success();
    }

    /**
     * Reads the booking and waits for the asynchronous Firestore call. A
     * pending or confirmed booking is worth reminding about; anything else
     * (cancelled, completed, expired, or a failed read) is not.
     */
    private boolean isStillActive(String bookingId) {
        final CountDownLatch latch = new CountDownLatch(1);
        final boolean[] active = {false};

        new BookingManager().getBookingById(bookingId, new BookingManager.BookingDataCallback() {
            @Override
            public void onSuccess(Map<String, Object> booking) {
                Object status = booking.get("status");
                String value = status == null ? "" : status.toString();
                active[0] = "pending".equals(value) || "confirmed".equals(value);
                latch.countDown();
            }

            @Override
            public void onFailure(String errorMessage) {
                latch.countDown();
            }
        });

        try {
            if (!latch.await(20, TimeUnit.SECONDS)) return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
        return active[0];
    }
}
