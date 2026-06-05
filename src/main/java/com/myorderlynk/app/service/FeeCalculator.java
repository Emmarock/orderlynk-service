package com.myorderlynk.app.service;

import com.myorderlynk.app.domain.enums.FulfillmentType;
import com.myorderlynk.app.domain.enums.PaymentMethod;
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
        BigDecimal subtotal = scale(productSubtotal);
        BigDecimal logisticsFee = scale(props.logisticsFeeFor(fulfillmentType));
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
