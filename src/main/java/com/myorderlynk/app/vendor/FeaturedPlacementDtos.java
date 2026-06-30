package com.myorderlynk.app.vendor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** DTOs for featured-placement purchases ({@code /api/vendor/featured}, {@code /api/admin/promotions}). */
public final class FeaturedPlacementDtos {

    private FeaturedPlacementDtos() {
    }

    /** Current featured-placement price + duration, for the vendor's purchase prompt. */
    public record PricingResponse(BigDecimal fee, int days, String currency) {
    }

    public record PlacementResponse(
            UUID id,
            UUID vendorId,
            int days,
            BigDecimal amount,
            String currency,
            Instant startsAt,
            Instant endsAt,
            SubscriptionInvoiceStatus status,
            Instant paidAt,
            Instant createdAt) {
    }

    public static PlacementResponse toResponse(FeaturedPlacement p) {
        return new PlacementResponse(p.getId(), p.getVendorId(), p.getDays(), p.getAmount(),
                p.getCurrency(), p.getStartsAt(), p.getEndsAt(), p.getStatus(), p.getPaidAt(),
                p.getCreatedAt());
    }
}
