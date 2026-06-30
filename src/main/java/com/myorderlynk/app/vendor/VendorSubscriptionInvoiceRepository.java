package com.myorderlynk.app.vendor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Persistence for monthly vendor subscription invoices. */
public interface VendorSubscriptionInvoiceRepository extends JpaRepository<VendorSubscriptionInvoice, UUID> {

    /** Idempotency guard for monthly generation. */
    boolean existsByVendorIdAndPeriodStart(UUID vendorId, LocalDate periodStart);

    List<VendorSubscriptionInvoice> findByVendorIdOrderByPeriodStartDesc(UUID vendorId);

    /** All invoices in a status (e.g. DUE) — drives the collection pass. */
    List<VendorSubscriptionInvoice> findByStatusOrderByCreatedAtAsc(SubscriptionInvoiceStatus status);

    Page<VendorSubscriptionInvoice> findByStatus(SubscriptionInvoiceStatus status, Pageable pageable);

    Page<VendorSubscriptionInvoice> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
