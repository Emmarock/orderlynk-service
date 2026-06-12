package com.myorderlynk.app.vendor;

import com.myorderlynk.app.vendor.VendorRating;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface VendorRatingRepository extends JpaRepository<VendorRating, UUID> {

    Optional<VendorRating> findByVendorIdAndCustomerUserId(UUID vendorId, UUID customerUserId);

    /** Aggregate projection for recomputing a vendor's denormalized rating. */
    interface RatingAggregate {
        Double getAverage();

        long getCount();
    }

    @Query("select avg(r.stars) as average, count(r) as count from VendorRating r where r.vendorId = :vendorId")
    RatingAggregate aggregateForVendor(UUID vendorId);
}