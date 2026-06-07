package com.myorderlynk.app.shipping;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

/**
 * Shipping settings, bound from {@code shipping.*}. {@code provider} selects the active
 * {@link ShippingProvider} (default {@code shippo}). Leave the chosen provider's credentials
 * blank to disable shipping — rate/label calls then fail with a clean error and checkout falls
 * back to the flat per-fulfillment logistics fee.
 */
@Data
@ConfigurationProperties(prefix = "shipping")
public class ShippingProperties {

    /** Active provider key, e.g. "shippo". */
    private String provider = "shippo";

    private final Shippo shippo = new Shippo();
    private final Defaults defaults = new Defaults();

    @Data
    public static class Shippo {
        /** Shippo API token (e.g. shippo_test_… / shippo_live_…). The "ShippoToken " prefix is added if missing. */
        private String apiToken;
        /** Pinned Shippo API version date; blank uses the account default. */
        private String apiVersion = "2018-02-08";
        /** Label format requested when buying: PDF, PNG, PDF_4x6, ZPLII, etc. */
        private String labelFileType = "PDF_4x6";
    }

    /**
     * Fallback parcel attributes used when a product is missing weight/dimensions, so a quote
     * can still be produced. Values are in grams (weight) and centimetres (dimensions).
     */
    @Data
    public static class Defaults {
        private BigDecimal weightGrams = new BigDecimal("500");
        private BigDecimal lengthCm = new BigDecimal("20");
        private BigDecimal widthCm = new BigDecimal("15");
        private BigDecimal heightCm = new BigDecimal("10");
    }
}