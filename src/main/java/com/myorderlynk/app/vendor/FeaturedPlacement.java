package com.myorderlynk.app.vendor;

import com.myorderlynk.app.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A single featured-placement purchase: a vendor pays to be promoted in marketplace discovery for a
 * window. The purchase extends {@code Vendor.featuredUntil}; this row is the billing ledger entry,
 * settled like a subscription invoice (created {@code DUE}, then {@code PAID}/{@code WAIVED}).
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "featured_placements", indexes = @Index(name = "idx_featured_vendor", columnList = "vendor_id"))
public class FeaturedPlacement extends BaseEntity {

    @Column(name = "vendor_id", nullable = false)
    private UUID vendorId;

    @Column(nullable = false)
    private int days;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(nullable = false, length = 8)
    private String currency;

    /** Featured window opened by this purchase. */
    @Column(nullable = false)
    private Instant startsAt;

    @Column(nullable = false)
    private Instant endsAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SubscriptionInvoiceStatus status = SubscriptionInvoiceStatus.DUE;

    private Instant paidAt;

    private String reference;

    public FeaturedPlacement(UUID vendorId, int days, BigDecimal amount, String currency,
                             Instant startsAt, Instant endsAt) {
        this.vendorId = vendorId;
        this.days = days;
        this.amount = amount;
        this.currency = currency;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.status = SubscriptionInvoiceStatus.DUE;
    }
}
