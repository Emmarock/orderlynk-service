package com.myorderlynk.app.shipping;

import java.time.Instant;

/**
 * Result of purchasing a shipping label (a "transaction" in Shippo terms).
 *
 * @param transactionId        provider's id for the purchase
 * @param status               normalised status of the purchase
 * @param trackingNumber       carrier tracking number
 * @param trackingUrlProvider  carrier's public tracking page for the parcel
 * @param labelUrl             URL of the printable label (PDF/PNG)
 * @param eta                  estimated delivery time, when provided
 * @param messages             any provider-side messages/errors, joined for logging
 */
public record ShippingLabel(
        String transactionId,
        ShipmentStatus status,
        String trackingNumber,
        String trackingUrlProvider,
        String labelUrl,
        Instant eta,
        String messages) {
}