package com.myorderlynk.app.order;

import com.myorderlynk.app.common.BaseEntity;
import com.myorderlynk.app.common.enums.FulfillmentType;
import com.myorderlynk.app.common.enums.PaymentMethod;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.MapKeyEnumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.EnumMap;
import java.util.Map;

/**
 * DB-backed, admin-configurable fee policy. A single row holds the platform's live fee
 * configuration; {@link FeeSettingsService} reads it and seeds it from {@link FeeProperties}
 * bootstrap defaults on first boot. The pure fee helpers live here so {@link FeeCalculator}
 * stays a thin orchestrator. Field defaults mirror {@link FeeProperties} so a fresh instance is
 * already a valid policy (used as the fallback before the row is seeded, and in unit tests).
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "fee_settings")
public class FeeSettings extends BaseEntity {

    /** Customer-facing platform service fee as a fraction of product subtotal. */
    @Column(nullable = false)
    private BigDecimal serviceFeeRate = new BigDecimal("0.03");

    /** Payment processor percentage fee (Stripe-like 2.9%). */
    @Column(nullable = false)
    private BigDecimal processingRate = new BigDecimal("0.029");

    /** Payment processor fixed fee per transaction. */
    @Column(nullable = false)
    private BigDecimal processingFixed = new BigDecimal("0.30");

    /** Extra buffer over {@link #processingRate} for cross-border + FX conversion costs. */
    @Column(nullable = false)
    private BigDecimal processingBufferRate = new BigDecimal("0.005");

    /** Gross up the processing fee so the processor's cut on the grand total is recovered. */
    @Column(nullable = false)
    private boolean grossUpProcessing = true;

    /** Platform markup retained on logistics, a fraction of the base cost, added on top. */
    @Column(nullable = false)
    private BigDecimal logisticsMarginRate = new BigDecimal("0.12");

    /** Flat platform markup per shipment, on top of {@link #logisticsMarginRate}. */
    @Column(nullable = false)
    private BigDecimal logisticsMarkupFlat = new BigDecimal("0.00");

    /** Tax withheld from the vendor's net earnings (fraction). 0 = no withholding. */
    @Column(nullable = false)
    private BigDecimal taxRate = new BigDecimal("0.00");

    // --- Phase 3: value-added service pricing ---

    /** Fee for an instant (early) vendor payout, as a fraction of the payout amount (e.g. 0.01 = 1%). */
    @Column(nullable = false)
    private BigDecimal instantPayoutFeeRate = new BigDecimal("0.01");

    /** Platform cargo handling/sourcing fee, as a fraction of a shipment's base charge, added on top. */
    @Column(nullable = false)
    private BigDecimal cargoHandlingFeeRate = new BigDecimal("0.02");

    /** Price of one featured-placement slot in the marketplace. */
    @Column(nullable = false)
    private BigDecimal featuredPlacementFee = new BigDecimal("25.00");

    /** Duration a single featured-placement purchase lasts. */
    @Column(nullable = false)
    private int featuredPlacementDays = 7;

    /** Billing currency for featured placement. */
    @Column(nullable = false, length = 8)
    private String featuredPlacementCurrency = "CAD";

    /** Flat logistics fee per fulfillment type (base carrier cost when no live rate is available). */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "fee_settings_logistics", joinColumns = @JoinColumn(name = "fee_settings_id"))
    @MapKeyEnumerated(EnumType.STRING)
    @MapKeyColumn(name = "fulfillment_type")
    @Column(name = "fee", nullable = false)
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

    // --- pure fee helpers ---

    /** Flat logistics fee for a fulfillment type (the base carrier cost when no live rate is given). */
    public BigDecimal logisticsFeeFor(FulfillmentType type) {
        return logistics.getOrDefault(type, BigDecimal.ZERO);
    }

    /** Whether a payment method incurs a processor fee (card/stripe do; e-transfer/cash do not). */
    public boolean hasProcessingFee(PaymentMethod method) {
        return method == PaymentMethod.CARD || method == PaymentMethod.STRIPE;
    }

    /** Platform cargo handling fee for a shipment's {@code baseCharge} (0 when there is no charge). */
    public BigDecimal cargoHandlingFeeFor(BigDecimal baseCharge) {
        if (baseCharge == null || baseCharge.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        return baseCharge.multiply(cargoHandlingFeeRate).setScale(2, RoundingMode.HALF_UP);
    }

    /** Fee charged to expedite a vendor payout of {@code payoutAmount} (0 when amount is non-positive). */
    public BigDecimal instantPayoutFeeFor(BigDecimal payoutAmount) {
        if (payoutAmount == null || payoutAmount.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        return payoutAmount.multiply(instantPayoutFeeRate).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Platform markup to add on top of a base logistics cost (carrier rate or flat fee).
     * Zero when there is no logistics cost (e.g. local pickup), so pickups carry no flat markup.
     */
    public BigDecimal logisticsMarkupFor(BigDecimal baseLogistics) {
        if (baseLogistics == null || baseLogistics.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        return baseLogistics.multiply(logisticsMarginRate).add(logisticsMarkupFlat);
    }

    /**
     * Processing fee to charge on a pre-processing {@code base} (subtotal + logistics + platform fee).
     * <p>The base (non-grossed-up) fee is {@code base * (processingRate + processingBufferRate) + processingFixed}.
     * When {@link #grossUpProcessing} is set, this is divided by {@code (1 - effectiveRate)} so that after the
     * processor takes its percentage on the grand total (base + this fee), the platform still nets {@code base}:
     * <pre>fee = (base * r + fixed) / (1 - r),  r = processingRate + processingBufferRate</pre>
     */
    public BigDecimal processingFeeFor(BigDecimal base) {
        BigDecimal rate = processingRate.add(processingBufferRate);
        BigDecimal fee = base.multiply(rate).add(processingFixed);
        if (grossUpProcessing) {
            BigDecimal net = BigDecimal.ONE.subtract(rate);
            // Guard against a pathological rate >= 1 (would make the gross-up blow up / go negative).
            if (net.signum() > 0) {
                fee = fee.divide(net, 10, RoundingMode.HALF_UP);
            }
        }
        return fee;
    }
}