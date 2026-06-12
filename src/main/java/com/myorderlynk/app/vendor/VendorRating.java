package com.myorderlynk.app.vendor;
import com.myorderlynk.app.common.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * A single customer's rating of a vendor (1–5 stars, optional comment).
 * One row per (vendor, customer) — re-rating updates the existing row. The
 * vendor's denormalized average ({@link Vendor#getRating()}) and count are
 * recomputed whenever a rating is saved.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "vendor_ratings", indexes = {
        @Index(name = "idx_vendor_rating_vendor", columnList = "vendorId"),
        @Index(name = "uq_vendor_rating_customer", columnList = "vendorId,customerUserId", unique = true)
})
public class VendorRating extends BaseEntity {

    @Column(nullable = false)
    private UUID vendorId;

    @Column(nullable = false)
    private UUID customerUserId;

    @Column(nullable = false)
    private int stars;

    @Column(length = 1000)
    private String comment;
}