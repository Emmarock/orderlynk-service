package com.myorderlynk.app.batch;

import com.myorderlynk.app.common.enums.FulfillmentType;
import com.myorderlynk.app.common.enums.PaymentStatus;
import com.myorderlynk.app.common.enums.SourceChannel;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Request/response records for batch-product orders (spec §7.1, §8.2). */
public final class BatchOrderDtos {

    private BatchOrderDtos() {
    }

    public record CartLine(@NotNull UUID batchProductId, @Positive int quantity) {
    }

    public record BatchOrderRequest(
            @NotNull UUID batchId,
            @NotBlank String customerName,
            @NotBlank String customerPhone,
            @Email String customerEmail,
            @NotEmpty List<CartLine> items,
            FulfillmentType fulfillmentType,
            String customerHouseNumber,
            String customerStreet,
            String customerCity,
            String customerState,
            String customerPostcode,
            String customerCountry,
            SourceChannel sourceChannel,
            @Size(max = 2000) String notes) {
    }

    public record BatchOrderItemResponse(
            UUID batchProductId,
            String productName,
            int quantity,
            BigDecimal unitPrice,
            BigDecimal lineTotal) {
    }

    public record BatchOrderResponse(
            UUID id,
            String publicOrderId,
            UUID batchId,
            String batchName,
            UUID vendorId,
            String vendorName,
            UUID customerUserId,
            String customerName,
            String customerPhone,
            String customerEmail,
            List<BatchOrderItemResponse> items,
            FulfillmentType fulfillmentType,
            String customerHouseNumber,
            String customerStreet,
            String customerCity,
            String customerState,
            String customerPostcode,
            String customerCountry,
            BigDecimal productSubtotal,
            BigDecimal deliveryFee,
            BigDecimal totalAmount,
            BigDecimal amountPaid,
            BigDecimal balanceDue,
            BigDecimal refundedAmount,
            String currency,
            PaymentStatus paymentStatus,
            BatchOrderStatus status,
            SourceChannel sourceChannel,
            String pickupCode,
            String notes,
            Instant createdAt,
            String clientSecret,
            String paymentReference) {
    }

    public record OrderStatusUpdateRequest(@NotNull BatchOrderStatus status, String note) {
    }
}
