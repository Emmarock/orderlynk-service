package com.myorderlynk.app.dto;

import com.myorderlynk.app.domain.enums.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** DTOs for the vendor finance / earnings dashboard. */
public final class FinanceDtos {

    private FinanceDtos() {
    }

    /**
     * Earnings rollup for a vendor over a date range. Headline figures are realized
     * (computed from PAID orders); {@code orders} lists every order in the range so the
     * vendor can also see pending earnings.
     */
    public record EarningsSummary(
            BigDecimal grossSales,
            BigDecimal platformCommission,
            BigDecimal processingFees,
            BigDecimal refunds,
            BigDecimal taxRate,
            BigDecimal tax,
            BigDecimal netPayout,
            long totalOrders,
            long paidOrders,
            String currency,
            List<OrderEarning> orders) {
    }

    /** Per-order earnings line for the breakdown table. */
    public record OrderEarning(
            String publicOrderId,
            Instant createdAt,
            PaymentStatus paymentStatus,
            BigDecimal grossSales,
            BigDecimal commission,
            BigDecimal refund,
            BigDecimal net) {
    }
}