package com.myorderlynk.app.booking;

import com.myorderlynk.app.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * A sub-service / variant of a parent {@link ServiceOffering} — e.g. a "Braiding" service offering
 * "Braid with gel", "1 Million braids", "Weaving" options, each with its own price and duration.
 * Variants are mutually exclusive: the customer picks exactly one at booking and it sets the base
 * price (add-ons still apply on top). A service with no variants behaves as a single flat-priced one.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "service_variants", indexes = @Index(name = "idx_variant_service", columnList = "serviceId"))
public class ServiceVariant extends BaseEntity {

    @Column(nullable = false)
    private UUID serviceId;

    @Column(nullable = false)
    private UUID vendorId;

    @Column(nullable = false)
    private String name;

    /** Absolute price of this sub-service (replaces the parent's base price when chosen). */
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal price = BigDecimal.ZERO;

    /** How long this variant blocks the calendar (overrides the parent service duration). */
    @Column(nullable = false)
    private int durationMinutes = 60;

    @Column(nullable = false)
    private boolean active = true;
}