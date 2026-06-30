package com.myorderlynk.app.order;

import com.myorderlynk.app.order.FeeSettingsDtos.UpdateFeeSettingsRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;

/**
 * Source of truth for the live platform fee policy. The policy lives in a single
 * {@link FeeSettings} row, seeded once from {@link FeeProperties} bootstrap defaults and edited
 * thereafter by an admin. Read paths (quote/checkout/earnings) call {@link #current()}; the admin
 * console calls {@link #update(UpdateFeeSettingsRequest)}.
 */
@Service
public class FeeSettingsService {

    private static final Logger log = LoggerFactory.getLogger(FeeSettingsService.class);

    private final FeeSettingsRepository repo;
    private final FeeProperties bootstrap;

    public FeeSettingsService(FeeSettingsRepository repo, FeeProperties bootstrap) {
        this.repo = repo;
        this.bootstrap = bootstrap;
    }

    /**
     * The live fee policy. Returns the persisted row; before it is seeded (or in a context without
     * the startup seed) it returns a transient defaults instance so callers always get a valid policy.
     */
    @Transactional(readOnly = true)
    public FeeSettings current() {
        return repo.findFirstByOrderByCreatedAtAsc().orElseGet(this::transientDefaults);
    }

    /** Seed the singleton row from bootstrap defaults on first boot. No-op once a row exists. */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void seedIfMissing() {
        if (repo.count() > 0) {
            return;
        }
        repo.save(transientDefaults());
        log.info("Seeded fee_settings from bootstrap defaults (app.fees.*)");
    }

    /** Replace the live fee policy with the supplied values. */
    @Transactional
    public FeeSettings update(UpdateFeeSettingsRequest req) {
        FeeSettings s = repo.findFirstByOrderByCreatedAtAsc().orElseGet(FeeSettings::new);
        s.setServiceFeeRate(req.serviceFeeRate());
        s.setProcessingRate(req.processingRate());
        s.setProcessingFixed(req.processingFixed());
        s.setProcessingBufferRate(req.processingBufferRate());
        s.setGrossUpProcessing(req.grossUpProcessing());
        s.setLogisticsMarginRate(req.logisticsMarginRate());
        s.setLogisticsMarkupFlat(req.logisticsMarkupFlat());
        s.setTaxRate(req.taxRate());
        s.setInstantPayoutFeeRate(req.instantPayoutFeeRate());
        s.setCargoHandlingFeeRate(req.cargoHandlingFeeRate());
        s.setFeaturedPlacementFee(req.featuredPlacementFee());
        s.setFeaturedPlacementDays(req.featuredPlacementDays());
        s.setFeaturedPlacementCurrency(req.featuredPlacementCurrency());
        if (req.logistics() != null) {
            s.setLogistics(new EnumMap<>(req.logistics()));
        }
        FeeSettings saved = repo.save(s);
        log.info("Fee settings updated: serviceFee={} commissionMarkup={} processing={}+{}(buffer {}) grossUp={}",
                saved.getServiceFeeRate(), saved.getLogisticsMarginRate(), saved.getProcessingRate(),
                saved.getProcessingFixed(), saved.getProcessingBufferRate(), saved.isGrossUpProcessing());
        return saved;
    }

    private FeeSettings transientDefaults() {
        FeeSettings s = new FeeSettings();
        s.setServiceFeeRate(bootstrap.getServiceFeeRate());
        s.setProcessingRate(bootstrap.getProcessingRate());
        s.setProcessingFixed(bootstrap.getProcessingFixed());
        s.setProcessingBufferRate(bootstrap.getProcessingBufferRate());
        s.setGrossUpProcessing(bootstrap.isGrossUpProcessing());
        s.setLogisticsMarginRate(bootstrap.getLogisticsMarginRate());
        s.setLogisticsMarkupFlat(bootstrap.getLogisticsMarkupFlat());
        s.setTaxRate(bootstrap.getTaxRate());
        s.setInstantPayoutFeeRate(bootstrap.getInstantPayoutFeeRate());
        s.setCargoHandlingFeeRate(bootstrap.getCargoHandlingFeeRate());
        s.setFeaturedPlacementFee(bootstrap.getFeaturedPlacementFee());
        s.setFeaturedPlacementDays(bootstrap.getFeaturedPlacementDays());
        s.setFeaturedPlacementCurrency(bootstrap.getFeaturedPlacementCurrency());
        s.setLogistics(new EnumMap<>(bootstrap.getLogistics()));
        return s;
    }
}