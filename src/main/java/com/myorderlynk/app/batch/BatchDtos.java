package com.myorderlynk.app.batch;

import com.myorderlynk.app.common.enums.BatchStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Request/response records for batch cycles, batch products, dashboards and discovery. */
public final class BatchDtos {

    private BatchDtos() {
    }

    // ---- Batch cycle ----

    public record BatchRequest(
            @NotBlank String batchName,
            BatchType batchType,
            String route,
            String originCountry,
            String originCity,
            String destinationCountry,
            String destinationCity,
            ShippingMethod shippingMethod,
            LocalDate openDate,
            LocalDate closeDate,
            LocalDate estimatedDeparture,
            LocalDate estimatedArrival,
            @PositiveOrZero BigDecimal ratePerKg,
            @PositiveOrZero BigDecimal handlingFee,
            String currency,
            String pickupLocation,
            List<String> collectionPoints,
            BatchVisibility visibility,
            String notes) {
    }

    public record BatchResponse(
            UUID id,
            UUID vendorId,
            String vendorName,
            String batchName,
            BatchType batchType,
            String route,
            String originCountry,
            String originCity,
            String destinationCountry,
            String destinationCity,
            ShippingMethod shippingMethod,
            LocalDate openDate,
            LocalDate closeDate,
            LocalDate estimatedDeparture,
            LocalDate estimatedArrival,
            BigDecimal ratePerKg,
            BigDecimal handlingFee,
            String currency,
            String pickupLocation,
            List<String> collectionPoints,
            BatchStatus batchStatus,
            BatchVisibility visibility,
            String notes,
            boolean openForOrders) {
    }

    /** Vendor batch-cycle row with rollup counts/revenue for the Batch Cycles list (spec §9). */
    public record BatchSummary(
            BatchResponse batch,
            int orderCount,
            int paidOrderCount,
            int shipmentRequestCount,
            BigDecimal revenue,
            BigDecimal pendingPayments) {
    }

    public record StatusUpdateRequest(@NotNull BatchStatus status, String note) {
    }

    // ---- Batch products ----

    /** Attach catalog products to a batch (batch price defaults to each product's price). */
    public record AttachProductsRequest(@NotNull List<UUID> productIds) {
    }

    public record CopyFromBatchRequest(@NotNull UUID sourceBatchId) {
    }

    public record BatchProductRequest(
            @PositiveOrZero BigDecimal batchPrice,
            @PositiveOrZero Integer quantityLimit,
            @Positive Integer minOrderQuantity,
            BatchProductStatus status,
            String batchNotes) {
    }

    public record BatchProductResponse(
            UUID id,
            UUID batchId,
            UUID productId,
            String name,
            String imageUrl,
            String description,
            BigDecimal batchPrice,
            String currency,
            int quantityLimit,
            int soldQuantity,
            Integer remaining,
            int minOrderQuantity,
            BatchProductStatus status,
            String batchNotes) {
    }

    // ---- Public discovery ----

    /** Marketplace card for an open batch (spec §10). */
    public record BatchCard(
            UUID id,
            UUID vendorId,
            String vendorName,
            String storeSlug,
            String batchName,
            BatchType batchType,
            String route,
            String originCountry,
            String destinationCity,
            ShippingMethod shippingMethod,
            LocalDate closeDate,
            LocalDate estimatedArrival,
            BigDecimal ratePerKg,
            String currency,
            int productCount,
            boolean acceptsShipmentRequests,
            boolean openForOrders) {
    }

    /** Public batch page: cycle details + available products + provider info (spec §9 Customer Batch Page). */
    public record PublicBatchResponse(
            BatchResponse batch,
            String storeSlug,
            String vendorWhatsapp,
            List<BatchProductResponse> products) {
    }

    // ---- Shared payment kickoff ----

    public record PaymentInitResponse(
            String publicId,
            String clientSecret,
            String paymentReference,
            BigDecimal amount,
            String currency) {
    }

    public record PayRequest(String contact) {
    }

    /** Vendor-recorded manual (card) payment; amount defaults to the outstanding balance. */
    public record ManualPaymentRequest(@PositiveOrZero BigDecimal amount, String reference) {
    }
}
