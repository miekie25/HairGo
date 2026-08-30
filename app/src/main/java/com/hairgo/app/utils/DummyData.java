package com.hairgo.app.utils;

import com.hairgo.app.models.Salon;

import com.hairgo.app.models.Booking;
import java.util.Calendar;
import java.util.Date;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class DummyData {

    public static List<Salon> getDummySalons() {
        List<Salon> salons = new ArrayList<>();

        salons.add(new Salon("1", "Fresh Fadez Barbershop", "Randburg", 4.8,
                Arrays.asList("Haircut", "Beard Trim", "Line Up", "Hot Towel Shave")));

        salons.add(new Salon("2", "Glow Hair Studio", "Sandton", 4.5,
                Arrays.asList("Braids", "Weave Install", "Silk Press", "Deep Conditioning")));

        salons.add(new Salon("3", "The Cutting Edge", "Randburg", 4.2,
                Arrays.asList("Haircut", "Fade", "Kids Cut", "Beard Sculpt")));

        salons.add(new Salon("4", "Bella Hair Lounge", "Roodepoort", 4.9,
                Arrays.asList("Braids", "Cornrows", "Treatment", "Blow Dry")));

        return salons;
    }

    public static List<Booking> getDummyBookings() {
        List<Booking> bookings = new ArrayList<>();
        Calendar cal;

        cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_MONTH, 3);
        bookings.add(new Booking("b1", "Fresh Fadez Barbershop", "Haircut", cal.getTime(), "pending"));

        cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_MONTH, 1);
        bookings.add(new Booking("b2", "Glow Hair Studio", "Silk Press", cal.getTime(), "confirmed"));

        cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_MONTH, -5);
        bookings.add(new Booking("b3", "The Cutting Edge", "Fade", cal.getTime(), "completed"));

        cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_MONTH, -2);
        bookings.add(new Booking("b4", "Bella Hair Lounge", "Braids", cal.getTime(), "cancelled"));

        return bookings;
    }

    public static Booking getDummyBookingById(String bookingId) {
        for (Booking booking : getDummyBookings()) {
            if (booking.getBookingId().equals(bookingId)) {
                return booking;
            }
        }
        return null;
    }

    public static Salon getDummySalonById(String salonId) {
        for (Salon salon : getDummySalons()) {
            if (salon.getSalonId().equals(salonId)) {
                return salon;
            }
        }
        return null;
    }
}