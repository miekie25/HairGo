package com.hairgo.app.fragments;

/**
 * Implemented by a dashboard that shows an unread-notifications dot, so a
 * fragment can ask it to refresh that dot the moment it posts a notification.
 *
 * <p>Keeps the fragment decoupled: instead of reaching into the activity's
 * views, it only needs to know the host can refresh the dot.
 */
public interface NotificationDotHost {
    void refreshNotificationDot();
}
