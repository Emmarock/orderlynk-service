package com.myorderlynk.app.booking;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AvailabilityRuleRepository extends JpaRepository<AvailabilityRule, UUID> {
    List<AvailabilityRule> findByVendorId(UUID vendorId);

    List<AvailabilityRule> findByVendorIdAndActiveTrue(UUID vendorId);

    /** Shop-wide rules only (no staff member). */
    List<AvailabilityRule> findByVendorIdAndStaffIdIsNull(UUID vendorId);

    /** Rules belonging to one worker. */
    List<AvailabilityRule> findByVendorIdAndStaffId(UUID vendorId, UUID staffId);

    /** Active shop-wide rules only — used by the availability engine's shop-level fallback. */
    List<AvailabilityRule> findByVendorIdAndActiveTrueAndStaffIdIsNull(UUID vendorId);

    /** Active rules belonging to one worker. */
    List<AvailabilityRule> findByVendorIdAndActiveTrueAndStaffId(UUID vendorId, UUID staffId);

    void deleteByStaffId(UUID staffId);
}
