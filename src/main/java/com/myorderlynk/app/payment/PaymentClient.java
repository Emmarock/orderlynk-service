package com.myorderlynk.app.payment;

import com.myorderlynk.app.order.Order;
import com.myorderlynk.app.payment.PaymentDtos.ConnectAccountRequest;
import com.myorderlynk.app.payment.PaymentDtos.ConnectAccountStatus;
import com.myorderlynk.app.payment.PaymentDtos.CreatePaymentRequest;
import com.myorderlynk.app.payment.PaymentDtos.CreatePaymentResponse;
import com.myorderlynk.app.payment.PaymentDtos.OnboardingResult;
import com.myorderlynk.app.payment.PaymentDtos.PlatformChargeRequest;
import com.myorderlynk.app.payment.PaymentDtos.PlatformChargeResponse;
import com.myorderlynk.app.payment.PaymentDtos.BillingSetupRequest;
import com.myorderlynk.app.payment.PaymentDtos.ConfirmCardRequest;
import com.myorderlynk.app.payment.PaymentDtos.CardSetupResult;
import com.myorderlynk.app.payment.PaymentDtos.BillingStatus;
import com.myorderlynk.app.payment.PaymentDtos.InstantPayoutRequest;
import com.myorderlynk.app.payment.PaymentDtos.InstantPayoutResult;
import com.myorderlynk.app.security.JwtService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.HttpClientErrorException;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Synchronous client for the standalone payment-service. Authenticates with a
 * short-lived service JWT ({@code roles:["SERVICE"]}) and sends the order's public
 * id as the idempotency key, so a retried checkout never creates a second payment.
 */
@Slf4j
@Component
public class PaymentClient {

    private final RestClient restClient;
    private final JwtService jwtService;

    public PaymentClient(PaymentServiceProperties properties, JwtService jwtService) {
        this.restClient = RestClient.builder().baseUrl(properties.getBaseUrl()).build();
        this.jwtService = jwtService;
    }

    /**
     * Create a payment / PaymentIntent for an order. Allocations are derived from the
     * order's fee breakdown and must sum to the total (the payment-service validates this).
     * Returns the response (including the client secret the customer needs to pay).
     */
    public CreatePaymentResponse createPayment(Order order) {
        Map<String, BigDecimal> allocations = orderAllocations(order);

        // Stripe only allows an application fee (our PLATFORM_FEE/PROCESSING_FEE allocation)
        // on a destination charge, so the connected account must be wired here. A vendor
        // that hasn't finished onboarding has no account that can receive funds, so we send
        // null and let the payment-service decide how to handle the non-destination case.
        ConnectAccountStatus connect = connectStatus(order.getVendorId());
        String vendorAccountId = connect.canReceiveFunds() ? connect.accountId() : null;
        if (vendorAccountId == null) {
            log.warn("vendor {} has no Stripe account that can receive funds (chargesEnabled={}); "
                            + "creating payment without a destination for order {}",
                    order.getVendorId(), connect.chargesEnabled(), order.getPublicOrderId());
        }

        CreatePaymentRequest body = new CreatePaymentRequest(
                order.getPublicOrderId(),
                customerId(order),
                order.getVendorId().toString(),
                vendorAccountId,
                order.getCurrency(),
                order.getTotalAmount(),
                allocations,
                order.getPublicOrderId());

        CreatePaymentResponse response = restClient.post()
                .uri("/payments")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtService.issueServiceToken())
                .header("Idempotency-Key", order.getPublicOrderId())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(CreatePaymentResponse.class);

        log.info("payment-service created payment {} for order {} (status {})",
                response == null ? null : response.reference(), order.getPublicOrderId(),
                response == null ? null : response.status());
        return response;
    }

    /**
     * Create a payment / PaymentIntent for a service booking (deposit or balance). Keyed by the
     * public booking id (disjoint from order ids), so the same payment-service contract, webhook
     * and idempotency machinery handle bookings without any change. The platform commission is
     * taken as a destination application fee (gross − PRODUCT); the rest settles to the vendor.
     *
     * @param idempotencySuffix distinguishes a booking's deposit from its balance charge
     */
    public CreatePaymentResponse createBookingPayment(String publicBookingId, String customerId, UUID vendorId,
                                                      String currency, BigDecimal amount, BigDecimal commissionRate,
                                                      String idempotencySuffix) {
        BigDecimal platformFee = commissionRate == null ? BigDecimal.ZERO
                : amount.multiply(commissionRate).setScale(2, java.math.RoundingMode.HALF_UP);
        if (platformFee.compareTo(amount) > 0) {
            platformFee = amount;
        }
        BigDecimal vendorPortion = amount.subtract(platformFee);
        Map<String, BigDecimal> allocations = new LinkedHashMap<>();
        putIfPositive(allocations, "PRODUCT", vendorPortion);
        putIfPositive(allocations, "PLATFORM_FEE", platformFee);
        if (allocations.isEmpty()) {
            putIfPositive(allocations, "PRODUCT", amount);
        }

        ConnectAccountStatus connect = connectStatus(vendorId);
        String vendorAccountId = connect.canReceiveFunds() ? connect.accountId() : null;
        if (vendorAccountId == null) {
            log.warn("vendor {} has no Stripe account that can receive funds (chargesEnabled={}); "
                            + "creating booking payment without a destination for {}",
                    vendorId, connect.chargesEnabled(), publicBookingId);
        }

        String idempotencyKey = publicBookingId + "-" + idempotencySuffix;
        CreatePaymentRequest body = new CreatePaymentRequest(
                publicBookingId, customerId, vendorId.toString(), vendorAccountId,
                currency, amount, allocations, idempotencyKey);

        CreatePaymentResponse response = restClient.post()
                .uri("/payments")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtService.issueServiceToken())
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(CreatePaymentResponse.class);

        log.info("payment-service created booking payment {} for {} ({})",
                response == null ? null : response.reference(), publicBookingId,
                response == null ? null : response.status());
        return response;
    }

    /**
     * Generic module payment (batch orders, cargo shipment requests, …) keyed by the module's public
     * reference id — disjoint from order/booking ids, so the same payment-service contract and webhook
     * route it back. Commission is taken as a destination application fee (gross − PRODUCT).
     */
    public CreatePaymentResponse createModulePayment(String publicRef, String customerId, UUID vendorId,
                                                     String currency, BigDecimal amount, BigDecimal commissionRate,
                                                     String idempotencySuffix) {
        return createModulePayment(publicRef, customerId, vendorId, currency, amount, commissionRate,
                BigDecimal.ZERO, idempotencySuffix);
    }

    /**
     * As {@link #createModulePayment(String, String, UUID, String, BigDecimal, BigDecimal, String)}, but
     * also routes {@code extraPlatformFee} (e.g. a cargo handling fee that is part of the gross) to the
     * platform instead of the vendor. The vendor (PRODUCT) receives the gross minus commission minus this
     * extra fee; both the commission and the extra fee land in PLATFORM_FEE (-> PLATFORM_REVENUE).
     */
    public CreatePaymentResponse createModulePayment(String publicRef, String customerId, UUID vendorId,
                                                     String currency, BigDecimal amount, BigDecimal commissionRate,
                                                     BigDecimal extraPlatformFee, String idempotencySuffix) {
        BigDecimal commission = commissionRate == null ? BigDecimal.ZERO
                : amount.multiply(commissionRate).setScale(2, java.math.RoundingMode.HALF_UP);
        // Both commission and the extra platform fee are platform revenue; cap at the gross so PRODUCT
        // never goes negative (e.g. on a small partial payment).
        BigDecimal platformFee = commission.add(nz(extraPlatformFee)).min(amount);
        Map<String, BigDecimal> allocations = new LinkedHashMap<>();
        putIfPositive(allocations, "PRODUCT", amount.subtract(platformFee));
        putIfPositive(allocations, "PLATFORM_FEE", platformFee);
        if (allocations.isEmpty()) {
            putIfPositive(allocations, "PRODUCT", amount);
        }

        ConnectAccountStatus connect = connectStatus(vendorId);
        String vendorAccountId = connect.canReceiveFunds() ? connect.accountId() : null;
        if (vendorAccountId == null) {
            log.warn("vendor {} has no Stripe account that can receive funds (chargesEnabled={}); "
                            + "creating module payment without a destination for {}",
                    vendorId, connect.chargesEnabled(), publicRef);
        }

        String idempotencyKey = publicRef + "-" + idempotencySuffix;
        CreatePaymentRequest body = new CreatePaymentRequest(
                publicRef, customerId, vendorId.toString(), vendorAccountId,
                currency, amount, allocations, idempotencyKey);

        CreatePaymentResponse response = restClient.post()
                .uri("/payments")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtService.issueServiceToken())
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(CreatePaymentResponse.class);

        log.info("payment-service created module payment {} for {} ({})",
                response == null ? null : response.reference(), publicRef,
                response == null ? null : response.status());
        return response;
    }

    /**
     * Collect a platform fee (subscription / featured placement) from a vendor by netting it out of
     * their balance in the payment-service. Returns the settlement reference on success, or {@code null}
     * if the charge could not be collected (insufficient vendor balance, or the service is unreachable) —
     * the caller then leaves the invoice DUE for retry. Idempotent on {@code reference}.
     *
     * @param revenueType "SUBSCRIPTION" or "ADVERTISING"
     */
    public String chargeVendor(UUID vendorId, BigDecimal amount, String currency,
                               String revenueType, String reference) {
        if (amount == null || amount.signum() <= 0) {
            return null;
        }
        try {
            PlatformChargeResponse res = restClient.post()
                    .uri("/platform-charges")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtService.issueServiceToken())
                    .header("Idempotency-Key", reference)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new PlatformChargeRequest(vendorId.toString(), amount, currency, revenueType, reference))
                    .retrieve()
                    .body(PlatformChargeResponse.class);
            if (res == null) {
                return null;
            }
            log.info("platform charge {} for vendor {} -> {} ({})", reference, vendorId, res.status(), res.reference());
            return res.reference();
        } catch (Exception e) {
            log.warn("platform charge {} for vendor {} not collected ({})", reference, vendorId, e.getMessage());
            return null;
        }
    }

    /**
     * Issue a (partial) refund against a settled payment, identified by the payment-service reference we
     * stored when the charge succeeded. Looks the payment up by reference, then refunds {@code amount}.
     * Best-effort: returns false (and logs) if the payment can't be found or the service is unreachable —
     * the caller then leaves it for a manual refund. The refund settles asynchronously and is recorded
     * back via the {@code PAYMENT_REFUNDED} webhook, so nothing is applied locally here.
     */
    public boolean refundByReference(String reference, BigDecimal amount, String reason) {
        if (reference == null || reference.isBlank() || amount == null || amount.signum() <= 0) {
            return false;
        }
        try {
            PaymentDtos.PaymentLookup payment = restClient.get()
                    .uri("/payments/reference/{ref}", reference)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtService.issueServiceToken())
                    .retrieve()
                    .body(PaymentDtos.PaymentLookup.class);
            if (payment == null || payment.id() == null) {
                log.warn("Refund skipped — no payment found for reference {}", reference);
                return false;
            }
            restClient.post()
                    .uri("/payments/{id}/refund", payment.id())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtService.issueServiceToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new PaymentDtos.RefundRequest(amount, reason))
                    .retrieve()
                    .toBodilessEntity();
            log.info("Requested refund of {} against payment {} (ref {})", amount, payment.id(), reference);
            return true;
        } catch (Exception e) {
            log.warn("Refund of {} against reference {} failed: {}", amount, reference, e.getMessage());
            return false;
        }
    }

    /**
     * Start card capture for a vendor: ensures a billing customer and returns a SetupIntent client
     * secret (for Stripe Elements) + the setup-intent id to confirm with once the card is entered.
     */
    public CardSetupResult startCardSetup(UUID vendorId, String email) {
        return restClient.post()
                .uri("/vendors/{vendorId}/billing/setup-intent", vendorId.toString())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtService.issueServiceToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(new BillingSetupRequest(email))
                .retrieve()
                .body(CardSetupResult.class);
    }

    /** Confirm the vendor's saved card after the SetupIntent succeeded client-side. */
    public BillingStatus confirmCard(UUID vendorId, String setupIntentId) {
        return restClient.post()
                .uri("/vendors/{vendorId}/billing/confirm", vendorId.toString())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtService.issueServiceToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ConfirmCardRequest(setupIntentId))
                .retrieve()
                .body(BillingStatus.class);
    }

    /**
     * Trigger an instant payout of the vendor's connected-account balance and charge the platform fee
     * to their card on file. Exceptions propagate so the caller can surface the failure to the vendor.
     */
    public InstantPayoutResult requestInstantPayout(UUID vendorId, BigDecimal amount, String currency,
                                                    BigDecimal feeAmount, String reference) {
        return restClient.post()
                .uri("/vendors/{vendorId}/instant-payouts", vendorId.toString())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtService.issueServiceToken())
                .header("Idempotency-Key", reference)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new InstantPayoutRequest(currency, amount, feeAmount, reference))
                .retrieve()
                .body(InstantPayoutResult.class);
    }

    /** Whether the vendor has a usable card on file for platform-fee collection. */
    public BillingStatus billingStatus(UUID vendorId) {
        return restClient.get()
                .uri("/vendors/{vendorId}/billing", vendorId.toString())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtService.issueServiceToken())
                .retrieve()
                .body(BillingStatus.class);
    }

    /**
     * Create (or return) the vendor's Stripe connected account and a hosted onboarding link.
     * Authenticated with the service token; payment-service treats SERVICE as privileged.
     */
    public OnboardingResult createConnectAccount(UUID vendorId, String email, String country) {
        return restClient.post()
                .uri("/vendors/{vendorId}/connect-account", vendorId.toString())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtService.issueServiceToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ConnectAccountRequest(email, country))
                .retrieve()
                .body(OnboardingResult.class);
    }

    /** Cached connected-account capability state, or {@link ConnectAccountStatus#notStarted()} if none exists. */
    public ConnectAccountStatus connectStatus(UUID vendorId) {
        try {
            return restClient.get()
                    .uri("/vendors/{vendorId}/connect-account", vendorId.toString())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtService.issueServiceToken())
                    .retrieve()
                    .body(ConnectAccountStatus.class);
        } catch (HttpClientErrorException.NotFound e) {
            return ConnectAccountStatus.notStarted();
        }
    }

    /** Force a live re-sync of capability state from Stripe, then return it. */
    public ConnectAccountStatus refreshConnectStatus(UUID vendorId) {
        try {
            return restClient.post()
                    .uri("/vendors/{vendorId}/connect-account/refresh", vendorId.toString())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtService.issueServiceToken())
                    .retrieve()
                    .body(ConnectAccountStatus.class);
        } catch (HttpClientErrorException.NotFound e) {
            return ConnectAccountStatus.notStarted();
        }
    }

    private static String customerId(Order order) {
        return order.getCustomerUserId() != null
                ? order.getCustomerUserId().toString()
                : "guest:" + order.getPublicOrderId();
    }

    /**
     * Build the order's payment allocations so the payment-service routes money to match the
     * FeeCalculator economics. The connected vendor receives only PRODUCT (= vendorPayable, subtotal
     * minus commission); everything else is retained by the platform as the destination application
     * fee. PLATFORM_FEE (-> PLATFORM_REVENUE) carries the customer service fee + vendor commission +
     * logistics markup; LOGISTICS holds only the carrier's actual cost. The buckets sum to the gross.
     */
    static Map<String, BigDecimal> orderAllocations(Order order) {
        BigDecimal subtotal = nz(order.getProductSubtotal());
        BigDecimal vendorPayable = nz(order.getVendorPayable());
        BigDecimal commission = subtotal.subtract(vendorPayable).max(BigDecimal.ZERO);
        BigDecimal logisticsPayable = nz(order.getLogisticsPayable());
        BigDecimal logisticsMarkup = nz(order.getLogisticsFee()).subtract(logisticsPayable).max(BigDecimal.ZERO);
        BigDecimal platformFee = nz(order.getPlatformFee()).add(commission).add(logisticsMarkup);

        Map<String, BigDecimal> allocations = new LinkedHashMap<>();
        putIfPositive(allocations, "PRODUCT", vendorPayable);
        putIfPositive(allocations, "LOGISTICS", logisticsPayable);
        putIfPositive(allocations, "PLATFORM_FEE", platformFee);
        putIfPositive(allocations, "PROCESSING_FEE", order.getProcessingFee());
        if (allocations.isEmpty()) {
            putIfPositive(allocations, "PRODUCT", order.getTotalAmount());
        }
        return allocations;
    }

    private static void putIfPositive(Map<String, BigDecimal> map, String key, BigDecimal value) {
        if (value != null && value.signum() > 0) {
            map.put(key, value);
        }
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}