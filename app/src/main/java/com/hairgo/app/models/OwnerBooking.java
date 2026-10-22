package com.hairgo.app.models;

import com.google.firebase.Timestamp;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Map;

public class OwnerBooking {

    private final String bookingId;
    private final String clientId;
    private String clientName; // may be empty until it is stored or looked up
    private final String serviceName;
    private final Date dateTime;
    private boolean cancelledByClient;
    private String status; // "pending", "confirmed", "completed" (cancelled bookings are soft-deleted)
    private String notes; // optional free-text the client wrote when booking
    private List<ServiceDetail> serviceDetails; // saved copy of the services, new bookings only
    private Double totalPrice; // null means no booked service has a price
    private int totalDurationMinutes; // 0 means it was not saved (old booking)
    private PaymentDetails paymentDetails; // null for bookings made before payments

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

        OwnerBooking booking = new OwnerBooking(id, asString(raw.get("clientID")),
                clientName == null ? "" : clientName,
                service == null ? "" : service,
                date, status);

        String notes = asString(raw.get("notes"));
        booking.notes = notes == null ? "" : notes;

        // The client's own Cancel sets isDeleted true; the owner's Decline leaves it false.
        // That is how the two kinds of cancellation are told apart.
        booking.cancelledByClient = "cancelled".equals(status)
                && Boolean.TRUE.equals(raw.get("isDeleted"));

        // New bookings keep a copy of the chosen services and their totals.
        // Old bookings have none, so these stay empty and the card hides them.
        booking.serviceDetails = ServiceDetail.fromRawList(raw.get("serviceDetails"));

        Object priceValue = raw.get("totalPrice");
        if (priceValue instanceof Number) {
            booking.totalPrice = ((Number) priceValue).doubleValue();
        }

        Object durationValue = raw.get("totalDuration");
        if (durationValue instanceof Number) {
            booking.totalDurationMinutes = ((Number) durationValue).intValue();
        }

        booking.paymentDetails = PaymentDetails.fromMap(raw);
        return booking;
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

    /** Still ahead of us and still needs the salon to turn up: pending or confirmed. */
    public boolean isActiveUpcoming() {
        return ("pending".equals(status) || "confirmed".equals(status)) && !isPast();
    }

    public boolean isPast() {
        return dateTime != null && dateTime.before(new Date());
    }

    public String getBookingId() { return bookingId; }
    public String getClientId() { return clientId; }
    public String getClientName() { return clientName; }
    public void setClientName(String clientName) { this.clientName = clientName == null ? "" : clientName; }
    public String getServiceName() { return serviceName; }
    public Date getDateTime() { return dateTime; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public boolean isCancelledByClient() { return cancelledByClient; }
    public String getNotes() { return notes == null ? "" : notes; }

    // Always returns a list, never null.
    public List<ServiceDetail> getServiceDetails() {
        return serviceDetails == null ? new ArrayList<>() : serviceDetails;
    }

    // True if this booking has a saved copy of its services (new bookings only).
    public boolean hasServiceDetails() {
        return serviceDetails != null && !serviceDetails.isEmpty();
    }

    // Null means no price is available for this booking.
    public Double getTotalPrice() { return totalPrice; }

    // 0 means the total duration was not saved (old booking).
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

    public PaymentDetails getPaymentDetails() { return paymentDetails; }
}
