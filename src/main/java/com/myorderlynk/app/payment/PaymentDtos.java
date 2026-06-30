package com.myorderlynk.app.payment;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Wire DTOs for the payment-service REST contract. Kept intentionally small —
 * only the fields the backend sends/needs. Unknown response fields are ignored.
 */
public final class PaymentDtos {

    private PaymentDtos() {}

    /** Mirrors payment-service {@code POST /payments} request body. */
    public record CreatePaymentRequest(
            String orderId,
            String customerId,
            String vendorId,
            String vendorAccountId,
            String currency,
            BigDecimal grossAmount,
            Map<String, BigDecimal> allocations,
            String clientRequestId) {
    }

    /** Subset of payment-service {@code PaymentResponse} the backend consumes. */
    public record CreatePaymentResponse(
            String id,
            String reference,
            String status,
            String providerReference,
            String clientSecret) {
    }

    /** Request body for payment-service {@code POST /vendors/{id}/connect-account}. */
    public record ConnectAccountRequest(String email, String country) {
    }

    /** Mirrors payment-service {@code POST /platform-charges} request body. */
    public record PlatformChargeRequest(
            String vendorId,
            BigDecimal amount,
            String currency,
            String revenueType,
            String reference) {
    }

    /** Subset of payment-service {@code PlatformChargeResponse} the backend consumes. */
    public record PlatformChargeResponse(
            String reference,
            String status,
            BigDecimal amount,
            String currency) {
    }

    /** Request body for payment-service {@code POST /vendors/{id}/billing/setup-intent}. */
    public record BillingSetupRequest(String email) {
    }

    /** Request body for payment-service {@code POST /vendors/{id}/billing/confirm}. */
    public record ConfirmCardRequest(String setupIntentId) {
    }

    /** Card-capture client secret (for Stripe Elements) + setup-intent id to confirm with. */
    public record CardSetupResult(String clientSecret, String setupIntentId) {
    }

    /** Whether the vendor has a usable card on file. */
    public record BillingStatus(boolean hasPaymentMethod) {
    }

    /** Request body for payment-service {@code POST /vendors/{id}/instant-payouts}. */
    public record InstantPayoutRequest(
            String currency,
            BigDecimal amount,
            BigDecimal feeAmount,
            String reference) {
    }

    /** Result of an instant payout (provider payout reference + status + amounts). */
    public record InstantPayoutResult(
            String payoutReference,
            String status,
            BigDecimal amount,
            BigDecimal fee,
            String currency) {
    }

    /** Vendor connected-account capability state (payment-service {@code ConnectAccountResponse}). */
    public record ConnectAccountStatus(
            String vendorId,
            String accountId,
            boolean chargesEnabled,
            boolean payoutsEnabled,
            boolean detailsSubmitted,
            boolean canReceiveFunds) {

        /** Sentinel for "no connected account yet". */
        public static ConnectAccountStatus notStarted() {
            return new ConnectAccountStatus(null, null, false, false, false, false);
        }
    }

    /** Onboarding link + account snapshot (payment-service {@code OnboardingLinkResponse}). */
    public record OnboardingResult(String url, String expiresAt, ConnectAccountStatus account) {
    }
}