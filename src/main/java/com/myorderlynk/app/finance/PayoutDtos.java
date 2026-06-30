package com.myorderlynk.app.finance;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public final class PayoutDtos {

    private PayoutDtos() {
    }

    public record GeneratePayoutRequest(
            @NotNull UUID vendorId,
            @NotNull LocalDate periodStart,
            @NotNull LocalDate periodEnd) {
    }

    public record PayoutResponse(
            UUID id,
            UUID vendorId,
            LocalDate periodStart,
            LocalDate periodEnd,
            BigDecimal grossSales,
            BigDecimal platformFees,
            BigDecimal logisticsFees,
            BigDecimal refunds,
            BigDecimal netPayout,
            String payoutStatus,
            Instant paidDate,
            boolean instantPayout,
            BigDecimal instantPayoutFee) {
    }
}
