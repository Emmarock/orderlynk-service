package com.myorderlynk.app.finance;

import com.myorderlynk.app.common.enums.PaymentStatus;

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
            /** Vendor-collected VAT on paid orders — paid out to the vendor on top of {@link #netPayout}
             *  (a pass-through liability, not earnings). {@code netPayout + vatInPayout} is what the
             *  vendor actually receives. Zero when the platform collects the VAT. */
            BigDecimal vatInPayout,
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