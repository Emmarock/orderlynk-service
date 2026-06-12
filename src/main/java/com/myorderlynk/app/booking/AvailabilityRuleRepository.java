package com.myorderlynk.app.booking;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AvailabilityRuleRepository extends JpaRepository<AvailabilityRule, UUID> {
    List<AvailabilityRule> findByVendorId(UUID vendorId);

    List<AvailabilityRule> findByVendorIdAndActiveTrue(UUID vendorId);
}
