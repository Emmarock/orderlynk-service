package com.myorderlynk.app.booking;

/**
 * Lifecycle of a service {@link Booking} (service-provider PRD §8). Bookings have their
 * own lifecycle and deliberately do NOT reuse product-order fulfillment statuses.
 *
 * <p>Terminal states: {@link #CLOSED}, {@link #CANCELLED}, {@link #NO_SHOW}, {@link #REJECTED}.
 * Valid transitions are enforced in {@link BookingService}, not here.
 */
public enum BookingStatus {
    /** Booking form started but not submitted. */
    DRAFT,
    /** Customer submitted; provider approval required (manual-approval mode). */
    REQUESTED,
    /** Provider approved; a deposit may still be pending. */
    APPROVED,
    /** Deposit required to lock the time slot. */
    DEPOSIT_PENDING,
    /** Deposit paid (or no deposit required); the time slot is locked. */
    CONFIRMED,
    /** Pre-service reminder has been sent to the customer. */
    REMINDER_SENT,
    /** Service is being provided (optional). */
    IN_PROGRESS,
    /** Provider marked the service completed. */
    COMPLETED,
    /** Service complete but an outstanding balance is due. */
    BALANCE_PENDING,
    /** Booking fully paid and review requested/submitted. */
    CLOSED,
    /** Customer or provider cancelled before the service. */
    CANCELLED,
    /** Customer did not attend. */
    NO_SHOW,
    /** Provider rejected the request. */
    REJECTED
}
