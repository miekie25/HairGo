package com.hairgo.app.models;

import com.google.firebase.Timestamp;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * One payment transaction stored under bookings/{bookingId}/payments/{paymentId}.
 * A payment record is never updated or overwritten once it has been written.
 */
public class PaymentRecord {

    public static final String METHOD_CARD = "card";
    public static final String METHOD_CASH = "cash";

    private final String paymentId;
    private final String bookingId;
    private final String salonId;
    private final String clientId;
    private final String paymentMethod;
    private final String paymentType;
    private final double paymentAmount;
    private final Timestamp paymentTimestamp;
    private final String paymentDescription;
    private final String recordedBy;

    public PaymentRecord(String paymentId, String bookingId, String salonId, String clientId,
                         String paymentMethod, String paymentType, double paymentAmount,
                         Timestamp paymentTimestamp, String paymentDescription, String recordedBy) {
        this.paymentId = paymentId == null ? "" : paymentId;
        this.bookingId = bookingId == null ? "" : bookingId;
        this.salonId = salonId == null ? "" : salonId;
        this.clientId = clientId == null ? "" : clientId;
        this.paymentMethod = paymentMethod == null ? "" : paymentMethod;
        this.paymentType = paymentType == null ? "" : paymentType;
        this.paymentAmount = paymentAmount;
        this.paymentTimestamp = paymentTimestamp;
        this.paymentDescription = paymentDescription == null ? "" : paymentDescription;
        this.recordedBy = recordedBy == null ? "" : recordedBy;
    }

    public static PaymentRecord fromMap(Map<String, Object> raw) {
        if (raw == null) return null;

        String paymentId = asString(raw.get("paymentID"));
        if (paymentId == null || paymentId.isEmpty()) {
            paymentId = asString(raw.get("paymentId"));
        }

        Object amountValue = raw.get("paymentAmount");
        double amount = amountValue instanceof Number ? ((Number) amountValue).doubleValue() : 0.0;

        Timestamp timestamp = null;
        Object timestampValue = raw.get("paymentTimestamp");
        if (timestampValue instanceof Timestamp) {
            timestamp = (Timestamp) timestampValue;
        }

        return new PaymentRecord(
                paymentId,
                asString(raw.get("bookingID")),
                asString(raw.get("salonID")),
                asString(raw.get("clientID")),
                asString(raw.get("paymentMethod")),
                asString(raw.get("paymentType")),
                amount,
                timestamp,
                asString(raw.get("paymentDescription")),
                asString(raw.get("recordedBy"))
        );
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("paymentID", paymentId);
        map.put("bookingID", bookingId);
        map.put("salonID", salonId);
        map.put("clientID", clientId);
        map.put("paymentMethod", paymentMethod);
        map.put("paymentType", paymentType);
        map.put("paymentAmount", paymentAmount);
        if (paymentTimestamp != null) {
            map.put("paymentTimestamp", paymentTimestamp);
        }
        if (!paymentDescription.isEmpty()) {
            map.put("paymentDescription", paymentDescription);
        }
        if (!recordedBy.isEmpty()) {
            map.put("recordedBy", recordedBy);
        }
        return map;
    }

    private static String asString(Object value) {
        return value == null ? null : value.toString();
    }

    public String getPaymentId() { return paymentId; }
    public String getBookingId() { return bookingId; }
    public String getSalonId() { return salonId; }
    public String getClientId() { return clientId; }
    public String getPaymentMethod() { return paymentMethod; }
    public String getPaymentType() { return paymentType; }
    public double getPaymentAmount() { return paymentAmount; }
    public Timestamp getPaymentTimestamp() { return paymentTimestamp; }
    public java.util.Date getPaymentTimestampAsDate() {
        return paymentTimestamp == null ? null : paymentTimestamp.toDate();
    }
    public String getPaymentDescription() { return paymentDescription; }
    public String getRecordedBy() { return recordedBy; }
}