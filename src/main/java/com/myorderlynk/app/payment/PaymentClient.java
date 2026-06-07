package com.myorderlynk.app.payment;

import com.myorderlynk.app.domain.Order;
import com.myorderlynk.app.payment.PaymentDtos.CreatePaymentRequest;
import com.myorderlynk.app.payment.PaymentDtos.CreatePaymentResponse;
import com.myorderlynk.app.security.JwtService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

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

    public PaymentClient(RestClient.Builder builder, PaymentServiceProperties properties, JwtService jwtService) {
        this.restClient = builder.baseUrl(properties.getBaseUrl()).build();
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

        CreatePaymentRequest body = new CreatePaymentRequest(
                order.getPublicOrderId(),
                customerId(order),
                order.getVendorId().toString(),
                null,                          // Stripe connected-account id — wired once vendor onboarding exists
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