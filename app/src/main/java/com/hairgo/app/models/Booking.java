package com.hairgo.app.models;

import java.util.Date;

public class Booking {
    private String bookingId;
    private String salonId;
    private String salonName;
    private String serviceName;
    private Date dateTime;
    private String status; // pending, confirmed, completed, cancelled
    private int salonImageRes; // local drawable, 0 means use the placeholder

    public Booking() {
    }

    public Booking(String bookingId, String salonName, String serviceName, Date dateTime, String status) {
        this(bookingId, null, salonName, serviceName, dateTime, status);
    }

    public Booking(String bookingId, String salonId, String salonName, String serviceName, Date dateTime, String status) {
        this(bookingId, salonId, salonName, serviceName, dateTime, status, 0);
    }

    public Booking(String bookingId, String salonId, String salonName, String serviceName,
                   Date dateTime, String status, int salonImageRes) {
        this.bookingId = bookingId;
        this.salonId = salonId;
        this.salonName = salonName;
        this.serviceName = serviceName;
        this.dateTime = dateTime;
        this.status = status;
        this.salonImageRes = salonImageRes;
    }

    public String getBookingId() { return bookingId; }
    public String getSalonId() { return salonId; }
    public String getSalonName() { return salonName; }
    public String getServiceName() { return serviceName; }
    public Date getDateTime() { return dateTime; }
    public String getStatus() { return status; }
    public int getSalonImageRes() { return salonImageRes; }
}