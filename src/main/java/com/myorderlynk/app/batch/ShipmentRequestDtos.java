package com.myorderlynk.app.batch;

import com.myorderlynk.app.common.enums.PaymentStatus;
import com.myorderlynk.app.common.enums.SourceChannel;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Request/response records for customer "Send My Items" shipment requests (spec §7.2, §8.3, §11.3). */
public final class ShipmentRequestDtos {

    private ShipmentRequestDtos() {
    }

    public record ShipmentRequestCreate(
            @NotNull UUID batchId,
            @NotBlank String customerName,
            @NotBlank String customerPhone,
            @Email String customerEmail,
            @NotBlank @Size(max = 2000) String itemDescription,
            @Positive int packageCount,
            @PositiveOrZero BigDecimal estimatedWeight,
            @PositiveOrZero BigDecimal declaredValue,
            String originDropOffLocation,
            String destinationLocation,
            String deliveryPreference,
            boolean restrictedItemsConfirmed,
            SourceChannel sourceChannel,
            @Size(max = 2000) String notes) {
    }

    /** Cargo staff records actual weight (and optional rate/fee overrides) → recalculates the charge. */
    public record WeighRequest(
            @NotNull @PositiveOrZero BigDecimal actualWeight,
            @PositiveOrZero BigDecimal ratePerKg,
            @PositiveOrZero BigDecimal handlingFee,
            @PositiveOrZero BigDecimal deliveryFee) {
    }

    public record ShipmentStatusUpdateRequest(@NotNull ShipmentRequestStatus status, String note) {
    }

    public record ShipmentRequestResponse(
            UUID id,
            String publicRequestId,
            UUID batchId,
            String batchName,
            UUID vendorId,
            String vendorName,
            UUID customerUserId,
            String customerName,
            String customerPhone,
            String customerEmail,
            String itemDescription,
            int packageCount,
            BigDecimal estimatedWeight,
            BigDecimal actualWeight,
            BigDecimal ratePerKg,
            BigDecimal handlingFee,
            BigDecimal deliveryFee,
            BigDecimal platformCargoFee,
            BigDecimal totalCharge,
            BigDecimal amountPaid,
            BigDecimal balanceDue,
            BigDecimal refundedAmount,
            String currency,
            BigDecimal declaredValue,
            boolean restrictedItemsConfirmed,
            String originDropOffLocation,
            String destinationLocation,
            String deliveryPreference,
            PaymentStatus paymentStatus,
            ShipmentRequestStatus status,
            SourceChannel sourceChannel,
            String pickupCode,
            String notes,
            Instant createdAt,
            String clientSecret,
            String paymentReference) {
    }
}
