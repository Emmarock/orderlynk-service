package com.myorderlynk.app.order;

import com.myorderlynk.app.common.enums.FulfillmentType;
import com.myorderlynk.app.common.enums.PaymentMethod;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Implements the fee logic from Appendix A:
 * <pre>
 * Total Amount    = Product Subtotal + Logistics Fee + Platform Fee + Processing Fee
 * Vendor Payable  = Product Subtotal - Commission - Refunds
 * Platform Revenue= Platform Fee + Commission + Logistics Margin
 * </pre>
 */
@Service
public class FeeCalculator {

    private final FeeProperties props;

    public FeeCalculator(FeeProperties props) {
        this.props = props;
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
     * (e.g. a live carrier rate fetched at checkout).
     */
    public FeeBreakdown calculate(BigDecimal productSubtotal,
                                  FulfillmentType fulfillmentType,
                                  PaymentMethod paymentMethod,
                                  BigDecimal vendorCommissionRate,
                                  BigDecimal logisticsOverride) {
        BigDecimal subtotal = scale(productSubtotal);
        BigDecimal logisticsFee = scale(logisticsOverride != null ? logisticsOverride
                : props.logisticsFeeFor(fulfillmentType));
        BigDecimal platformFee = scale(subtotal.multiply(props.getServiceFeeRate()));

        BigDecimal preProcessing = subtotal.add(logisticsFee).add(platformFee);
        BigDecimal processingFee = BigDecimal.ZERO;
        if (props.hasProcessingFee(paymentMethod)) {
            processingFee = scale(preProcessing.multiply(props.getProcessingRate()).add(props.getProcessingFixed()));
        }

        BigDecimal total = scale(subtotal.add(logisticsFee).add(platformFee).add(processingFee));

        BigDecimal commission = scale(subtotal.multiply(vendorCommissionRate));
        BigDecimal vendorPayable = scale(subtotal.subtract(commission));

        BigDecimal logisticsMargin = scale(logisticsFee.multiply(props.getLogisticsMarginRate()));
        BigDecimal logisticsPayable = scale(logisticsFee.subtract(logisticsMargin));
        BigDecimal platformRevenue = scale(platformFee.add(commission).add(logisticsMargin));

        return new FeeBreakdown(subtotal, logisticsFee, platformFee, processingFee, total,
                vendorPayable, logisticsPayable, platformRevenue);
    }

    private static BigDecimal scale(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    public record FeeBreakdown(
            BigDecimal productSubtotal,
            BigDecimal logisticsFee,
            BigDecimal platformFee,
            BigDecimal processingFee,
            BigDecimal totalAmount,
            BigDecimal vendorPayable,
            BigDecimal logisticsPayable,
            BigDecimal platformRevenue) {
    }
}
