package com.myorderlynk.app.order;

import com.myorderlynk.app.common.enums.FulfillmentType;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.Map;

/**
 * <strong>Bootstrap</strong> fee defaults, bound from {@code app.fees.*}. These seed the
 * single {@link FeeSettings} row on first boot (see {@link FeeSettingsService}); thereafter the
 * live policy is the DB row, which an admin edits via {@code /api/admin/fee-settings}. Changing a
 * value here only affects environments that have not yet been seeded.
 */
@ConfigurationProperties(prefix = "app.fees")
public class FeeProperties {

    /** Customer-facing platform service fee as a fraction of product subtotal. */
    private BigDecimal serviceFeeRate = new BigDecimal("0.03");

    /** Payment processor percentage fee (Stripe-like 2.9%). */
    private BigDecimal processingRate = new BigDecimal("0.029");

    /** Payment processor fixed fee per transaction. */
    private BigDecimal processingFixed = new BigDecimal("0.30");

    /** Extra buffer over {@link #processingRate} to cover cross-border + FX conversion costs. */
    private BigDecimal processingBufferRate = new BigDecimal("0.005");

    /** Whether to gross up the processing fee so the processor's cut on the grand total is recovered. */
    private boolean grossUpProcessing = true;

    /** Platform markup retained on logistics, as a fraction of the base (carrier/flat) cost, added on top. */
    private BigDecimal logisticsMarginRate = new BigDecimal("0.12");

    /** Flat platform markup added per shipment, on top of {@link #logisticsMarginRate} (0 = none). */
    private BigDecimal logisticsMarkupFlat = new BigDecimal("0.00");

    /** Tax withheld from the vendor's net earnings (fraction). 0 = no tax withholding. */
    private BigDecimal taxRate = new BigDecimal("0.00");

    /** Phase 3: fee for an instant (early) vendor payout, as a fraction of the payout amount. */
    private BigDecimal instantPayoutFeeRate = new BigDecimal("0.01");

    /** Phase 3: platform cargo handling fee, as a fraction of a shipment's base charge. */
    private BigDecimal cargoHandlingFeeRate = new BigDecimal("0.02");

    /** Phase 3: price of one featured-placement slot. */
    private BigDecimal featuredPlacementFee = new BigDecimal("25.00");

    /** Phase 3: duration a featured-placement purchase lasts. */
    private int featuredPlacementDays = 7;

    /** Phase 3: billing currency for featured placement. */
    private String featuredPlacementCurrency = "CAD";

    /** Flat logistics fee per fulfillment type. */
    private Map<FulfillmentType, BigDecimal> logistics = defaultLogistics();

    private static Map<FulfillmentType, BigDecimal> defaultLogistics() {
        Map<FulfillmentType, BigDecimal> m = new EnumMap<>(FulfillmentType.class);
        m.put(FulfillmentType.LOCAL_PICKUP, BigDecimal.ZERO);
        m.put(FulfillmentType.LOCAL_DELIVERY, new BigDecimal("8.00"));
        m.put(FulfillmentType.DOMESTIC_SHIPPING, new BigDecimal("15.00"));
        m.put(FulfillmentType.IMPORT_BATCH, new BigDecimal("25.00"));
        m.put(FulfillmentType.EXPORT_BATCH, new BigDecimal("30.00"));
        return m;
    }

    public BigDecimal getServiceFeeRate() {
        return serviceFeeRate;
    }

    public void setServiceFeeRate(BigDecimal serviceFeeRate) {
        this.serviceFeeRate = serviceFeeRate;
    }

    public BigDecimal getProcessingRate() {
        return processingRate;
    }

    public void setProcessingRate(BigDecimal processingRate) {
        this.processingRate = processingRate;
    }

    public BigDecimal getProcessingFixed() {
        return processingFixed;
    }

    public void setProcessingFixed(BigDecimal processingFixed) {
        this.processingFixed = processingFixed;
    }

    public BigDecimal getProcessingBufferRate() {
        return processingBufferRate;
    }

    public void setProcessingBufferRate(BigDecimal processingBufferRate) {
        this.processingBufferRate = processingBufferRate;
    }

    public boolean isGrossUpProcessing() {
        return grossUpProcessing;
    }

    public void setGrossUpProcessing(boolean grossUpProcessing) {
        this.grossUpProcessing = grossUpProcessing;
    }

    public BigDecimal getLogisticsMarginRate() {
        return logisticsMarginRate;
    }

    public void setLogisticsMarginRate(BigDecimal logisticsMarginRate) {
        this.logisticsMarginRate = logisticsMarginRate;
    }

    public BigDecimal getLogisticsMarkupFlat() {
        return logisticsMarkupFlat;
    }

    public void setLogisticsMarkupFlat(BigDecimal logisticsMarkupFlat) {
        this.logisticsMarkupFlat = logisticsMarkupFlat;
    }

    public BigDecimal getTaxRate() {
        return taxRate;
    }

    public void setTaxRate(BigDecimal taxRate) {
        this.taxRate = taxRate;
    }

    public BigDecimal getInstantPayoutFeeRate() {
        return instantPayoutFeeRate;
    }

    public void setInstantPayoutFeeRate(BigDecimal instantPayoutFeeRate) {
        this.instantPayoutFeeRate = instantPayoutFeeRate;
    }

    public BigDecimal getCargoHandlingFeeRate() {
        return cargoHandlingFeeRate;
    }

    public void setCargoHandlingFeeRate(BigDecimal cargoHandlingFeeRate) {
        this.cargoHandlingFeeRate = cargoHandlingFeeRate;
    }

    public BigDecimal getFeaturedPlacementFee() {
        return featuredPlacementFee;
    }

    public void setFeaturedPlacementFee(BigDecimal featuredPlacementFee) {
        this.featuredPlacementFee = featuredPlacementFee;
    }

    public int getFeaturedPlacementDays() {
        return featuredPlacementDays;
    }

    public void setFeaturedPlacementDays(int featuredPlacementDays) {
        this.featuredPlacementDays = featuredPlacementDays;
    }

    public String getFeaturedPlacementCurrency() {
        return featuredPlacementCurrency;
    }

    public void setFeaturedPlacementCurrency(String featuredPlacementCurrency) {
        this.featuredPlacementCurrency = featuredPlacementCurrency;
    }

    public Map<FulfillmentType, BigDecimal> getLogistics() {
        return logistics;
    }

    public void setLogistics(Map<FulfillmentType, BigDecimal> logistics) {
        this.logistics = logistics;
    }
}