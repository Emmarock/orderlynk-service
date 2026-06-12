package com.myorderlynk.app.booking;

import com.myorderlynk.app.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

/** A selected add-on captured on a {@link Booking} (snapshot of the {@link ServiceAddOn} at booking time). */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "booking_add_ons")
public class BookingAddOn extends BaseEntity {

    @ManyToOne(optional = false)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    private UUID addOnId;

    @Column(nullable = false)
    private String nameSnapshot;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal priceDelta = BigDecimal.ZERO;

    @Column(nullable = false)
    private int durationDelta = 0;

    @Column(nullable = false)
    private int quantity = 1;
}
