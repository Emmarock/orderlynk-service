package com.myorderlynk.app.finance;

import com.myorderlynk.app.common.enums.VatCollector;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Persistence for the VAT ledger (one entry per VAT-charging order). */
public interface VatLedgerRepository extends JpaRepository<VatLedgerEntry, UUID> {

    List<VatLedgerEntry> findByVendorIdAndCreatedAtBetweenOrderByCreatedAtDesc(UUID vendorId, Instant from, Instant to);

    List<VatLedgerEntry> findByCollectorAndCreatedAtBetweenOrderByCreatedAtDesc(VatCollector collector, Instant from, Instant to);

    boolean existsByOrderId(UUID orderId);
}
