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

/** An optional/required add-on that adjusts a service's price and duration (PRD §13 ServiceAddOn). */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "service_add_ons", indexes = @Index(name = "idx_addon_service", columnList = "serviceId"))
public class ServiceAddOn extends BaseEntity {

    @Column(nullable = false)
    private UUID serviceId;

    @Column(nullable = false)
    private UUID vendorId;

    @Column(nullable = false)
    private String name;

    /** Amount added to the service price when selected. */
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal priceDelta = BigDecimal.ZERO;

    /** Extra minutes added to the service duration when selected. */
    @Column(nullable = false)
    private int durationDelta = 0;

    /** Required add-ons are always applied; optional ones are customer-selectable. */
    @Column(nullable = false)
    private boolean required = false;

    /** Maximum times this add-on can be selected on one booking (1 = single). */
    @Column(nullable = false)
    private int maxSelection = 1;

    @Column(nullable = false)
    private boolean active = true;
}
