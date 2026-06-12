package com.myorderlynk.app.batch;

/**
 * Lifecycle of a customer-supplied shipment request (batch-cargo spec §8.3). The price is finalized
 * at {@link #WEIGHED}/{@link #INVOICE_GENERATED} once actual weight is recorded, then paid.
 * Terminal: {@link #COMPLETED}, {@link #CANCELLED}.
 */
public enum ShipmentRequestStatus {
    REQUEST_CREATED,
    AWAITING_DROP_OFF,
    RECEIVED_AT_COLLECTION,
    WEIGHED,
    INVOICE_GENERATED,
    PAYMENT_PENDING,
    PAID,
    ADDED_TO_BATCH,
    SHIPPED,
    ARRIVED,
    READY_FOR_PICKUP,
    OUT_FOR_DELIVERY,
    DELIVERED,
    COMPLETED,
    CANCELLED
}
