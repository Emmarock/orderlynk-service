package com.myorderlynk.app.booking;

/** Classifies a {@link BookingPayment} against a booking's money breakdown (PRD §10). */
public enum BookingPaymentType {
    /** Upfront deposit that locks the time slot. */
    DEPOSIT,
    /** Remaining balance collected at/after service completion. */
    BALANCE,
    /** Full payment taken in a single charge. */
    FULL,
    /** A refund issued back to the customer. */
    REFUND
}
