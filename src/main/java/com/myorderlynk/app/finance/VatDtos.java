package com.myorderlynk.app.finance;

import com.myorderlynk.app.common.enums.VatCollector;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class VatDtos {

    private VatDtos() {
    }

    public record VatLedgerEntryResponse(
            UUID id,
            UUID orderId,
            String publicOrderId,
            UUID vendorId,
            VatCollector collector,
            BigDecimal taxableAmount,
            BigDecimal amount,
            String currency,
            boolean remitted,
            Instant remittedAt,
            Instant createdAt) {
    }

    /** Aggregate view over a set of ledger entries: totals collected, remitted, and outstanding. */
    public record VatLedgerSummary(
            String currency,
            BigDecimal totalCollected,
            BigDecimal totalRemitted,
            BigDecimal outstanding,
            int entryCount,
            List<VatLedgerEntryResponse> entries) {
    }
}
