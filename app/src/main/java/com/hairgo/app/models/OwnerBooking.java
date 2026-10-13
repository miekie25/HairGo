package com.hairgo.app.models;

import com.google.firebase.Timestamp;

import java.util.Calendar;
import java.util.Date;
import java.util.Map;

public class OwnerBooking {

    private final String bookingId;
    private final String clientId;
    private final String clientName; // may be empty until createBooking stores it
    private final String serviceName;
    private final Date dateTime;
    private String status; // "pending", "confirmed", "completed" (cancelled bookings are soft-deleted)

    public OwnerBooking(String bookingId, String clientId, String clientName,
                        String serviceName, Date dateTime, String status) {
        this.bookingId = bookingId;
        this.clientId = clientId;
        this.clientName = clientName;
        this.serviceName = serviceName;
        this.dateTime = dateTime;
        this.status = status;
    }

    /** Builds an OwnerBooking from a raw Firestore booking map. Returns null if it has no id. */
    public static OwnerBooking fromMap(Map<String, Object> raw) {
        if (raw == null) return null;

        String id = asString(raw.get("bookingID"));
        if (id == null || id.isEmpty()) return null;

        Object when = raw.get("dateTime");
        Date date = when instanceof Timestamp ? ((Timestamp) when).toDate() : null;

        String status = asString(raw.get("status"));
        status = (status == null || status.isEmpty()) ? "pending" : status.toLowerCase();

        String clientName = asString(raw.get("clientName"));
        String service = asString(raw.get("serviceName"));

        return new OwnerBooking(id, asString(raw.get("clientID")),
                clientName == null ? "" : clientName,
                service == null ? "" : service,
                date, status);
    }

    private static String asString(Object value) {
        return value == null ? null : value.toString();
    }

    public boolean isToday() {
        if (dateTime == null) return false;
        Calendar a = Calendar.getInstance();
        a.setTime(dateTime);
        Calendar b = Calendar.getInstance();
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR)
                && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR);
    }

    public boolean isPast() {
        return dateTime != null && dateTime.before(new Date());
    }

    public String getBookingId() { return bookingId; }
    public String getClientId() { return clientId; }
    public String getClientName() { return clientName; }
    public String getServiceName() { return serviceName; }
    public Date getDateTime() { return dateTime; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}