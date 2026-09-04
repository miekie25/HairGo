package com.hairgo.app.utils;

import com.hairgo.app.models.OwnerBooking;
import com.hairgo.app.models.StaffMember;

import java.util.ArrayList;
import java.util.List;

/**
 * Single shared in-memory source of truth for the owner dashboard's sample data.
 * All fragments (Home, Bookings, Staff) read/write through here so counts stay
 * consistent and actions (confirm/decline/remove) survive switching tabs.
 *
 * TODO: once Firebase is wired up, this becomes redundant — fragments will read
 * directly from Firestore/Realtime Database instead.
 */
public class DashboardData {

    private static List<OwnerBooking> bookings;
    private static List<StaffMember> staff;

    public static List<OwnerBooking> getBookings() {
        if (bookings == null) {
            bookings = new ArrayList<>();
            bookings.add(new OwnerBooking("1", "Thabo M.", "Skin fade", "Today, 14:00", "pending", 150));
            bookings.add(new OwnerBooking("2", "Lindiwe K.", "Braids", "Today, 16:30", "confirmed", 350));
            bookings.add(new OwnerBooking("3", "Sipho N.", "Beard trim", "Tomorrow, 10:00", "pending", 80));
        }
        return bookings;
    }

    public static List<StaffMember> getStaff() {
        if (staff == null) {
            staff = new ArrayList<>();
            staff.add(new StaffMember("1", "Nomsa D.", "Hairstylist", "071 234 5678"));
            staff.add(new StaffMember("2", "Kagiso R.", "Barber", "082 345 6789"));
        }
        return staff;
    }
}