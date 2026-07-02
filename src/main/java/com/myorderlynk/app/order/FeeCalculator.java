package com.myorderlynk.app.order;

import com.myorderlynk.app.common.enums.FulfillmentType;
import com.myorderlynk.app.common.enums.PaymentMethod;
import com.myorderlynk.app.common.enums.VatCollector;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Implements the fee logic from Appendix A:
 * <pre>
 * Total Amount    = Product Subtotal + VAT + Logistics Fee + Platform Fee + Processing Fee
 * Vendor Payable  = Product Subtotal - Commission - Refunds (+ VAT when the vendor collects it)
 * Platform Revenue= Platform Fee + Commission + Logistics Margin
 * </pre>
 * VAT is a pass-through tax, never platform revenue: it is added to the vendor payout when the
 * vendor is the collector, otherwise the platform holds it as a liability (tracked in the VAT ledger).
 * Commission is charged on the product subtotal only — never on VAT.
 */
@Service
public class FeeCalculator {

    private final FeeSettingsService settings;

    public FeeCalculator(FeeSettingsService settings) {
        this.settings = settings;
    }

    public FeeBreakdown calculate(BigDecimal productSubtotal,
                                  FulfillmentType fulfillmentType,
                                  PaymentMethod paymentMethod,
                                  BigDecimal vendorCommissionRate) {
        return calculate(productSubtotal, fulfillmentType, paymentMethod, vendorCommissionRate, null);
    }

    /**
     * As {@link #calculate(BigDecimal, FulfillmentType, PaymentMethod, BigDecimal)}, but when
     * {@code logisticsOverride} is non-null it replaces the flat per-fulfillment logistics fee
     * (e.g. a live carrier rate fetched at checkout). VAT-free (kept for callers with no VAT).
     */
    public FeeBreakdown calculate(BigDecimal productSubtotal,
                                  FulfillmentType fulfillmentType,
                                  PaymentMethod paymentMethod,
                                  BigDecimal vendorCommissionRate,
                                  BigDecimal logisticsOverride) {
        return calculate(productSubtotal, BigDecimal.ZERO, VatCollector.VENDOR,
                fulfillmentType, paymentMethod, vendorCommissionRate, logisticsOverride);
    }

    /**
     * Full breakdown including VAT. {@code vatAmount} is the total VAT charged on taxable items;
     * {@code vatCollector} routes it — {@link VatCollector#VENDOR} adds it to the vendor payout,
     * {@link VatCollector#PLATFORM} keeps it out of the payout (the platform remits it). VAT is part
     * of the amount the customer pays, so it is included in the processing-fee base and the total,
     * but it is never treated as platform revenue.
     */
    public FeeBreakdown calculate(BigDecimal productSubtotal,
                                  BigDecimal vatAmount,
                                  VatCollector vatCollector,
                                  FulfillmentType fulfillmentType,
                                  PaymentMethod paymentMethod,
                                  BigDecimal vendorCommissionRate,
                                  BigDecimal logisticsOverride) {
        FeeSettings policy = settings.current();
        BigDecimal subtotal = scale(productSubtotal);
        BigDecimal vat = scale(vatAmount == null ? BigDecimal.ZERO : vatAmount);
        // Base logistics is the carrier's actual cost (live rate) or the flat per-fulfillment fee.
        // The platform markup is added on top so the carrier is paid in full and the markup is margin.
        BigDecimal baseLogistics = scale(logisticsOverride != null ? logisticsOverride
                : policy.logisticsFeeFor(fulfillmentType));
        BigDecimal logisticsMargin = scale(policy.logisticsMarkupFor(baseLogistics));
        BigDecimal logisticsFee = scale(baseLogistics.add(logisticsMargin));
        BigDecimal platformFee = scale(subtotal.multiply(policy.getServiceFeeRate()));

        // VAT is money the customer actually pays, so it belongs in the processing-fee base.
        BigDecimal preProcessing = subtotal.add(vat).add(logisticsFee).add(platformFee);
        BigDecimal processingFee = BigDecimal.ZERO;
        if (policy.hasProcessingFee(paymentMethod)) {
            processingFee = scale(policy.processingFeeFor(preProcessing));
        }

        BigDecimal total = scale(subtotal.add(vat).add(logisticsFee).add(platformFee).add(processingFee));

        BigDecimal commission = scale(subtotal.multiply(vendorCommissionRate));
        // The vendor keeps the subtotal net of commission, plus the VAT when it is the collector.
        BigDecimal vendorPayable = scale(subtotal.subtract(commission)
                .add(vatCollector == VatCollector.VENDOR ? vat : BigDecimal.ZERO));

        BigDecimal logisticsPayable = baseLogistics;
        // VAT is a pass-through liability, never platform margin — excluded from platform revenue.
        BigDecimal platformRevenue = scale(platformFee.add(commission).add(logisticsMargin));

        return new FeeBreakdown(subtotal, vat, logisticsFee, platformFee, processingFee, total,
                vendorPayable, logisticsPayable, platformRevenue);
    }

    private static BigDecimal scale(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    public record FeeBreakdown(
            BigDecimal productSubtotal,
            BigDecimal vatAmount,
            BigDecimal logisticsFee,
            BigDecimal platformFee,
            BigDecimal processingFee,
            BigDecimal totalAmount,
            BigDecimal vendorPayable,
            BigDecimal logisticsPayable,
            BigDecimal platformRevenue) {
    }
}
