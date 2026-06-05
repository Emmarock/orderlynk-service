package com.myorderlynk.app.repository;

import com.myorderlynk.app.domain.Vendor;
import com.myorderlynk.app.domain.enums.VendorStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VendorRepository extends JpaRepository<Vendor, UUID> {
    Optional<Vendor> findByStoreSlug(String storeSlug);

    boolean existsByStoreSlug(String storeSlug);

    Optional<Vendor> findByOwnerUserId(UUID ownerUserId);

    List<Vendor> findByVerificationStatus(VendorStatus status);

    List<Vendor> findByActiveTrueAndVerificationStatus(VendorStatus status);

    List<Vendor> findByActiveTrueAndVerificationStatusAndCityIgnoreCase(VendorStatus status, String city);
}
