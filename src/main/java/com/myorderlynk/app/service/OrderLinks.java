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

    /** Signed token embedding the order id + contact (email when present, else phone). */
    public String trackToken(Order order) {
        String contact = order.getCustomerEmail() != null && !order.getCustomerEmail().isBlank()
                ? order.getCustomerEmail()
                : order.getCustomerPhone();
        return jwtService.issueOrderTrackToken(order.getPublicOrderId(), contact);
    }

    /** Tokenized tracking URL for an order. */
    public String trackUrl(Order order) {
        return baseUrl + "/orders?token=" + UriUtils.encode(trackToken(order), StandardCharsets.UTF_8);
    }
}