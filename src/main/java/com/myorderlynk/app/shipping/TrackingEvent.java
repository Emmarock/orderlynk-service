package com.myorderlynk.app.shipping;

import java.time.Instant;

/** A single scan/event in a parcel's tracking history. */
public record TrackingEvent(
        ShipmentStatus status,
        String statusDetails,
        String location,
        Instant occurredAt) {
}