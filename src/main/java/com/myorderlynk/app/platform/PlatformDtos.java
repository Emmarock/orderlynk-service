package com.myorderlynk.app.platform;

/** DTOs for public, platform-wide marketing metrics (the OrderLynk home page). */
public final class PlatformDtos {

    private PlatformDtos() {
    }

    /**
     * Headline numbers shown on the public home page stats strip.
     *
     * @param ordersProcessed  total orders ever placed through the platform
     * @param verifiedVendors  live vendors (active + approved)
     * @param citiesServed     distinct cities those vendors operate in
     * @param fulfillmentRate  share of completed orders that were fulfilled rather than
     *                         cancelled, as a percentage rounded to one decimal (0–100)
     */
    public record PlatformStats(
            long ordersProcessed,
            long verifiedVendors,
            long citiesServed,
            double fulfillmentRate) {
    }
}