package com.myorderlynk.app.service;

import com.myorderlynk.app.domain.Order;
import com.myorderlynk.app.security.JwtService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;

/**
 * Builds customer-facing order links. The tracking link embeds the order id + contact in a
 * signed JWT ({@code /orders?token=…}) so no personal data appears in the URL.
 */
@Component
public class OrderLinks {

    private final JwtService jwtService;
    private final String baseUrl;

    public OrderLinks(JwtService jwtService,
                      @Value("${app.public-base-url:http://localhost:5173}") String baseUrl) {
        this.jwtService = jwtService;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    /** Tokenized tracking URL for an order; the contact is the email when present, else the phone. */
    public String trackUrl(Order order) {
        String contact = order.getCustomerEmail() != null && !order.getCustomerEmail().isBlank()
                ? order.getCustomerEmail()
                : order.getCustomerPhone();
        String token = jwtService.issueOrderTrackToken(order.getPublicOrderId(), contact);
        return baseUrl + "/orders?token=" + UriUtils.encode(token, StandardCharsets.UTF_8);
    }
}