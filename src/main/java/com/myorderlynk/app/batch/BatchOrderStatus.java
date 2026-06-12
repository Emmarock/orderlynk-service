package com.myorderlynk.app.batch;

/**
 * Lifecycle of a customer's batch-product order (batch-cargo spec §8.2). Distinct from the regular
 * product-order fulfillment status — a batch order tracks the shipment cycle, not local fulfillment.
 * Terminal: {@link #COMPLETED}, {@link #CANCELLED}.
 */
public enum BatchOrderStatus {
    ORDER_RECEIVED,
    PAYMENT_PENDING,
    PAID,
    ASSIGNED_TO_BATCH,
    SOURCING,
    PACKED,
    SHIPPED,
    ARRIVED,
    READY_FOR_PICKUP,
    OUT_FOR_DELIVERY,
    DELIVERED,
    COMPLETED,
    CANCELLED
}
