package com.myorderlynk.app.booking;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BookingReviewRepository extends JpaRepository<BookingReview, UUID> {
    Optional<BookingReview> findByBookingId(UUID bookingId);

    boolean existsByBookingId(UUID bookingId);

    List<BookingReview> findByVendorIdAndVisibleTrueOrderByCreatedAtDesc(UUID vendorId);

    @Query("select coalesce(avg(r.rating), 0) from BookingReview r where r.vendorId = :vendorId and r.visible = true")
    double averageRating(@Param("vendorId") UUID vendorId);

    long countByVendorIdAndVisibleTrue(UUID vendorId);
}
