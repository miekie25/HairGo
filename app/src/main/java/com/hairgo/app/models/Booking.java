package com.hairgo.app.models;

import android.text.TextUtils;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class Booking {
    private String bookingId;
    private String salonId;
    private String salonName;
    private List<String> serviceNames;
    // A copy of the chosen services with price and duration. Old bookings have none.
    private List<ServiceDetail> serviceDetails;
    // Sum of the prices that are set. Null means no selected service has a price.
    private Double totalPrice;
    // Sum of the durations in minutes. 0 means it was not saved (old booking).
    private int totalDurationMinutes;
    private Date dateTime;
    private String status; // pending, confirmed, completed, cancelled, expired
    private int salonImageRes; // local drawable, 0 means use the placeholder
    private String notes; // optional free-text the client wrote when booking

    public Booking() {
        this.serviceNames = new ArrayList<>();
    }

    public Booking(String bookingId, String salonName, String serviceName, Date dateTime, String status) {
        this(bookingId, null, salonName, serviceName, dateTime, status, 0);
    }

    public Booking(String bookingId, String salonId, String salonName, String serviceName, Date dateTime, String status) {
        this(bookingId, salonId, salonName, serviceName, dateTime, status, 0);
    }

    public Booking(String bookingId, String salonId, String salonName, String serviceName,
                   Date dateTime, String status, int salonImageRes) {
        this(bookingId, salonId, salonName, singleton(serviceName), dateTime, status, salonImageRes);
    }

    public Booking(String bookingId, String salonId, String salonName, List<String> serviceNames,
                   Date dateTime, String status, int salonImageRes) {
        this.bookingId = bookingId;
        this.salonId = salonId;
        this.salonName = salonName;
        this.serviceNames = serviceNames == null ? new ArrayList<>() : serviceNames;
        this.dateTime = dateTime;
        this.status = status;
        this.salonImageRes = salonImageRes;
    }

    private static List<String> singleton(String serviceName) {
        List<String> list = new ArrayList<>();
        if (serviceName != null && !serviceName.isEmpty()) {
            list.add(serviceName);
        }
        return list;
    }

    // Adds up the prices that are set. Returns null if none of them has a price.
    public static Double sumPrice(List<ServiceDetail> details) {
        if (details == null) {
            return null;
        }
        double total = 0;
        boolean anyPrice = false;
        for (ServiceDetail detail : details) {
            if (detail.hasPrice()) {
                total += detail.getPrice();
                anyPrice = true;
            }
        }
        return anyPrice ? total : null;
    }

    // Adds up the durations in minutes.
    public static int sumDuration(List<ServiceDetail> details) {
        int total = 0;
        if (details != null) {
            for (ServiceDetail detail : details) {
                total += detail.getDurationMinutes();
            }
        }
        return total;
    }

    public String getBookingId() { return bookingId; }
    public String getSalonId() { return salonId; }
    public String getSalonName() { return salonName; }
    public List<String> getServiceNames() {
        return serviceNames == null ? new ArrayList<>() : serviceNames;
    }
    /** All booked services joined for display, e.g. "Haircut, Beard Trim". */
    public String getServiceLabel() { return TextUtils.join(", ", getServiceNames()); }

    // Always returns a list, never null.
    public List<ServiceDetail> getServiceDetails() {
        return serviceDetails == null ? new ArrayList<>() : serviceDetails;
    }

    // True if this booking has a saved copy of its services (new bookings only).
    public boolean hasServiceDetails() {
        return serviceDetails != null && !serviceDetails.isEmpty();
    }

    // Sets the services and works out the totals from them.
    public void setServiceDetails(List<ServiceDetail> details) {
        this.serviceDetails = details;
        this.totalPrice = sumPrice(details);
        this.totalDurationMinutes = sumDuration(details);
    }

    // Use these two when reading totals that were already saved in Firestore.
    public void setTotalPrice(Double totalPrice) { this.totalPrice = totalPrice; }
    public void setTotalDurationMinutes(int minutes) { this.totalDurationMinutes = minutes; }

    // Null means no price is available for this booking.
    public Double getTotalPrice() { return totalPrice; }
    public int getTotalDurationMinutes() { return totalDurationMinutes; }

    // True if some of the booked services have no price, so the total is partial.
    public boolean hasUnpricedServices() {
        for (ServiceDetail detail : getServiceDetails()) {
            if (!detail.hasPrice()) {
                return true;
            }
        }
        return false;
    }

    public Date getDateTime() { return dateTime; }
    public String getStatus() { return status; }
    public int getSalonImageRes() { return salonImageRes; }
    public String getNotes() { return notes == null ? "" : notes; }
    public void setNotes(String notes) { this.notes = notes; }
}