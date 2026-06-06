package com.myorderlynk.app.domain;

import com.myorderlynk.app.domain.enums.FulfillmentType;
import com.myorderlynk.app.domain.enums.ProductCategory;
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
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "products", indexes = @Index(name = "idx_product_vendor", columnList = "vendorId"))
public class Product extends BaseEntity {

    @Column(nullable = false)
    private UUID vendorId;

    @Column(nullable = false)
    private String name;

    @Column(length = 2000)
    private String description;

    @Enumerated(EnumType.STRING)
    private ProductCategory category = ProductCategory.OTHER;

    @Column(nullable = false)
    private BigDecimal price;

    /** Vendor discount as a percentage (0–100) off {@link #price}; 0 = no discount. */
    @Column(nullable = false)
    private int discountPercent = 0;

    @Column(nullable = false)
    private String currency = "CAD";

    @Column(nullable = false)
    private int quantityAvailable = 0;

    /** Vendor-set threshold for low-stock alerts; 0 disables alerting for this product. */
    @Column(nullable = false)
    private int lowStockThreshold = 0;

    private String productImageUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FulfillmentType fulfillmentType = FulfillmentType.LOCAL_PICKUP;

    private String originCountry;

    @Column(nullable = false)
    private boolean availableNow = true;

    private UUID batchId;

    @Column(nullable = false)
    private boolean active = true;

    /** The price actually charged after applying {@link #discountPercent} (rounded to 2dp). */
    public BigDecimal effectivePrice() {
        if (discountPercent <= 0 || price == null) {
            return price;
        }
        BigDecimal multiplier = BigDecimal.valueOf(100 - discountPercent).divide(BigDecimal.valueOf(100));
        return price.multiply(multiplier).setScale(2, java.math.RoundingMode.HALF_UP);
    }
}
