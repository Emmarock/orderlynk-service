package com.myorderlynk.app.shipping;

import java.math.BigDecimal;

/**
 * A single purchasable rate option returned by a {@link ShippingProvider}.
 *
 * @param rateId           provider's id for this exact rate (ephemeral; used to buy a label now)
 * @param providerShipmentId provider's parent shipment id this rate belongs to
 * @param carrier          carrier name, e.g. "USPS", "UPS"
 * @param serviceLevelName human-friendly service, e.g. "Priority Mail"
 * @param serviceToken     stable machine token for the service, e.g. "usps_priority" — safe to
 *                         persist and re-select across rate refreshes (rateId is not)
 * @param amount           price to the buyer
 * @param currency         ISO currency of {@code amount}
 * @param estimatedDays    estimated transit days, when the carrier provides it
 * @param durationTerms    free-text delivery estimate, when provided
 * @param providerImageUrl small carrier logo URL, when provided
 */
public record ShippingRate(
        String rateId,
        String providerShipmentId,
        String carrier,
        String serviceLevelName,
        String serviceToken,
        BigDecimal amount,
        String currency,
        Integer estimatedDays,
        String durationTerms,
        String providerImageUrl) {
}