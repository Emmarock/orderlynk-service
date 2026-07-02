package com.myorderlynk.app.order;

import com.myorderlynk.app.common.enums.FulfillmentStatus;
import com.myorderlynk.app.common.enums.FulfillmentType;
import com.myorderlynk.app.common.enums.PaymentMethod;
import com.myorderlynk.app.common.enums.PaymentStatus;
import com.myorderlynk.app.common.enums.SourceChannel;
import com.myorderlynk.app.common.enums.VatCollector;
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
            @Positive int quantity,
            /** Chosen colour; required when the product defines colour options, else ignored. */
            String selectedColor,
            /** Chosen size; required when the product defines size options, else ignored. */
            String selectedSize) {
    }

    /** Public checkout payload (PRD §8 customer journey, §14 Customer Order). */
    public record CheckoutRequest(
            @NotNull UUID vendorId,
            @NotEmpty @Valid List<CartLine> items,
            @NotBlank String customerName,
            @NotBlank String customerPhone,
            @Email String customerEmail,
            String customerHouseNumber,
            String customerStreet,
            String customerCity,
            String customerState,
            String customerPostcode,
            String customerCountry,
            @NotNull FulfillmentType fulfillmentType,
            @NotNull PaymentMethod paymentMethod,
            SourceChannel sourceChannel,
            String campaign,
            String notes,
            /** Selected shipping rate's service-level token (e.g. "usps_priority") from a prior /api/shipping/rates call; null = cheapest. */
            String shippingServiceToken) {

        /**
         * Copy of this request pinned to a specific vendor. Used by vendor-side order entry to force
         * the order onto the authenticated vendor, ignoring whatever the client put in the body.
         */
        public CheckoutRequest withVendorId(UUID vendorId) {
            return new CheckoutRequest(vendorId, items, customerName, customerPhone, customerEmail,
                    customerHouseNumber, customerStreet, customerCity, customerState, customerPostcode,
                    customerCountry, fulfillmentType, paymentMethod, sourceChannel, campaign, notes,
                    shippingServiceToken);
        }
    }

    /**
     * Pre-checkout fee quote so the cart can show full cost before submit. For shipping
     * fulfillment, supply the destination (and optionally a chosen {@code shippingServiceToken}
     * from {@code /api/shipping/rates}) so the logistics fee reflects a live carrier rate.
     */
    public record QuoteRequest(
            @NotNull UUID vendorId,
            @NotEmpty @Valid List<CartLine> items,
            @NotNull FulfillmentType fulfillmentType,
            @NotNull PaymentMethod paymentMethod,
            String customerHouseNumber,
            String customerStreet,
            String customerCity,
            String customerState,
            String customerPostcode,
            String customerCountry,
            String customerName,
            String customerPhone,
            String customerEmail,
            String shippingServiceToken) {
    }

    public record QuoteResponse(
            BigDecimal productSubtotal,
            BigDecimal vatAmount,
            BigDecimal logisticsFee,
            BigDecimal platformFee,
            BigDecimal processingFee,
            BigDecimal totalAmount,
            String currency,
            /** Whether logisticsFee came from a live carrier rate (vs the flat per-fulfillment fee). */
            boolean liveShippingRate,
            String shippingCarrier,
            String shippingService,
            String shippingServiceToken,
            Integer shippingEstimatedDays) {
    }

    /** Customer self-service tracking (PRD §14 Track Order): order id + matching contact. */
    public record TrackOrderRequest(
            @NotBlank String orderId,
            @NotBlank String contact) {
    }

    /** Tracking via a signed token from an order link (keeps order id + contact out of the URL). */
    public record TrackTokenRequest(
            @NotBlank String token) {
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
            String selectedColor,
            String selectedSize,
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
            String email,
            String currency,
            String sortCode,
            String routingNumber,
            String institutionNumber,
            String transitNumber,
            String iban,
            String bic,
            String bankCode) {
    }

    public record OrderResponse(
            UUID id,
            String publicOrderId,
            String customerName,
            String customerPhone,
            String customerEmail,
            String customerHouseNumber,
            String customerStreet,
            String customerCity,
            String customerState,
            String customerPostcode,
            String customerCountry,
            UUID vendorId,
            String vendorName,
            List<OrderItemResponse> items,
            BigDecimal productSubtotal,
            BigDecimal vatAmount,
            VatCollector vatCollector,
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
            PaymentInstructions paymentInstructions,
            String trackToken,
            // Set only on checkout when card payment is initiated: the Stripe client
            // secret the frontend confirms with, plus the payment-service reference.
            String clientSecret,
            String paymentReference) {
    }
}
