package com.myorderlynk.app.finance;

import com.myorderlynk.app.common.enums.VatCollector;
import com.myorderlynk.app.exception.ApiException;
import com.myorderlynk.app.finance.VatDtos.VatLedgerEntryResponse;
import com.myorderlynk.app.finance.VatDtos.VatLedgerSummary;
import com.myorderlynk.app.order.Order;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Records and reports VAT transactions. Every order that charges VAT produces exactly one ledger
 * entry attributing the amount to whoever remits it (vendor or platform). Reads power the vendor's
 * "VAT to remit" view and the admin's platform-collected VAT view.
 */
@Service
@Slf4j
public class VatLedgerService {

    private final VatLedgerRepository ledger;

    public VatLedgerService(VatLedgerRepository ledger) {
        this.ledger = ledger;
    }

    /**
     * Persist a VAT ledger entry for an order. No-op when the order carries no VAT or an entry
     * already exists for it (idempotent, so a retried checkout can't double-record VAT).
     */
    @Transactional
    public void recordForOrder(Order order) {
        if (order.getVatAmount() == null || order.getVatAmount().signum() <= 0) {
            return;
        }
        if (ledger.existsByOrderId(order.getId())) {
            return;
        }
        VatLedgerEntry entry = new VatLedgerEntry();
        entry.setOrderId(order.getId());
        entry.setPublicOrderId(order.getPublicOrderId());
        entry.setVendorId(order.getVendorId());
        entry.setCollector(order.getVatCollector() == null ? VatCollector.VENDOR : order.getVatCollector());
        entry.setTaxableAmount(order.getProductSubtotal());
        entry.setAmount(order.getVatAmount());
        entry.setCurrency(order.getCurrency());
        ledger.save(entry);
        log.info("VAT ledger: order={} vendor={} collector={} amount={} {}", order.getPublicOrderId(),
                order.getVendorId(), entry.getCollector(), entry.getAmount(), entry.getCurrency());
    }

    /** A vendor's own VAT ledger (VAT it collects and must remit) over a time window. */
    @Transactional(readOnly = true)
    public VatLedgerSummary forVendor(UUID vendorId, Instant from, Instant to) {
        Instant start = from == null ? Instant.EPOCH : from;
        Instant end = to == null ? Instant.now() : to;
        return summarize(ledger.findByVendorIdAndCreatedAtBetweenOrderByCreatedAtDesc(vendorId, start, end));
    }

    /** Platform-collected VAT the platform must remit (collector = PLATFORM) over a time window. */
    @Transactional(readOnly = true)
    public VatLedgerSummary forPlatform(Instant from, Instant to) {
        Instant start = from == null ? Instant.EPOCH : from;
        Instant end = to == null ? Instant.now() : to;
        return summarize(ledger.findByCollectorAndCreatedAtBetweenOrderByCreatedAtDesc(VatCollector.PLATFORM, start, end));
    }

    /**
     * A vendor marks its own VAT entry as remitted to the government. Only entries the vendor
     * collects (collector = VENDOR) and owns can be settled here. Idempotent: re-marking a settled
     * entry is a no-op that returns the current state.
     */
    @Transactional
    public VatLedgerEntryResponse markRemittedByVendor(UUID entryId, UUID vendorId) {
        VatLedgerEntry entry = require(entryId);
        if (entry.getCollector() != VatCollector.VENDOR || !entry.getVendorId().equals(vendorId)) {
            throw ApiException.forbidden("This VAT entry isn't yours to remit");
        }
        return markRemitted(entry);
    }

    /** An admin marks a platform-collected VAT entry as remitted to the government. */
    @Transactional
    public VatLedgerEntryResponse markRemittedByPlatform(UUID entryId) {
        VatLedgerEntry entry = require(entryId);
        if (entry.getCollector() != VatCollector.PLATFORM) {
            throw ApiException.badRequest("This VAT is collected by the vendor, not the platform");
        }
        return markRemitted(entry);
    }

    private VatLedgerEntry require(UUID entryId) {
        return ledger.findById(entryId).orElseThrow(() -> ApiException.notFound("VAT entry not found"));
    }

    private VatLedgerEntryResponse markRemitted(VatLedgerEntry entry) {
        if (!entry.isRemitted()) {
            entry.setRemitted(true);
            entry.setRemittedAt(Instant.now());
            ledger.save(entry);
            log.info("VAT remitted: entry={} order={} collector={} amount={} {}", entry.getId(),
                    entry.getPublicOrderId(), entry.getCollector(), entry.getAmount(), entry.getCurrency());
        }
        return toResponse(entry);
    }

    private VatLedgerSummary summarize(List<VatLedgerEntry> entries) {
        BigDecimal collected = BigDecimal.ZERO;
        BigDecimal remitted = BigDecimal.ZERO;
        for (VatLedgerEntry e : entries) {
            collected = collected.add(e.getAmount());
            if (e.isRemitted()) {
                remitted = remitted.add(e.getAmount());
            }
        }
        String currency = entries.isEmpty() ? "CAD" : entries.get(0).getCurrency();
        List<VatLedgerEntryResponse> mapped = entries.stream().map(VatLedgerService::toResponse).toList();
        return new VatLedgerSummary(currency, collected, remitted, collected.subtract(remitted), entries.size(), mapped);
    }

    private static VatLedgerEntryResponse toResponse(VatLedgerEntry e) {
        return new VatLedgerEntryResponse(e.getId(), e.getOrderId(), e.getPublicOrderId(), e.getVendorId(),
                e.getCollector(), e.getTaxableAmount(), e.getAmount(), e.getCurrency(),
                e.isRemitted(), e.getRemittedAt(), e.getCreatedAt());
    }
}
