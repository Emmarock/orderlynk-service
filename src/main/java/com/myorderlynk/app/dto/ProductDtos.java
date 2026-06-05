package com.myorderlynk.app.dto;

import com.myorderlynk.app.domain.enums.FulfillmentType;
import com.myorderlynk.app.domain.enums.ProductCategory;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.util.UUID;

public final class ProductDtos {

    private ProductDtos() {
    }

    public record ProductRequest(
            @NotBlank String name,
            String description,
            ProductCategory category,
            @NotNull @DecimalMin("0.0") BigDecimal price,
            String currency,
            @PositiveOrZero int quantityAvailable,
            String productImageUrl,
            @NotNull FulfillmentType fulfillmentType,
            String originCountry,
            Boolean availableNow,
            UUID batchId,
            Boolean active) {
    }

    public record ProductResponse(
            UUID id,
            UUID vendorId,
            String name,
            String description,
            ProductCategory category,
            BigDecimal price,
            String currency,
            int quantityAvailable,
            String productImageUrl,
            FulfillmentType fulfillmentType,
            String originCountry,
            boolean availableNow,
            UUID batchId,
            boolean active) {
    }
}
