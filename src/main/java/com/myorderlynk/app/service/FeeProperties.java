package com.myorderlynk.app.service;

import com.myorderlynk.app.domain.enums.FulfillmentType;
import com.myorderlynk.app.domain.enums.PaymentMethod;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.Map;

/**
 * Tunable fee policy, bound from {@code app.fees.*}. Defaults give a sensible
 * platform-managed-checkout model (PRD §11 recommended MVP model).
 */
@ConfigurationProperties(prefix = "app.fees")
public class FeeProperties {

    /** Customer-facing platform service fee as a fraction of product subtotal. */
    private BigDecimal serviceFeeRate = new BigDecimal("0.03");

    /** Payment processor percentage fee (Stripe-like 2.9%). */
    private BigDecimal processingRate = new BigDecimal("0.029");

    /** Payment processor fixed fee per transaction. */
    private BigDecimal processingFixed = new BigDecimal("0.30");

    /** Platform margin retained from the logistics fee. */
    private BigDecimal logisticsMarginRate = new BigDecimal("0.00");

    /** Tax withheld from the vendor's net earnings (fraction). 0 = no tax withholding. */
    private BigDecimal taxRate = new BigDecimal("0.00");

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

    public BigDecimal logisticsFeeFor(FulfillmentType type) {
        return logistics.getOrDefault(type, BigDecimal.ZERO);
    }

    /** Whether a payment method incurs a processor fee (card/stripe do; e-transfer/cash do not). */
    public boolean hasProcessingFee(PaymentMethod method) {
        return method == PaymentMethod.CARD || method == PaymentMethod.STRIPE;
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

    public BigDecimal getLogisticsMarginRate() {
        return logisticsMarginRate;
    }

    public void setLogisticsMarginRate(BigDecimal logisticsMarginRate) {
        this.logisticsMarginRate = logisticsMarginRate;
    }

    public BigDecimal getTaxRate() {
        return taxRate;
    }

    public void setTaxRate(BigDecimal taxRate) {
        this.taxRate = taxRate;
    }

    public Map<FulfillmentType, BigDecimal> getLogistics() {
        return logistics;
    }

    public void setLogistics(Map<FulfillmentType, BigDecimal> logistics) {
        this.logistics = logistics;
    }
}
