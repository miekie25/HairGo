package com.hairgo.app.utils;

import com.hairgo.app.firebase.BookingManager;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The operating hours of one salon, read from the optional "hours" map on the
 * salon document:
 *
 * <pre>
 * hours: {
 *   mon: { open: "09:00", close: "17:00", closed: false },
 *   sun: { open: "09:00", close: "13:00", closed: true }
 * }
 * </pre>
 *
 * Salons without the field (or with a malformed day) fall back to the original
 * app-wide grid — 09:00 to 18:00, open every day — so existing salons behave
 * exactly as before.
 *
 * <p>Day values use {@link java.util.Calendar#DAY_OF_WEEK} numbering
 * (SUNDAY = 1 … SATURDAY = 7) so callers can hand a Calendar or LocalDate
 * straight over.
 */
public final class SalonHours {

    public static final int DEFAULT_OPEN_MINUTES = 9 * 60;
    public static final int DEFAULT_CLOSE_MINUTES = 18 * 60;

    /** Minimum bookable window for an open day, in minutes. */
    public static final int MIN_OPEN_MINUTES = 60;

    /** Firestore day keys, indexed by Calendar.DAY_OF_WEEK - 1 (Sunday first). */
    private static final String[] DAY_KEYS = {"sun", "mon", "tue", "wed", "thu", "fri", "sat"};
    /** Plural day names for user-facing messages ("closed on Mondays"). */
    private static final String[] DAY_NAMES = {"Sundays", "Mondays", "Tuesdays", "Wednesdays",
            "Thursdays", "Fridays", "Saturdays"};

    private final int[] openMinutes = new int[7];
    private final int[] closeMinutes = new int[7];
    private final boolean[] closed = new boolean[7];

    private SalonHours() {
    }

    /** The original app-wide grid: 09:00–18:00, open every day. */
    public static SalonHours defaults() {
        SalonHours hours = new SalonHours();
        for (int day = 0; day < 7; day++) {
            hours.openMinutes[day] = DEFAULT_OPEN_MINUTES;
            hours.closeMinutes[day] = DEFAULT_CLOSE_MINUTES;
        }
        return hours;
    }

    /**
     * Parses a salon document's optional "hours" map. Days that are missing or
     * malformed keep the default values, so a partially-written document can
     * never produce an unbookable day by accident.
     */
    public static SalonHours fromDoc(Map<String, Object> salonData) {
        SalonHours hours = defaults();
        if (salonData == null) return hours;

        Object raw = salonData.get("hours");
        if (!(raw instanceof Map)) return hours;
        Map<?, ?> doc = (Map<?, ?>) raw;

        for (int day = 0; day < 7; day++) {
            Object entryRaw = doc.get(DAY_KEYS[day]);
            if (!(entryRaw instanceof Map)) continue;
            Map<?, ?> entry = (Map<?, ?>) entryRaw;

            if (Boolean.TRUE.equals(entry.get("closed"))) {
                hours.closed[day] = true;
                continue;
            }

            int open = parseTime(entry.get("open"));
            int close = parseTime(entry.get("close"));
            if (open >= 0 && close >= 0 && close - open >= MIN_OPEN_MINUTES) {
                hours.openMinutes[day] = open;
                hours.closeMinutes[day] = close;
            }
        }
        return hours;
    }

    /** Minutes past midnight for an "HH:mm" value, or -1 when unusable. */
    private static int parseTime(Object value) {
        if (!(value instanceof String)) return -1;
        String[] parts = ((String) value).split(":");
        if (parts.length != 2) return -1;
        try {
            int hour = Integer.parseInt(parts[0].trim());
            int minute = Integer.parseInt(parts[1].trim());
            if (hour < 0 || hour > 23 || minute < 0 || minute > 59) return -1;
            return hour * 60 + minute;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    // --- day state ---------------------------------------------------------

    private static int index(int dayOfWeek) {
        if (dayOfWeek < Calendar.SUNDAY || dayOfWeek > Calendar.SATURDAY) {
            throw new IllegalArgumentException("dayOfWeek out of range: " + dayOfWeek);
        }
        return dayOfWeek - Calendar.SUNDAY;
    }

    public boolean isOpenOn(int dayOfWeek) {
        return !closed[index(dayOfWeek)];
    }

    public boolean isClosed(int dayOfWeek) {
        return closed[index(dayOfWeek)];
    }

    public int openMinutes(int dayOfWeek) {
        return openMinutes[index(dayOfWeek)];
    }

    public int closeMinutes(int dayOfWeek) {
        return closeMinutes[index(dayOfWeek)];
    }

    public void setTime(int dayOfWeek, int open, int close) {
        int i = index(dayOfWeek);
        openMinutes[i] = open;
        closeMinutes[i] = close;
    }

    public void setClosed(int dayOfWeek, boolean isClosed) {
        closed[index(dayOfWeek)] = isClosed;
    }

    /** Exact independent copy — used so editing a draft never mutates the loaded state. */
    public SalonHours copy() {
        SalonHours copy = new SalonHours();
        System.arraycopy(openMinutes, 0, copy.openMinutes, 0, 7);
        System.arraycopy(closeMinutes, 0, copy.closeMinutes, 0, 7);
        System.arraycopy(closed, 0, copy.closed, 0, 7);
        return copy;
    }

    /**
     * The 30-minute "HH:mm" slots for the given day, or an empty list when the
     * salon is closed that day. Each slot lies fully inside the open window,
     * so the last start is close - 30 minutes.
     */
    public List<String> slotsFor(int dayOfWeek) {
        if (isClosed(dayOfWeek)) return new ArrayList<>();
        int i = index(dayOfWeek);
        return BookingManager.generateTimeSlots(openMinutes[i], closeMinutes[i]);
    }

    /** Full replacement value for the salon document's "hours" field. */
    public Map<String, Object> toDocMap() {
        Map<String, Object> hours = new HashMap<>();
        for (int day = 0; day < 7; day++) {
            Map<String, Object> entry = new HashMap<>();
            entry.put("open", formatTime(openMinutes[day]));
            entry.put("close", formatTime(closeMinutes[day]));
            entry.put("closed", closed[day]);
            hours.put(DAY_KEYS[day], entry);
        }
        return hours;
    }

    /** Plural day name for messages, e.g. "Mondays". */
    public static String dayName(int dayOfWeek) {
        return DAY_NAMES[index(dayOfWeek)];
    }

    /** "HH:mm" in stable ASCII, regardless of the device locale. */
    public static String formatTime(int minutes) {
        return String.format(Locale.US, "%02d:%02d", minutes / 60, minutes % 60);
    }
}
