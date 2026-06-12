package com.myorderlynk.app.booking;

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
 * A customer's review of a completed booking (PRD §13 Review). One review per booking.
 * Reviews also feed the vendor's denormalized average rating.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "booking_reviews", indexes = {
        @Index(name = "uq_review_booking", columnList = "bookingId", unique = true),
        @Index(name = "idx_review_vendor", columnList = "vendorId")
})
public class BookingReview extends BaseEntity {

    @Column(nullable = false)
    private UUID bookingId;

    @Column(nullable = false)
    private UUID vendorId;

    private UUID serviceId;

    private UUID customerUserId;

    @Column(nullable = false)
    private int rating;

    @Column(length = 1000)
    private String comment;

    @Column(nullable = false)
    private boolean visible = true;
}
