package com.myorderlynk.app.platform;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Marketing "floor" for the public home-page stats, bound from {@code app.platform.stats.*}.
 *
 * <p>Each headline figure is reported as {@code max(real value, floor)} so a young marketplace
 * still reads convincingly before it has organic traction. Real data takes over automatically
 * once it surpasses a floor. Set every floor to {@code 0} to always show the true numbers.
 */
@ConfigurationProperties(prefix = "app.platform.stats")
public class PlatformStatsProperties {

    /** Minimum "orders processed" to display. */
    private long ordersFloor = 0;

    /** Minimum "verified vendors" to display. */
    private long verifiedVendorsFloor = 0;

    /** Minimum "cities served" to display. */
    private long citiesFloor = 0;

    /** Minimum on-time fulfillment rate to display (percentage, 0–100). */
    private double fulfillmentRateFloor = 0;

    public long getOrdersFloor() {
        return ordersFloor;
    }

    public void setOrdersFloor(long ordersFloor) {
        this.ordersFloor = ordersFloor;
    }

    public long getVerifiedVendorsFloor() {
        return verifiedVendorsFloor;
    }

    public void setVerifiedVendorsFloor(long verifiedVendorsFloor) {
        this.verifiedVendorsFloor = verifiedVendorsFloor;
    }

    public long getCitiesFloor() {
        return citiesFloor;
    }

    public void setCitiesFloor(long citiesFloor) {
        this.citiesFloor = citiesFloor;
    }

    public double getFulfillmentRateFloor() {
        return fulfillmentRateFloor;
    }

    public void setFulfillmentRateFloor(double fulfillmentRateFloor) {
        this.fulfillmentRateFloor = fulfillmentRateFloor;
    }
}
