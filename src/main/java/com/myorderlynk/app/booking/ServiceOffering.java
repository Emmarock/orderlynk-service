package com.myorderlynk.app.booking;

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
import java.math.RoundingMode;
import java.util.UUID;

/**
 * A bookable service in a provider's catalog (PRD §13 Service). Named {@code ServiceOffering}
 * to avoid colliding with Spring's {@code @Service} stereotype; the table is {@code services}.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "services", indexes = {
        @Index(name = "idx_service_vendor", columnList = "vendorId"),
        @Index(name = "idx_service_category", columnList = "category")
})
public class ServiceOffering extends BaseEntity {

    @Column(nullable = false)
    private UUID vendorId;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ServiceCategory category = ServiceCategory.OTHER;

    @Column(length = 2000)
    private String description;

    /** Base price; final price = base + selected add-ons (PRD §10 "starting at"). */
    @Column(nullable = false)
    private BigDecimal basePrice;

    @Column(nullable = false)
    private String currency = "CAD";

    /** How long one booking of this service blocks the calendar (PRD §9). */
    @Column(nullable = false)
    private int durationMinutes = 60;

    private String imageUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DepositType depositType = DepositType.NONE;

    /** Fixed amount when {@link DepositType#FIXED}; percent (0–100) when {@link DepositType#PERCENTAGE}. */
    @Column(precision = 19, scale = 2)
    private BigDecimal depositValue;

    /** Optional tax rate applied to the service price (e.g. 0.13 = 13%). */
    @Column(nullable = false, precision = 6, scale = 4)
    private BigDecimal taxRate = BigDecimal.ZERO;

    @Column(nullable = false)
    private boolean active = true;

    /** The deposit due for this service at its base price, before add-ons (rounded to 2dp). */
    public BigDecimal depositFor(BigDecimal price) {
        if (price == null) {
            return BigDecimal.ZERO;
        }
        return switch (depositType) {
            case NONE -> BigDecimal.ZERO;
            case FIXED -> depositValue == null ? BigDecimal.ZERO : depositValue.min(price);
            case PERCENTAGE -> depositValue == null ? BigDecimal.ZERO
                    : price.multiply(depositValue).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            case FULL -> price;
        };
    }
}
