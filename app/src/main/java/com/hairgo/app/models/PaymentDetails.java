package com.hairgo.app.models;

import com.google.firebase.Timestamp;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class PaymentDetails {

    public static final String TYPE_DEPOSIT = "deposit";
    public static final String TYPE_FULL = "full";
    public static final String TYPE_CASH = "cash";

    public static final String STATUS_PARTIAL = "partial";
    public static final String STATUS_PAID = "paid";
    public static final String STATUS_DUE = "due";

    private static final BigDecimal DEPOSIT_RATE = new BigDecimal("0.10");

    private final String type;
    private final String status;
    private final Double amountPaid;
    private final Double amountOutstanding;
    private final Timestamp paymentTimestamp;

    public PaymentDetails(String type, String status, Double amountPaid, Double amountOutstanding) {
        this(type, status, amountPaid, amountOutstanding, null);
    }

    public PaymentDetails(String type, String status, Double amountPaid, Double amountOutstanding, Timestamp paymentTimestamp) {
        this.type = type;
        this.status = status;
        this.amountPaid = amountPaid;
        this.amountOutstanding = amountOutstanding;
        this.paymentTimestamp = paymentTimestamp;
    }

    public static PaymentDetails depositByCard(double total) {
        BigDecimal roundedTotal = round(total);
        BigDecimal paid = roundedTotal.multiply(DEPOSIT_RATE).setScale(2, RoundingMode.HALF_UP);
        BigDecimal outstanding = roundedTotal.subtract(paid);
        return new PaymentDetails(TYPE_DEPOSIT, STATUS_PARTIAL,
                paid.doubleValue(), outstanding.doubleValue(), null);
    }

    public static PaymentDetails fullByCard(double total) {
        return new PaymentDetails(TYPE_FULL, STATUS_PAID, round(total).doubleValue(), 0.0, null);
    }

    public static PaymentDetails cashAtSalon(Double total) {
        Double outstanding = total == null ? null : round(total).doubleValue();
        return new PaymentDetails(TYPE_CASH, STATUS_DUE, 0.0, outstanding, null);
    }

    public boolean isCardPayment() {
        return TYPE_DEPOSIT.equals(type) || TYPE_FULL.equals(type);
    }

    /**
     * Recalculates the payment for an edited booking total. Money already paid is
     * kept, so lowering the total reduces the outstanding balance instead of
     * producing a refund.
     */
    public PaymentDetails recalculateForNewTotal(double newTotal) {
        BigDecimal total = round(newTotal);

        if (TYPE_CASH.equals(type)) {
            return cashAtSalon(total.doubleValue());
        }

        if (!isCardPayment()) {
            return this;
        }

        BigDecimal paidBefore = amountPaid == null ? BigDecimal.ZERO : round(amountPaid);
        BigDecimal paid;
        if (TYPE_DEPOSIT.equals(type)) {
            BigDecimal requiredDeposit = round(depositByCard(total.doubleValue()).getAmountPaid());
            paid = paidBefore.max(requiredDeposit);
        } else {
            paid = paidBefore.max(total);
        }

        BigDecimal outstanding = total.subtract(paid).max(BigDecimal.ZERO);
        String status = outstanding.signum() > 0 ? STATUS_PARTIAL : STATUS_PAID;
        return new PaymentDetails(type, status, paid.doubleValue(), outstanding.doubleValue());
    }

    /**
     * The extra amount that must be paid by card now for this edited total.
     * Cash bookings never take a card payment.
     */
    public double additionalPaymentForNewTotal(double newTotal) {
        if (!isCardPayment() || newTotal <= 0) {
            return 0.0;
        }

        PaymentDetails updated = recalculateForNewTotal(newTotal);
        double paidBefore = amountPaid == null ? 0.0 : amountPaid;
        double paidAfter = updated.amountPaid == null ? 0.0 : updated.amountPaid;
        return Math.max(0.0, round(paidAfter - paidBefore).doubleValue());
    }

    public static PaymentDetails fromMap(Map<String, Object> raw) {
        if (raw == null) return null;

        Object typeValue = raw.get("paymentType");
        Object statusValue = raw.get("paymentStatus");
        if (!(typeValue instanceof String) || !(statusValue instanceof String)) {
            return null;
        }

        Timestamp timestamp = null;
        Object timestampValue = raw.get("paymentTimestamp");
        if (timestampValue instanceof Timestamp) {
            timestamp = (Timestamp) timestampValue;
        }

        return new PaymentDetails(typeValue.toString(), statusValue.toString(),
                asDouble(raw.get("amountPaid")), asDouble(raw.get("amountOutstanding")), timestamp);
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("paymentType", type);
        map.put("paymentStatus", status);
        if (amountPaid != null) {
            map.put("amountPaid", amountPaid);
        }
        if (amountOutstanding != null) {
            map.put("amountOutstanding", amountOutstanding);
        }
        if (paymentTimestamp != null) {
            map.put("paymentTimestamp", paymentTimestamp);
        }
        return map;
    }

    private static BigDecimal round(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }

    private static Double asDouble(Object value) {
        return value instanceof Number ? ((Number) value).doubleValue() : null;
    }

    public String getType() { return type; }
    public String getStatus() { return status; }
    public Double getAmountPaid() { return amountPaid; }
    public Double getAmountOutstanding() { return amountOutstanding; }

    /**
     * Returns a new PaymentDetails with the given cash payment recorded.
     * Only valid for TYPE_CASH bookings with a known positive outstanding balance.
     * Returns null if the payment cannot be recorded.
     */
    public PaymentDetails recordCashPayment(double amount) {
        return recordCashPayment(amount, com.google.firebase.Timestamp.now());
    }

    /**
     * Returns a new PaymentDetails with the given cash payment recorded at the specified time.
     * Used for recording cash payments with a specific timestamp.
     */
    public PaymentDetails recordCashPayment(double amount, com.google.firebase.Timestamp timestamp) {
        if (!TYPE_CASH.equals(type)) return null;
        if (amountOutstanding == null || amountOutstanding <= 0) return null;
        if (amount <= 0 || amount > amountOutstanding) return null;

        BigDecimal paidBefore = amountPaid == null ? BigDecimal.ZERO : round(amountPaid);
        BigDecimal outstandingBefore = round(amountOutstanding);
        BigDecimal payment = round(amount);

        BigDecimal paidAfter = paidBefore.add(payment);
        BigDecimal outstandingAfter = outstandingBefore.subtract(payment);
        if (outstandingAfter.signum() < 0) outstandingAfter = BigDecimal.ZERO;

        String newStatus = outstandingAfter.signum() == 0 ? STATUS_PAID : STATUS_DUE;
        return new PaymentDetails(TYPE_CASH, newStatus, paidAfter.doubleValue(), outstandingAfter.doubleValue(), timestamp);
    }

    /**
     * True if this payment record allows recording an additional cash payment.
     */
    public boolean canRecordCashPayment() {
        return TYPE_CASH.equals(type)
                && amountOutstanding != null
                && amountOutstanding > 0;
    }

    public Timestamp getPaymentTimestamp() { return paymentTimestamp; }

    public Date getPaymentTimestampAsDate() {
        return paymentTimestamp == null ? null : paymentTimestamp.toDate();
    }
}
