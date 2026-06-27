package com.myorderlynk.app.shipping;

/**
 * Lifecycle of a {@link Shipment} within OrderLynk, independent of any carrier's own status
 * vocabulary. A {@link ShippingProvider} maps its native states onto these.
 */
public enum ShipmentStatus {
    /** Rates have been requested but none purchased yet. */
    RATED,
    /** A label has been purchased; tracking number issued. */
    PURCHASED,
    /** Carrier has picked up / package in transit. */
    IN_TRANSIT,
    /** Carrier reports delivered. */
    DELIVERED,
    /** Returned to sender. */
    RETURNED,
    /** Label purchase or tracking failed at the carrier. */
    FAILED,
    /** Shipment/label was cancelled or refunded. */
    CANCELLED,
    /** Carrier status not yet known. */
    UNKNOWN
}