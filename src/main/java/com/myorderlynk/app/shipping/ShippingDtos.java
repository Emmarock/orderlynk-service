package com.myorderlynk.app.shipping;

import com.myorderlynk.app.dto.AddressDtos.AddressDto;
import com.myorderlynk.app.dto.OrderDtos.CartLine;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Request/response payloads for the shipping API. */
public final class ShippingDtos {

    private ShippingDtos() {
    }

    /** Public cart request: live rate options to ship the given items to a destination. */
    public record RateQuoteRequest(
            @NotNull UUID vendorId,
            @NotEmpty @Valid List<CartLine> items,
            @NotNull @Valid AddressDto destination,
            String customerName,
            String customerPhone,
            String customerEmail) {
    }

    /** One selectable shipping option. Persist/select by {@code serviceToken}, not {@code rateId}. */
    public record RateOption(
            String rateId,
            String carrier,
            String serviceLevel,
            String serviceToken,
            BigDecimal amount,
            String currency,
            Integer estimatedDays,
            String durationTerms,
            String providerImageUrl) {
    }

    public record RateQuoteResponse(
            String currency,
            List<RateOption> rates) {
    }

    /** Vendor buys a label for an order; rateId optional (defaults to the order's stored rate). */
    public record BuyLabelRequest(
            String rateId) {
    }

    public record ShipmentResponse(
            UUID id,
            UUID orderId,
            String provider,
            ShipmentStatus status,
            String carrier,
            String serviceLevel,
            String serviceToken,
            BigDecimal amount,
            String currency,
            Integer estimatedDays,
            String trackingNumber,
            String trackingUrl,
            String labelUrl,
            String trackingStatusDetail,
            Instant eta,
            Instant createdAt) {
    }

    public record TrackingEventResponse(
            ShipmentStatus status,
            String statusDetails,
            String location,
            Instant occurredAt) {
    }

    public record TrackingResponse(
            String carrier,
            String trackingNumber,
            ShipmentStatus status,
            Instant eta,
            List<TrackingEventResponse> events) {
    }
}