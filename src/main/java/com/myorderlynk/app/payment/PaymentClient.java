package com.myorderlynk.app.payment;

import com.myorderlynk.app.order.Order;
import com.myorderlynk.app.payment.PaymentDtos.ConnectAccountRequest;
import com.myorderlynk.app.payment.PaymentDtos.ConnectAccountStatus;
import com.myorderlynk.app.payment.PaymentDtos.CreatePaymentRequest;
import com.myorderlynk.app.payment.PaymentDtos.CreatePaymentResponse;
import com.myorderlynk.app.payment.PaymentDtos.OnboardingResult;
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
        Map<String, BigDecimal> allocations = new LinkedHashMap<>();
        putIfPositive(allocations, "PRODUCT", order.getProductSubtotal());
        putIfPositive(allocations, "LOGISTICS", order.getLogisticsFee());
        putIfPositive(allocations, "PLATFORM_FEE", order.getPlatformFee());
        putIfPositive(allocations, "PROCESSING_FEE", order.getProcessingFee());

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
        BigDecimal platformFee = commissionRate == null ? BigDecimal.ZERO
                : amount.multiply(commissionRate).setScale(2, java.math.RoundingMode.HALF_UP);
        if (platformFee.compareTo(amount) > 0) {
            platformFee = amount;
        }
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

    private static void putIfPositive(Map<String, BigDecimal> map, String key, BigDecimal value) {
        if (value != null && value.signum() > 0) {
            map.put(key, value);
        }
    }
}