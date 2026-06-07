package com.myorderlynk.app.shipping;

import java.util.List;

/**
 * A shipping carrier-aggregator integration (Shippo today; EasyPost, shipping APIs, or a
 * direct carrier tomorrow). Implementations are stateless and provider-specific; the rest of
 * the app talks only to this interface and the provider-neutral models in this package.
 *
 * <p>Implementations must degrade gracefully: when not configured, {@link #isConfigured()}
 * returns false and the rate/label/track methods throw {@link ShippingException} rather than
 * failing at startup.
 */
public interface ShippingProvider {

    /** Stable key identifying this provider, e.g. {@code "shippo"}. Matches {@code shipping.provider}. */
    String key();

    /** True when credentials are present and the provider can service requests. */
    boolean isConfigured();

    /** Fetch all available rate options for the parcel(s) described by {@code request}. */
    List<ShippingRate> getRates(ShipmentRequest request);

    /** Purchase a shipping label for a previously-returned {@link ShippingRate#rateId()}. */
    ShippingLabel purchaseLabel(String rateId);

    /**
     * Fetch the latest tracking state for a parcel. {@code carrierToken} is the carrier's
     * machine token (e.g. {@code "usps"}); pass what the provider expects.
     */
    TrackingInfo track(String carrierToken, String trackingNumber);
}