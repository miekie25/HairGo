package com.hairgo.app.models;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PaymentDetailsTest {

    @Test
    public void depositEditChargesOnlyTheDepositDifference() {
        PaymentDetails original = PaymentDetails.depositByCard(150.0);

        assertEquals(5.0, original.additionalPaymentForNewTotal(200.0), 0.001);

        PaymentDetails updated = original.recalculateForNewTotal(200.0);
        assertEquals(PaymentDetails.TYPE_DEPOSIT, updated.getType());
        assertEquals(PaymentDetails.STATUS_PARTIAL, updated.getStatus());
        assertEquals(20.0, updated.getAmountPaid(), 0.001);
        assertEquals(180.0, updated.getAmountOutstanding(), 0.001);
    }

    @Test
    public void fullEditChargesTheRemainingBalance() {
        PaymentDetails original = PaymentDetails.fullByCard(150.0);

        assertEquals(50.0, original.additionalPaymentForNewTotal(200.0), 0.001);

        PaymentDetails updated = original.recalculateForNewTotal(200.0);
        assertEquals(PaymentDetails.TYPE_FULL, updated.getType());
        assertEquals(PaymentDetails.STATUS_PAID, updated.getStatus());
        assertEquals(200.0, updated.getAmountPaid(), 0.001);
        assertEquals(0.0, updated.getAmountOutstanding(), 0.001);
    }

    @Test
    public void cashEditOnlyUpdatesTheOutstandingAmount() {
        PaymentDetails original = PaymentDetails.cashAtSalon(150.0);

        assertFalse(original.isCardPayment());
        assertEquals(0.0, original.additionalPaymentForNewTotal(200.0), 0.001);

        PaymentDetails updated = original.recalculateForNewTotal(200.0);
        assertEquals(PaymentDetails.TYPE_CASH, updated.getType());
        assertEquals(PaymentDetails.STATUS_DUE, updated.getStatus());
        assertEquals(0.0, updated.getAmountPaid(), 0.001);
        assertEquals(200.0, updated.getAmountOutstanding(), 0.001);
    }

    @Test
    public void loweringDepositTotalKeepsPaidAmountAndReducesOutstanding() {
        PaymentDetails original = PaymentDetails.depositByCard(150.0);

        assertEquals(0.0, original.additionalPaymentForNewTotal(100.0), 0.001);

        PaymentDetails updated = original.recalculateForNewTotal(100.0);
        assertEquals(15.0, updated.getAmountPaid(), 0.001);
        assertEquals(85.0, updated.getAmountOutstanding(), 0.001);
        assertEquals(PaymentDetails.STATUS_PARTIAL, updated.getStatus());
    }

    @Test
    public void loweringFullTotalKeepsPaidAmountWithoutRefund() {
        PaymentDetails original = PaymentDetails.fullByCard(150.0);

        assertEquals(0.0, original.additionalPaymentForNewTotal(100.0), 0.001);

        PaymentDetails updated = original.recalculateForNewTotal(100.0);
        assertEquals(150.0, updated.getAmountPaid(), 0.001);
        assertEquals(0.0, updated.getAmountOutstanding(), 0.001);
        assertEquals(PaymentDetails.STATUS_PAID, updated.getStatus());
    }

    @Test
    public void depositPaidAboveTheNewTotalIsMarkedPaid() {
        PaymentDetails original = PaymentDetails.depositByCard(150.0);

        PaymentDetails updated = original.recalculateForNewTotal(10.0);
        assertTrue(updated.isCardPayment());
        assertEquals(15.0, updated.getAmountPaid(), 0.001);
        assertEquals(0.0, updated.getAmountOutstanding(), 0.001);
        assertEquals(PaymentDetails.STATUS_PAID, updated.getStatus());
    }

    @Test
    public void cardPaymentIsDetectedFromType() {
        assertTrue(PaymentDetails.depositByCard(100.0).isCardPayment());
        assertTrue(PaymentDetails.fullByCard(100.0).isCardPayment());
    }
}
