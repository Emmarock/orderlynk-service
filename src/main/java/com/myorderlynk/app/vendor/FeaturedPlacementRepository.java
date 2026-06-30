package com.myorderlynk.app.vendor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/** Persistence for featured-placement purchases (the promotions ledger). */
public interface FeaturedPlacementRepository extends JpaRepository<FeaturedPlacement, UUID> {

    List<FeaturedPlacement> findByVendorIdOrderByStartsAtDesc(UUID vendorId);

    Page<FeaturedPlacement> findByStatus(SubscriptionInvoiceStatus status, Pageable pageable);

    Page<FeaturedPlacement> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
