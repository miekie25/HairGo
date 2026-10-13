package com.hairgo.app.utils;

import android.content.Context;
import android.content.SharedPreferences;

import com.hairgo.app.models.AppNotification;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Keeps a small on-device history of the notifications the app has posted, so the
 * bell icon has something to show. Stored as JSON in SharedPreferences: no database
 * or extra dependency, and it works offline. Per-device only.
 */
public final class NotificationStore {

    private static final String PREFS = "hairgo_notifications";
    private static final String KEY_ITEMS = "items";
    private static final String KEY_LAST_SEEN = "last_seen";
    private static final int MAX_ITEMS = 50;

    private NotificationStore() {}

    /** Prepends a notification to the history, keeping the newest entry first. */
    public static synchronized void add(Context context, String id, String title,
                                        String body, String channel, String bookingId) {
        if (context == null) return;

        List<AppNotification> items = getAll(context);
        items.add(0, new AppNotification(id, title, body,
                System.currentTimeMillis(), channel, bookingId));

        while (items.size() > MAX_ITEMS) {
            items.remove(items.size() - 1);
        }
        save(context, items);
    }

    /** Returns the stored history, newest first. Never null. */
    public static synchronized List<AppNotification> getAll(Context context) {
        List<AppNotification> items = new ArrayList<>();
        if (context == null) return items;

        String raw = prefs(context).getString(KEY_ITEMS, null);
        if (raw == null) return items;

        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                JSONObject object = array.getJSONObject(i);
                items.add(new AppNotification(
                        object.optString("id"),
                        object.optString("title"),
                        object.optString("body"),
                        object.optLong("time"),
                        object.optString("channel"),
                        object.optString("bookingId", null)));
            }
        } catch (JSONException e) {
            // Corrupt history: start clean rather than crash the screen.
            return new ArrayList<>();
        }
        return items;
    }

    /** True when a notification arrived after the list was last opened. */
    public static synchronized boolean hasUnread(Context context) {
        if (context == null) return false;
        long lastSeen = prefs(context).getLong(KEY_LAST_SEEN, 0L);
        for (AppNotification item : getAll(context)) {
            if (item.getTimeMillis() > lastSeen) return true;
        }
        return false;
    }

    /** Marks everything currently stored as read. */
    public static synchronized void markAllSeen(Context context) {
        if (context == null) return;
        prefs(context).edit().putLong(KEY_LAST_SEEN, System.currentTimeMillis()).apply();
    }

    /** Wipes the stored history and treats everything as seen (clears the dot). */
    public static synchronized void clear(Context context) {
        if (context == null) return;
        prefs(context).edit()
                .remove(KEY_ITEMS)
                .putLong(KEY_LAST_SEEN, System.currentTimeMillis())
                .apply();
    }

    private static void save(Context context, List<AppNotification> items) {
        JSONArray array = new JSONArray();
        for (AppNotification item : items) {
            JSONObject object = new JSONObject();
            try {
                object.put("id", item.getId());
                object.put("title", item.getTitle());
                object.put("body", item.getBody());
                object.put("time", item.getTimeMillis());
                object.put("channel", item.getChannel());
                if (item.getBookingId() != null) object.put("bookingId", item.getBookingId());
            } catch (JSONException ignored) {
            }
            array.put(object);
        }
        prefs(context).edit().putString(KEY_ITEMS, array.toString()).apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
