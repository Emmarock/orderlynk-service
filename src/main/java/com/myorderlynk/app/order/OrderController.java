package com.myorderlynk.app.order;

import com.myorderlynk.app.order.OrderDtos.CheckoutRequest;
import com.myorderlynk.app.order.OrderDtos.OrderResponse;
import com.myorderlynk.app.order.OrderDtos.QuoteRequest;
import com.myorderlynk.app.order.OrderDtos.QuoteResponse;
import com.myorderlynk.app.order.OrderDtos.TrackOrderRequest;
import com.myorderlynk.app.order.OrderDtos.TrackTokenRequest;
import com.myorderlynk.app.security.AuthPrincipal;
import com.myorderlynk.app.security.CurrentUser;
import com.myorderlynk.app.order.OrderService;
import jakarta.validation.Valid;
import com.myorderlynk.app.security.access.IsAuthenticated;
import com.myorderlynk.app.common.PageResponse;
import com.myorderlynk.app.common.PageRequests;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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

    /**
     * Resolve a signed order link to a card-payment session (order + Stripe client secret) so a
     * customer can pay by card from a link the vendor shared over WhatsApp/Instagram/etc. Public:
     * the signed token is the bearer credential, same as tracking.
     */
    @PostMapping("/pay-token")
    public OrderResponse payByToken(@Valid @RequestBody TrackTokenRequest req) {
        return orderService.payByToken(req.token());
    }

    @GetMapping("/mine")
    @IsAuthenticated
    public PageResponse<OrderResponse> myOrders(@RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "20") int size) {
        return orderService.customerOrders(currentUser.require().userId(), PageRequests.of(page, size));
    }
}
