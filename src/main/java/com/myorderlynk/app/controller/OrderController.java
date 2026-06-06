package com.myorderlynk.app.controller;

import com.myorderlynk.app.dto.OrderDtos.CheckoutRequest;
import com.myorderlynk.app.dto.OrderDtos.OrderResponse;
import com.myorderlynk.app.dto.OrderDtos.QuoteRequest;
import com.myorderlynk.app.dto.OrderDtos.QuoteResponse;
import com.myorderlynk.app.dto.OrderDtos.TrackOrderRequest;
import com.myorderlynk.app.dto.OrderDtos.TrackTokenRequest;
import com.myorderlynk.app.security.AuthPrincipal;
import com.myorderlynk.app.security.CurrentUser;
import com.myorderlynk.app.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Customer-facing order endpoints. Checkout, quote, and tracking are public
 * (guest checkout per PRD §8); the order history endpoint requires a customer login.
 */
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;
    private final CurrentUser currentUser;

    public OrderController(OrderService orderService, CurrentUser currentUser) {
        this.orderService = orderService;
        this.currentUser = currentUser;
    }

    @PostMapping("/quote")
    public QuoteResponse quote(@Valid @RequestBody QuoteRequest req) {
        return orderService.quote(req);
    }

    @PostMapping
    public OrderResponse checkout(@Valid @RequestBody CheckoutRequest req) {
        AuthPrincipal principal = currentUser.resolve();
        UUID customerUserId = principal == null ? null : principal.userId();
        return orderService.checkout(req, customerUserId);
    }

    @PostMapping("/track")
    public OrderResponse track(@Valid @RequestBody TrackOrderRequest req) {
        return orderService.track(req.orderId(), req.contact());
    }

    /** Resolve a signed tracking token (from an order link) to the order. */
    @PostMapping("/track-token")
    public OrderResponse trackByToken(@Valid @RequestBody TrackTokenRequest req) {
        return orderService.trackByToken(req.token());
    }

    @GetMapping("/mine")
    @PreAuthorize("isAuthenticated()")
    public List<OrderResponse> myOrders() {
        return orderService.customerOrders(currentUser.require().userId());
    }
}
