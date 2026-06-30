package com.myorderlynk.app.booking;

import com.myorderlynk.app.common.enums.PaymentMethod;
import com.myorderlynk.app.common.enums.PaymentStatus;
import com.myorderlynk.app.common.enums.SourceChannel;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Request/response records for customer bookings and provider booking operations. */
public final class BookingDtos {

    private BookingDtos() {
    }

    /** A selected add-on on a booking request. */
    public record SelectedAddOn(
            @NotNull UUID addOnId,
            @Positive Integer quantity) {
    }

    /** Customer booking request from a vendor link or the marketplace (PRD §6). */
    public record BookingRequest(
            @NotNull UUID vendorId,
            @NotNull UUID serviceId,
            // Chosen sub-service / variant — required when the service defines active variants.
            UUID serviceVariantId,
            @NotBlank String customerName,
            @NotBlank String customerPhone,
            @Email String customerEmail,
            @NotNull Instant appointmentStart,
            List<SelectedAddOn> addOns,
            // Customer's chosen delivery location. Only honoured for HYBRID providers (pick
            // AT_PROVIDER or CUSTOMER_LOCATION); ignored for fixed-location providers.
            ServiceLocationType locationType,
            // Customer address — required only for mobile (CUSTOMER_LOCATION) services.
            String customerHouseNumber,
            String customerStreet,
            String customerCity,
            String customerState,
            String customerPostcode,
            String customerCountry,
            SourceChannel sourceChannel,
            @Size(max = 2000) String notes) {
    }

    public record BookingAddOnResponse(
            UUID addOnId,
            String name,
            BigDecimal priceDelta,
            int durationDelta,
            int quantity) {
    }

    public record BookingResponse(
            UUID id,
            String publicBookingId,
            UUID customerUserId,
            String customerName,
            String customerPhone,
            String customerEmail,
            UUID vendorId,
            String vendorName,
            UUID serviceId,
            String serviceName,
            UUID serviceVariantId,
            String variantName,
            List<BookingAddOnResponse> addOns,
            Instant appointmentStart,
            Instant appointmentEnd,
            BookingStatus status,
            ApprovalMode approvalMode,
            ServiceLocationType locationType,
            String customerHouseNumber,
            String customerStreet,
            String customerCity,
            String customerState,
            String customerPostcode,
            String customerCountry,
            BigDecimal servicePrice,
            BigDecimal travelFee,
            BigDecimal taxAmount,
            BigDecimal totalAmount,
            DepositType depositType,
            BigDecimal depositAmount,
            BigDecimal amountPaid,
            BigDecimal balanceDue,
            BigDecimal refundedAmount,
            String currency,
            PaymentStatus paymentStatus,
            Instant holdExpiresAt,
            SourceChannel sourceChannel,
            String notes,
            String statusReason,
            Instant createdAt,
            ReviewResponse review,
            // Present only when a card payment was just initiated (deposit on create): the
            // Stripe client secret the customer confirms, and the payment-service reference.
            String clientSecret,
            String paymentReference) {
    }

    /** Card-payment kickoff for a booking deposit/balance — returned by the "pay" endpoint. */
    public record PaymentInitResponse(
            String publicBookingId,
            String clientSecret,
            String paymentReference,
            BigDecimal amount,
            String currency) {
    }

    /** Body for the customer "pay" endpoint; contact authenticates a guest booking. */
    public record PayRequest(String contact) {
    }

    // ---- Provider operations ----

    public record RejectRequest(@Size(max = 500) String reason) {
    }

    public record CancelRequest(@Size(max = 500) String reason) {
    }

    public record RescheduleRequest(@NotNull Instant appointmentStart) {
    }

    /** Records a manual/cash/online payment against a booking (PRD §10 manual fallback). */
    public record PaymentRequest(
            @NotNull BookingPaymentType paymentType,
            @PositiveOrZero BigDecimal amount,
            PaymentMethod method,
            String transactionReference) {
    }

    public record PaymentResponse(
            UUID id,
            UUID bookingId,
            BookingPaymentType paymentType,
            BigDecimal amount,
            PaymentStatus status,
            PaymentMethod method,
            String transactionReference,
            Instant paidAt) {
    }

    // ---- Reviews ----

    public record ReviewRequest(
            @Min(1) @Max(5) int rating,
            @Size(max = 1000) String comment) {
    }

    public record ReviewResponse(
            UUID id,
            UUID bookingId,
            UUID vendorId,
            UUID serviceId,
            int rating,
            String comment,
            Instant createdAt) {
    }

    // ---- Public discovery ----

    /** A service-provider card on the Services marketplace (PRD §12.1). */
    public record ProviderCard(
            UUID vendorId,
            String businessName,
            String storeSlug,
            String logoUrl,
            String bannerUrl,
            String city,
            String serviceArea,
            ServiceLocationType locationType,
            BigDecimal rating,
            int ratingCount,
            BigDecimal startingPrice,
            String currency,
            List<ServiceCategory> categories,
            boolean acceptsDeposits,
            boolean featured) {
    }

    /** Public service storefront: provider profile + active services + recent reviews (PRD §12.2). */
    public record ServiceStorefrontResponse(
            UUID vendorId,
            String businessName,
            String storeSlug,
            String description,
            String logoUrl,
            String bannerUrl,
            String city,
            String whatsappNumber,
            String instagramHandle,
            ServiceDtos.ProfileResponse profile,
            BigDecimal rating,
            int ratingCount,
            List<ServiceDtos.ServiceResponse> services,
            List<ReviewResponse> reviews) {
    }
}
