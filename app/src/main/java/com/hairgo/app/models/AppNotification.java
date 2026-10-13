package com.hairgo.app.models;

/**
 * A single entry in the in-app notification list. Mirrors the notification that
 * was posted to the system tray so the bell screen can show the same history.
 */
public class AppNotification {

    private final String id;
    private final String title;
    private final String body;
    private final long timeMillis;
    private final String channel;
    private final String bookingId;

    public AppNotification(String id, String title, String body, long timeMillis,
                           String channel, String bookingId) {
        this.id = id;
        this.title = title;
        this.body = body;
        this.timeMillis = timeMillis;
        this.channel = channel;
        this.bookingId = bookingId;
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getBody() {
        return body;
    }

    public long getTimeMillis() {
        return timeMillis;
    }

    public String getChannel() {
        return channel;
    }

    public String getBookingId() {
        return bookingId;
    }
}
