package com.myorderlynk.app.shipping;

import java.time.Instant;
import java.util.List;

/** Current tracking state for a parcel plus its scan history, provider-neutral. */
public record TrackingInfo(
        String carrier,
        String trackingNumber,
        ShipmentStatus status,
        Instant eta,
        List<TrackingEvent> events) {
}