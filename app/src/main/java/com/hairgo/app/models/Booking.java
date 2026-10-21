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

    public String getBookingId() { return bookingId; }
    public String getSalonId() { return salonId; }
    public String getSalonName() { return salonName; }
    public List<String> getServiceNames() {
        return serviceNames == null ? new ArrayList<>() : serviceNames;
    }
    /** All booked services joined for display, e.g. "Haircut, Beard Trim". */
    public String getServiceLabel() { return TextUtils.join(", ", getServiceNames()); }
    public Date getDateTime() { return dateTime; }
    public String getStatus() { return status; }
    public int getSalonImageRes() { return salonImageRes; }
    public String getNotes() { return notes == null ? "" : notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
