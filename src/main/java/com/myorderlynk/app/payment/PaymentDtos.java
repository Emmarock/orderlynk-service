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
}