package com.myorderlynk.app.booking;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ServiceOfferingRepository extends JpaRepository<ServiceOffering, UUID> {
    List<ServiceOffering> findByVendorIdOrderByCreatedAtDesc(UUID vendorId);

    Page<ServiceOffering> findByVendorIdOrderByCreatedAtDesc(UUID vendorId, Pageable pageable);

    List<ServiceOffering> findByVendorIdAndActiveTrue(UUID vendorId);

    /** Vendor ids that currently offer at least one active service in a category (marketplace filter). */
    @Query("select distinct s.vendorId from ServiceOffering s where s.active = true and s.category = :category")
    List<UUID> findVendorIdsByActiveCategory(@Param("category") ServiceCategory category);

    /** Vendor ids that currently offer at least one active service (marketplace listing). */
    @Query("select distinct s.vendorId from ServiceOffering s where s.active = true")
    List<UUID> findVendorIdsWithActiveServices();
}
