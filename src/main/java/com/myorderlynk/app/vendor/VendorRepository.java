package com.myorderlynk.app.vendor;

import com.myorderlynk.app.vendor.Vendor;
import com.myorderlynk.app.common.enums.VendorStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VendorRepository extends JpaRepository<Vendor, UUID> {
    Optional<Vendor> findByStoreSlug(String storeSlug);

    boolean existsByStoreSlug(String storeSlug);

    Optional<Vendor> findByOwnerUserId(UUID ownerUserId);

    List<Vendor> findByVerificationStatus(VendorStatus status);

    Page<Vendor> findByVerificationStatus(VendorStatus status, Pageable pageable);

    long countByActiveTrue();

    long countByVerificationStatusIn(Collection<VendorStatus> statuses);

    /** Live (active + approved) vendors — the "verified vendors" headline for the public home page. */
    long countByActiveTrueAndVerificationStatus(VendorStatus status);

    /** Distinct, non-blank cities served by live vendors (case-insensitive) — for the public home page. */
    @Query("select count(distinct lower(v.address.city)) from Vendor v " +
            "where v.active = true and v.verificationStatus = :status " +
            "and v.address.city is not null and v.address.city <> ''")
    long countDistinctCities(VendorStatus status);

    /** Vendors in any of the given statuses, newest application first — for the admin approval queue. */
    List<Vendor> findByVerificationStatusInOrderByCreatedAtDesc(Collection<VendorStatus> statuses, Pageable pageable);

    List<Vendor> findByActiveTrueAndVerificationStatus(VendorStatus status);

    List<Vendor> findByActiveTrueAndVerificationStatusAndAddressCityIgnoreCase(VendorStatus status, String city);
}
