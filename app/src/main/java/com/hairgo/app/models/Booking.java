package com.hairgo.app.models;

import java.util.Date;

public class Booking {
    private String bookingId;
    private String salonName;
    private String serviceName;
    private Date dateTime;
    private String status; // pending, confirmed, completed, cancelled

    public Booking() {
    }

    public Booking(String bookingId, String salonName, String serviceName, Date dateTime, String status) {
        this.bookingId = bookingId;
        this.salonName = salonName;
        this.serviceName = serviceName;
        this.dateTime = dateTime;
        this.status = status;
    }

    public String getBookingId() { return bookingId; }
    public String getSalonName() { return salonName; }
    public String getServiceName() { return serviceName; }
    public Date getDateTime() { return dateTime; }
    public String getStatus() { return status; }
}