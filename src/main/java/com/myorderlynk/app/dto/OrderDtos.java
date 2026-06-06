package com.myorderlynk.app.dto;

import com.myorderlynk.app.domain.enums.FulfillmentStatus;
import com.myorderlynk.app.domain.enums.FulfillmentType;
import com.myorderlynk.app.domain.enums.PaymentMethod;
import com.myorderlynk.app.domain.enums.PaymentStatus;
import com.myorderlynk.app.domain.enums.SourceChannel;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class OrderDtos {

    private OrderDtos() {
    }

    public record CartLine(
            @NotNull UUID productId,
            @Positive int quantity) {
    }

    /** Public checkout payload (PRD §8 customer journey, §14 Customer Order). */
    public record CheckoutRequest(
            @NotNull UUID vendorId,
            @NotEmpty @Valid List<CartLine> items,
            @NotBlank String customerName,
            @NotBlank String customerPhone,
            @Email String customerEmail,
            String customerCity,
            @NotNull FulfillmentType fulfillmentType,
            @NotNull PaymentMethod paymentMethod,
            SourceChannel sourceChannel,
            String campaign,
            String notes) {
    }

    /** Pre-checkout fee quote so the cart can show full cost before submit. */
    public record QuoteRequest(
            @NotNull UUID vendorId,
            @NotEmpty @Valid List<CartLine> items,
            @NotNull FulfillmentType fulfillmentType,
            @NotNull PaymentMethod paymentMethod) {
    }

    public record QuoteResponse(
            BigDecimal productSubtotal,
            BigDecimal logisticsFee,
            BigDecimal platformFee,
            BigDecimal processingFee,
            BigDecimal totalAmount,
            String currency) {
    }

    /** Customer self-service tracking (PRD §14 Track Order): order id + matching contact. */
    public record TrackOrderRequest(
            @NotBlank String orderId,
            @NotBlank String contact) {
    }

    public record FulfillmentUpdateRequest(
            @NotNull FulfillmentStatus status,
            String note) {
    }

    public record PaymentUpdateRequest(
            @NotNull PaymentStatus status,
            PaymentMethod method,
            String transactionReference,
            BigDecimal amount) {
    }

    public record OrderItemResponse(
            UUID productId,
            String productName,
            int quantity,
            BigDecimal unitPrice,
            BigDecimal lineTotal) {
    }

    /**
     * How the customer should pay the vendor — the vendor's configured payout details.
     * Only attached to a customer's own order (checkout result / tracking), never to public
     * listings. Null when the vendor hasn't configured payment details.
     */
    public record PaymentInstructions(
            String method,
            String accountName,
            String bankName,
            String accountNumber,
            String email) {
    }

    public record OrderResponse(
            UUID id,
            String publicOrderId,
            String customerName,
            String customerPhone,
            String customerEmail,
            String customerCity,
            UUID vendorId,
            String vendorName,
            List<OrderItemResponse> items,
            BigDecimal productSubtotal,
            BigDecimal logisticsFee,
            BigDecimal platformFee,
            BigDecimal processingFee,
            BigDecimal totalAmount,
            BigDecimal vendorPayable,
            BigDecimal logisticsPayable,
            BigDecimal platformRevenue,
            BigDecimal refundedAmount,
            String currency,
            PaymentStatus paymentStatus,
            FulfillmentType fulfillmentType,
            FulfillmentStatus fulfillmentStatus,
            List<FulfillmentStatus> fulfillmentFlow,
            String pickupCode,
            SourceChannel sourceChannel,
            String campaign,
            String notes,
            Instant createdAt,
            PaymentInstructions paymentInstructions) {
    }
}
