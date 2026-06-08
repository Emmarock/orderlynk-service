package com.myorderlynk.app.shipping;

import com.myorderlynk.app.exception.ApiException;
import com.myorderlynk.app.security.CurrentUser;
import com.myorderlynk.app.shipping.ShippingDtos.BuyLabelRequest;
import com.myorderlynk.app.shipping.ShippingDtos.RateQuoteRequest;
import com.myorderlynk.app.shipping.ShippingDtos.RateQuoteResponse;
import com.myorderlynk.app.shipping.ShippingDtos.ShipmentResponse;
import com.myorderlynk.app.shipping.ShippingDtos.TrackingResponse;
import jakarta.validation.Valid;
import com.myorderlynk.app.security.access.IsVendor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Shipping endpoints. The cart rate quote is public (guests check out); buying labels and
 * refreshing tracking are vendor-scoped under {@code /api/shipping/vendor/**}.
 */
@RestController
@RequestMapping("/api/shipping")
public class ShippingController {

    private final ShippingService shippingService;
    private final CurrentUser currentUser;

    public ShippingController(ShippingService shippingService, CurrentUser currentUser) {
        this.shippingService = shippingService;
        this.currentUser = currentUser;
    }

    /** Public: live carrier rate options for a cart, so the customer can pick a service at checkout. */
    @PostMapping("/rates")
    public RateQuoteResponse rates(@Valid @RequestBody RateQuoteRequest req) {
        return shippingService.rateOptions(req);
    }

    /** Vendor: (re-)fetch live rates for an existing order before buying a label. */
    @GetMapping("/vendor/orders/{orderId}/rates")
    @IsVendor
    public RateQuoteResponse orderRates(@PathVariable UUID orderId) {
        return shippingService.ratesForOrder(vendorId(), orderId);
    }

    /** Vendor: buy a shipping label for an order (uses the order's selected/cheapest rate, or a supplied one). */
    @PostMapping("/vendor/orders/{orderId}/label")
    @IsVendor
    public ShipmentResponse buyLabel(@PathVariable UUID orderId, @RequestBody(required = false) BuyLabelRequest req) {
        String rateId = req == null ? null : req.rateId();
        return shippingService.buyLabel(vendorId(), orderId, rateId);
    }

    /** Vendor: current shipment (rate/label/tracking) for an order. */
    @GetMapping("/vendor/orders/{orderId}")
    @IsVendor
    public ShipmentResponse shipment(@PathVariable UUID orderId) {
        return shippingService.getShipment(vendorId(), orderId)
                .orElseThrow(() -> ApiException.notFound("No shipment for this order"));
    }

    /** Vendor: pull the latest tracking from the carrier and sync the order. */
    @PostMapping("/vendor/orders/{orderId}/tracking/refresh")
    @IsVendor
    public TrackingResponse refreshTracking(@PathVariable UUID orderId) {
        return shippingService.refreshTracking(vendorId(), orderId);
    }

    private UUID vendorId() {
        UUID vendorId = currentUser.require().vendorId();
        if (vendorId == null) {
            throw ApiException.forbidden("No vendor is linked to your account");
        }
        return vendorId;
    }
}