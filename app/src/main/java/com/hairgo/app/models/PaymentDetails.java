package com.hairgo.app.models;

import java.math.BigDecimal;
import java.math.RoundingMode;
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

    public PaymentDetails(String type, String status, Double amountPaid, Double amountOutstanding) {
        this.type = type;
        this.status = status;
        this.amountPaid = amountPaid;
        this.amountOutstanding = amountOutstanding;
    }

    public static PaymentDetails depositByCard(double total) {
        BigDecimal roundedTotal = round(total);
        BigDecimal paid = roundedTotal.multiply(DEPOSIT_RATE).setScale(2, RoundingMode.HALF_UP);
        BigDecimal outstanding = roundedTotal.subtract(paid);
        return new PaymentDetails(TYPE_DEPOSIT, STATUS_PARTIAL,
                paid.doubleValue(), outstanding.doubleValue());
    }

    public static PaymentDetails fullByCard(double total) {
        return new PaymentDetails(TYPE_FULL, STATUS_PAID, round(total).doubleValue(), 0.0);
    }

    public static PaymentDetails cashAtSalon(Double total) {
        Double outstanding = total == null ? null : round(total).doubleValue();
        return new PaymentDetails(TYPE_CASH, STATUS_DUE, 0.0, outstanding);
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

        return new PaymentDetails(typeValue.toString(), statusValue.toString(),
                asDouble(raw.get("amountPaid")), asDouble(raw.get("amountOutstanding")));
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
}
