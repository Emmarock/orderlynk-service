package com.myorderlynk.app.payment;

import com.myorderlynk.app.order.Order;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Settlement routing: an order's payment allocations must mirror the FeeCalculator economics so the
 * vendor is paid their net and the platform's revenue (commission + service fee + logistics markup)
 * lands in PLATFORM_FEE. The buckets must also sum exactly to the gross (payment-service validates this).
 */
class PaymentClientAllocationTest {

    private Order order() {
        Order o = new Order();
        // $100 subtotal, 7% commission -> vendorPayable 93; 3% service fee = 3.00;
        // logistics fee 11.20 (carrier 10.00 + 1.20 markup); processing 3.94. Total = 118.14.
        o.setProductSubtotal(new BigDecimal("100.00"));
        o.setVendorPayable(new BigDecimal("93.00"));
        o.setPlatformFee(new BigDecimal("3.00"));
        o.setLogisticsFee(new BigDecimal("11.20"));
        o.setLogisticsPayable(new BigDecimal("10.00"));
        o.setProcessingFee(new BigDecimal("3.94"));
        o.setPlatformRevenue(new BigDecimal("11.20")); // 3 service + 7 commission + 1.20 markup
        o.setTotalAmount(new BigDecimal("118.14"));
        return o;
    }

    @Test
    void vendorGetsNetAndPlatformGetsCommissionPlusServiceFeePlusMarkup() {
        Map<String, BigDecimal> a = PaymentClient.orderAllocations(order());

        assertThat(a.get("PRODUCT")).isEqualByComparingTo("93.00");      // vendorPayable (commission removed)
        assertThat(a.get("LOGISTICS")).isEqualByComparingTo("10.00");    // carrier cost only
        assertThat(a.get("PLATFORM_FEE")).isEqualByComparingTo("11.20"); // service 3 + commission 7 + markup 1.20
        assertThat(a.get("PROCESSING_FEE")).isEqualByComparingTo("3.94");
    }

    @Test
    void platformFeeEqualsRecordedPlatformRevenueAndBucketsSumToGross() {
        Order o = order();
        Map<String, BigDecimal> a = PaymentClient.orderAllocations(o);

        assertThat(a.get("PLATFORM_FEE")).isEqualByComparingTo(o.getPlatformRevenue());

        BigDecimal sum = a.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(sum).isEqualByComparingTo(o.getTotalAmount());
    }

    @Test
    void pickupOrderWithNoLogisticsOrCommission() {
        Order o = new Order();
        o.setProductSubtotal(new BigDecimal("50.00"));
        o.setVendorPayable(new BigDecimal("50.00")); // no commission (e.g. 0%)
        o.setPlatformFee(new BigDecimal("1.50"));
        o.setLogisticsFee(BigDecimal.ZERO);
        o.setLogisticsPayable(BigDecimal.ZERO);
        o.setProcessingFee(BigDecimal.ZERO);
        o.setTotalAmount(new BigDecimal("51.50"));

        Map<String, BigDecimal> a = PaymentClient.orderAllocations(o);

        assertThat(a).doesNotContainKey("LOGISTICS");
        assertThat(a.get("PRODUCT")).isEqualByComparingTo("50.00");
        assertThat(a.get("PLATFORM_FEE")).isEqualByComparingTo("1.50");
        assertThat(a.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo(o.getTotalAmount());
    }
}
