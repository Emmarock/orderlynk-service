package com.myorderlynk.app.common.enums;

/**
 * Union of every fulfillment status across all fulfillment types.
 * Valid transitions per {@link FulfillmentType} are enforced in the
 * fulfillment service, not here.
 */
public enum FulfillmentStatus {
    ORDER_RECEIVED,
    PAYMENT_PENDING,
    PAID,
    VENDOR_CONFIRMED,
    ASSIGNED_TO_BATCH,
    SOURCING,
    PREPARING,
    PACKED,
    READY_FOR_PICKUP,
    SHIPPED,
    OUT_FOR_DELIVERY,
    ARRIVED,
    DELIVERED,
    COMPLETED,
    CANCELLED
}
