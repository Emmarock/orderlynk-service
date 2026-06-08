package com.myorderlynk.app.payment;

import com.myorderlynk.app.domain.Order;
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