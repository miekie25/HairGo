package com.hairgo.app.utils;

import android.content.Context;
import android.content.SharedPreferences;

import com.hairgo.app.R;
import com.hairgo.app.models.Booking;

import java.util.List;
import java.util.Locale;

/**
 * Notices when a booking's status changes between app loads and posts a
 * notification for it.
 *
 * <p>Without a push server this is how status changes surface: the last status
 * seen on this device is remembered, so the next time the bookings list loads a
 * change (for example pending to confirmed) can be announced. The very first
 * time a booking is seen it is only remembered, never announced.
 */
public final class BookingStatusNotifier {

    private static final String PREFS = "hairgo_booking_status";

    private BookingStatusNotifier() {}

    public static void notifyChanges(Context context, List<Booking> bookings) {
        if (context == null || bookings == null || bookings.isEmpty()) return;

        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();

        for (Booking booking : bookings) {
            String id = booking.getBookingId();
            String status = BookingSections.effectiveStatus(booking);
            if (id == null || status == null) continue;

            String previous = prefs.getString(id, null);
            if (previous != null && !previous.equals(status) && isNotifiable(status)) {
                Notifications.show(context, (id + ":status").hashCode(),
                        Notifications.CHANNEL_UPDATES,
                        context.getString(R.string.status_change_title),
                        context.getString(R.string.status_change_body,
                                booking.getSalonName(), capitalize(status)),
                        id);
            }
            editor.putString(id, status);
        }

        editor.apply();
    }

    private static boolean isNotifiable(String status) {
        return "confirmed".equals(status)
                || "cancelled".equals(status)
                || "completed".equals(status)
                || BookingSections.STATUS_EXPIRED.equals(status);
    }

    private static String capitalize(String text) {
        if (text == null || text.isEmpty()) return text;
        return text.substring(0, 1).toUpperCase(Locale.getDefault()) + text.substring(1);
    }
}
