package com.myorderlynk.app.vendor;

import com.myorderlynk.app.exception.ApiException;
import com.myorderlynk.app.order.FeeSettings;
import com.myorderlynk.app.order.FeeSettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Sells featured placement: a vendor buys a promotion window (priced in fee settings), which extends
 * {@code Vendor.featuredUntil} and records a ledger entry. Stacking purchases extends from the current
 * end of the window rather than from now, so paid time is never lost. Settled like a subscription
 * invoice ({@code DUE} → {@code markPaid}/{@code waive}).
 */
@Service
public class FeaturedPlacementService {

    private static final Logger log = LoggerFactory.getLogger(FeaturedPlacementService.class);

    private final VendorRepository vendors;
    private final FeaturedPlacementRepository placements;
    private final FeeSettingsService feeSettings;
    private final com.myorderlynk.app.payment.PaymentClient paymentClient;

    public FeaturedPlacementService(VendorRepository vendors, FeaturedPlacementRepository placements,
                                    FeeSettingsService feeSettings,
                                    com.myorderlynk.app.payment.PaymentClient paymentClient) {
        this.vendors = vendors;
        this.placements = placements;
        this.feeSettings = feeSettings;
        this.paymentClient = paymentClient;
    }

    @Transactional
    public FeaturedPlacement purchase(UUID vendorId) {
        Vendor vendor = vendors.findById(vendorId)
                .orElseThrow(() -> ApiException.notFound("Vendor not found"));
        FeeSettings settings = feeSettings.current();
        int days = settings.getFeaturedPlacementDays();

        Instant now = Instant.now();
        // Stack on top of any remaining featured window so the vendor never loses paid time.
        Instant startsAt = vendor.getFeaturedUntil() != null && vendor.getFeaturedUntil().isAfter(now)
                ? vendor.getFeaturedUntil() : now;
        Instant endsAt = startsAt.plus(Duration.ofDays(days));
        vendor.setFeaturedUntil(endsAt);
        vendors.save(vendor);

        FeaturedPlacement placement = placements.save(new FeaturedPlacement(
                vendorId, days, settings.getFeaturedPlacementFee(),
                settings.getFeaturedPlacementCurrency(), startsAt, endsAt));
        log.info("Featured placement purchased: vendor={} days={} fee={} {} until={}", vendorId, days,
                settings.getFeaturedPlacementFee(), settings.getFeaturedPlacementCurrency(), endsAt);

        // Attempt to collect immediately by netting the fee out of the vendor's balance. If it can't be
        // collected (insufficient balance / service down) the placement stays DUE for an admin to settle.
        String reference = paymentClient.chargeVendor(vendorId, placement.getAmount(), placement.getCurrency(),
                "ADVERTISING", "FEAT-" + placement.getId());
        if (reference != null) {
            placement.setStatus(SubscriptionInvoiceStatus.PAID);
            placement.setPaidAt(Instant.now());
            placement.setReference(reference);
            placement = placements.save(placement);
        }
        return placement;
    }

    @Transactional
    public FeaturedPlacement markPaid(UUID placementId, String reference) {
        FeaturedPlacement p = require(placementId);
        p.setStatus(SubscriptionInvoiceStatus.PAID);
        p.setPaidAt(Instant.now());
        if (reference != null && !reference.isBlank()) {
            p.setReference(reference);
        }
        log.info("Featured placement {} marked PAID (vendor {})", placementId, p.getVendorId());
        return placements.save(p);
    }

    @Transactional
    public FeaturedPlacement waive(UUID placementId) {
        FeaturedPlacement p = require(placementId);
        p.setStatus(SubscriptionInvoiceStatus.WAIVED);
        log.info("Featured placement {} WAIVED (vendor {})", placementId, p.getVendorId());
        return placements.save(p);
    }

    @Transactional(readOnly = true)
    public List<FeaturedPlacement> forVendor(UUID vendorId) {
        return placements.findByVendorIdOrderByStartsAtDesc(vendorId);
    }

    /** Current price of one featured-placement slot, so the vendor sees the cost before buying. */
    @Transactional(readOnly = true)
    public FeaturedPlacementDtos.PricingResponse pricing() {
        FeeSettings s = feeSettings.current();
        return new FeaturedPlacementDtos.PricingResponse(
                s.getFeaturedPlacementFee(), s.getFeaturedPlacementDays(), s.getFeaturedPlacementCurrency());
    }

    private FeaturedPlacement require(UUID placementId) {
        return placements.findById(placementId)
                .orElseThrow(() -> ApiException.notFound("Featured placement not found"));
    }
}
