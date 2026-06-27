package com.myorderlynk.app.shipping;

import java.util.List;

/**
 * Provider-neutral request to rate (and later ship) a parcel from one address to another.
 * {@code metadata} is an opaque string a provider echoes back on its objects/webhooks
 * (we use it to correlate a carrier shipment with an OrderLynk order).
 */
public record ShipmentRequest(
        ShippingAddress from,
        ShippingAddress to,
        List<ShippingParcel> parcels,
        String metadata) {
}