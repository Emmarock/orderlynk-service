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

    @Column(nullable = false)
    private String currency = "CAD";

    @Column(nullable = false)
    private int quantityAvailable = 0;

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
}
