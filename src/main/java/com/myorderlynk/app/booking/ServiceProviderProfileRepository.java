package com.myorderlynk.app.booking;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ServiceProviderProfileRepository extends JpaRepository<ServiceProviderProfile, UUID> {
    Optional<ServiceProviderProfile> findByVendorId(UUID vendorId);

    boolean existsByVendorIdAndServiceEnabledTrue(UUID vendorId);
}
