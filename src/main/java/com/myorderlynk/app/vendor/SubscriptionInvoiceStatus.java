package com.myorderlynk.app.vendor;

/** Lifecycle of a monthly vendor subscription invoice. */
public enum SubscriptionInvoiceStatus {
    /** Generated and owed; not yet collected. */
    DUE,
    /** Collected (charged via Stripe, or marked paid by an admin). */
    PAID,
    /** Forgiven by an admin (e.g. goodwill, billing dispute). */
    WAIVED,
    /** A collection attempt failed; awaiting retry or admin action. */
    FAILED
}
