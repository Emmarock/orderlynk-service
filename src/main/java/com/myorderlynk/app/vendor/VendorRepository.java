package com.myorderlynk.app.vendor;

import com.myorderlynk.app.vendor.Vendor;
import com.myorderlynk.app.common.enums.VendorStatus;
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

    List<Vendor> findByActiveTrueAndVerificationStatusAndAddressCityIgnoreCase(VendorStatus status, String city);
}
