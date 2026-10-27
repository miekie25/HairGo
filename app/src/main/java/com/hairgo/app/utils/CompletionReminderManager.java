package com.hairgo.app.utils;
import android.content.Context;
import android.content.SharedPreferences;

import com.hairgo.app.models.OwnerBooking;

import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

public class CompletionReminderManager {
    private static final String PREFS_NAME = "completion_reminders_prefs";
    private static final String KEY_DEFERRED_PREFIX = "deferred_until_";
    private static final String KEY_SHOWN_PREFIX = "last_shown_";

    private static CompletionReminderManager instance;

    private final LinkedList<String> queue = new LinkedList<String>();
    private final Set<String> queuedIds = new HashSet<String>();
    private String currentlyShowingId = null;
    private long deferDelayNotYetMs = 10 * 60 * 1000L;
    private long deferDelayRemindLaterMs = 15 * 60 * 1000L;

    private CompletionReminderManager() {
    }

    public static synchronized CompletionReminderManager getInstance() {
        if (instance == null) {
            instance = new CompletionReminderManager();
        }
        return instance;
    }

    public void setDeferDelays(long notYetMs, long remindLaterMs) {
        if (notYetMs > 0) {
            deferDelayNotYetMs = notYetMs;
        }
        if (remindLaterMs > 0) {
            deferDelayRemindLaterMs = remindLaterMs;
        }
    }

    public long getDeferDelayNotYetMs() {
        return deferDelayNotYetMs;
    }

    public long getDeferDelayRemindLaterMs() {
        return deferDelayRemindLaterMs;
    }

    public boolean isCurrentlyShowing(String bookingId) {
        return bookingId != null && bookingId.equals(currentlyShowingId);
    }

    public void setCurrentlyShowing(String bookingId) {
        currentlyShowingId = bookingId;
    }

    public void clearCurrentlyShowing(String bookingId) {
        if (bookingId != null && bookingId.equals(currentlyShowingId)) {
            currentlyShowingId = null;
        } else if (bookingId == null) {
            currentlyShowingId = null;
        }
    }

    public void enqueueIfEligible(Context context, OwnerBooking booking) {
        if (context == null || booking == null) {
            return;
        }
        if (booking.getBookingId() == null || booking.getBookingId().isEmpty()) {
            return;
        }
        if (isCurrentlyShowing(booking.getBookingId())) {
            return;
        }
        if (queuedIds.contains(booking.getBookingId())) {
            return;
        }
        if ("completed".equalsIgnoreCase(booking.getStatus())) {
            return;
        }
        if ("cancelled".equalsIgnoreCase(booking.getStatus())) {
            return;
        }
        if (booking.isCancelledByClient()) {
            return;
        }
        if (!"confirmed".equalsIgnoreCase(booking.getStatus())) {
            return;
        }
        long now = System.currentTimeMillis();
        if (booking.getDateTime() != null && booking.getDateTime().getTime() > now) {
            return;
        }
        if (!booking.isOverdueForCompletion(now)) {
            return;
        }
        if (isDeferred(context, booking.getBookingId(), now)) {
            return;
        }
        queue.addLast(booking.getBookingId());
        queuedIds.add(booking.getBookingId());
    }

    public void enqueueBookings(Context context, List<OwnerBooking> bookings) {
        if (context == null || bookings == null) {
            return;
        }
        for (OwnerBooking booking : bookings) {
            enqueueIfEligible(context, booking);
        }
    }

    public String peekNext() {
        while (!queue.isEmpty()) {
            String id = queue.peekFirst();
            if (id == null) {
                queue.pollFirst();
                queuedIds.remove(id);
                continue;
            }
            if (queuedIds.contains(id)) {
                return id;
            }
            queue.pollFirst();
        }
        return null;
    }

    public String pollNext() {
        while (!queue.isEmpty()) {
            String id = queue.pollFirst();
            if (id == null) {
                continue;
            }
            queuedIds.remove(id);
            return id;
        }
        return null;
    }

    public void remove(String bookingId) {
        if (bookingId == null) {
            return;
        }
        queuedIds.remove(bookingId);
        Iterator<String> it = queue.iterator();
        while (it.hasNext()) {
            String id = it.next();
            if (bookingId.equals(id)) {
                it.remove();
                break;
            }
        }
    }

    public void clear() {
        queue.clear();
        queuedIds.clear();
        currentlyShowingId = null;
    }

    public void defer(Context context, String bookingId, long delayMs) {
        if (context == null || bookingId == null) {
            return;
        }
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        long until = System.currentTimeMillis() + (delayMs > 0 ? delayMs : deferDelayRemindLaterMs);
        prefs.edit().putLong(KEY_DEFERRED_PREFIX + bookingId, until).apply();
        remove(bookingId);
    }

    public void deferNotYet(Context context, String bookingId) {
        defer(context, bookingId, deferDelayNotYetMs);
    }

    public void deferRemindLater(Context context, String bookingId) {
        defer(context, bookingId, deferDelayRemindLaterMs);
    }

    public boolean isDeferred(Context context, String bookingId, long now) {
        if (context == null || bookingId == null) {
            return false;
        }
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        long until = prefs.getLong(KEY_DEFERRED_PREFIX + bookingId, 0L);
        if (until <= 0) {
            return false;
        }
        if (until > now) {
            return true;
        }
        prefs.edit().remove(KEY_DEFERRED_PREFIX + bookingId).apply();
        return false;
    }

    public void markShown(Context context, String bookingId) {
        if (context == null || bookingId == null || bookingId.isEmpty()) {
            return;
        }
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putLong(KEY_SHOWN_PREFIX + bookingId, System.currentTimeMillis()).apply();
    }
}
