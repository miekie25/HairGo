package com.hairgo.app.utils;

import com.hairgo.app.models.Booking;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class BookingSections {

    public static final int TYPE_UPCOMING = 0;
    public static final int TYPE_PAST = 1;

    public static class BookingSection {
        private final int type;
        private final List<Booking> bookings;

        BookingSection(int type, List<Booking> bookings) {
            this.type = type;
            this.bookings = bookings;
        }

        public int getType() { return type; }
        public List<Booking> getBookings() { return bookings; }
        public int getCount() { return bookings.size(); }
        public boolean isEmpty() { return bookings.isEmpty(); }
    }

    /**
     * Splits bookings into Upcoming then Past. A booking is Past when its date is
     * before today, or when its status is completed/cancelled. Date wins when the
     * two rules disagree, so a stale pending booking filed as upcoming still lands
     * in Past. Empty sections are omitted.
     */
    public static List<BookingSection> partition(List<Booking> bookings) {
        List<BookingSection> sections = new ArrayList<>();
        if (bookings == null || bookings.isEmpty()) {
            return sections;
        }

        List<Booking> upcoming = new ArrayList<>();
        List<Booking> past = new ArrayList<>();

        for (Booking booking : bookings) {
            if (isPast(booking)) {
                past.add(booking);
            } else {
                upcoming.add(booking);
            }
        }

        // Soonest action first for upcoming, most recent first for past.
        upcoming.sort(Comparator.comparing(Booking::getDateTime,
                Comparator.nullsLast(Comparator.naturalOrder())));
        past.sort(Comparator.comparing(Booking::getDateTime,
                Comparator.nullsLast(Comparator.reverseOrder())));

        if (!upcoming.isEmpty()) {
            sections.add(new BookingSection(TYPE_UPCOMING, upcoming));
        }
        if (!past.isEmpty()) {
            sections.add(new BookingSection(TYPE_PAST, past));
        }
        return sections;
    }

    private static boolean isPast(Booking booking) {
        if (booking == null) return false;

        String status = booking.getStatus();
        if ("completed".equals(status) || "cancelled".equals(status)) {
            return true;
        }

        if (booking.getDateTime() == null) return false;

        LocalDate bookingDate = booking.getDateTime()
                .toInstant()
                .atZone(ZoneId.systemDefault())
                .toLocalDate();
        return bookingDate.isBefore(LocalDate.now());
    }

    public static boolean isEmpty(List<BookingSection> sections) {
        return sections == null || sections.isEmpty();
    }
}