package com.myorderlynk.app.catalog;

import com.myorderlynk.app.common.enums.FulfillmentType;
import com.myorderlynk.app.common.enums.ProductCategory;
import com.myorderlynk.app.shipping.DimensionUnit;
import com.myorderlynk.app.shipping.WeightUnit;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public final class ProductDtos {

    private ProductDtos() {
    }

    public record ProductRequest(
            @NotBlank String name,
            String description,
            ProductCategory category,
            @NotNull @DecimalMin("0.0") BigDecimal price,
            @Min(0) @Max(100) Integer discountPercent,
            String currency,
            @PositiveOrZero int quantityAvailable,
            @PositiveOrZero Integer lowStockThreshold,
            String productImageUrl,
            @Size(max = 6, message = "A product can have at most 6 images") List<String> imageUrls,
            String videoUrl,
            @Size(max = 30, message = "A product can have at most 30 colour options") List<String> colors,
            @Size(max = 30, message = "A product can have at most 30 size options") List<String> sizes,
            @NotNull FulfillmentType fulfillmentType,
            String originCountry,
            @PositiveOrZero BigDecimal weight,
            WeightUnit weightUnit,
            @PositiveOrZero BigDecimal length,
            @PositiveOrZero BigDecimal width,
            @PositiveOrZero BigDecimal height,
            DimensionUnit dimensionUnit,
            Boolean availableNow,
            UUID batchId,
            Boolean active) {
    }

    public record ImageUploadResponse(String url) {
    }

    public record DescriptionRequest(
            @NotBlank String name,
            ProductCategory category) {
    }

    public record DescriptionResponse(String description) {
    }

    public record ProductResponse(
            UUID id,
            UUID vendorId,
            String name,
            String description,
            ProductCategory category,
            BigDecimal price,
            int discountPercent,
            BigDecimal discountedPrice,
            String currency,
            int quantityAvailable,
            int lowStockThreshold,
            boolean lowStock,
            String productImageUrl,
            List<String> imageUrls,
            String videoUrl,
            List<String> colors,
            List<String> sizes,
            FulfillmentType fulfillmentType,
            String originCountry,
            BigDecimal weight,
            WeightUnit weightUnit,
            BigDecimal length,
            BigDecimal width,
            BigDecimal height,
            DimensionUnit dimensionUnit,
            boolean availableNow,
            UUID batchId,
            boolean active) {
    }
}
