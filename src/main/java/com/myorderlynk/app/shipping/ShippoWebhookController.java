package com.myorderlynk.app.shipping;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;

/**
 * Receives Shippo webhooks (configure the URL in the Shippo dashboard pointing here). We care
 * about {@code track_updated} events — the carrier reports a new scan — and apply the new
 * status/ETA onto the matching {@link Shipment} and its order.
 *
 * <p>Public (Shippo is unauthenticated). The path lives under {@code /api/webhooks/**}, which is
 * permit-all in security config. We always return 200 for well-formed posts so Shippo doesn't
 * retry on our own mapping quirks; only malformed bodies yield 400.
 */
@Slf4j
@RestController
@RequestMapping("/api/webhooks/shippo")
public class ShippoWebhookController {

    private final ShippingService shippingService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ShippoWebhookController(ShippingService shippingService) {
        this.shippingService = shippingService;
    }

    @PostMapping
    public ResponseEntity<Void> receive(@RequestBody String payload) {
        JsonNode root;
        try {
            root = objectMapper.readTree(payload);
        } catch (Exception e) {
            log.warn("Shippo webhook: unparseable body ({})", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
        String event = text(root, "event");
        JsonNode data = root.get("data");
        if (data == null || data.isNull()) {
            log.info("Shippo webhook '{}' with no data — ignoring", event);
            return ResponseEntity.ok().build();
        }

        String trackingNumber = text(data, "tracking_number");
        JsonNode trackingStatus = data.get("tracking_status");
        String statusStr = trackingStatus == null ? text(data, "status") : text(trackingStatus, "status");
        String statusDetail = trackingStatus == null ? null : text(trackingStatus, "status_details");
        Instant eta = parseInstant(text(data, "eta"));
        if (eta == null && trackingStatus != null) {
            eta = parseInstant(text(trackingStatus, "status_date"));
        }

        ShipmentStatus status = mapStatus(statusStr);
        log.info("Shippo webhook '{}': tracking={} status={} ({})", event, trackingNumber, statusStr, status);
        shippingService.applyTrackingWebhook(trackingNumber, status, eta, statusDetail);
        return ResponseEntity.ok().build();
    }

    private static ShipmentStatus mapStatus(String status) {
        if (status == null) {
            return ShipmentStatus.UNKNOWN;
        }
        return switch (status.trim().toUpperCase()) {
            case "PRE_TRANSIT", "TRANSIT" -> ShipmentStatus.IN_TRANSIT;
            case "DELIVERED" -> ShipmentStatus.DELIVERED;
            case "RETURNED" -> ShipmentStatus.RETURNED;
            case "FAILURE" -> ShipmentStatus.FAILED;
            default -> ShipmentStatus.UNKNOWN;
        };
    }

    private static String text(JsonNode node, String field) {
        if (node == null) {
            return null;
        }
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private static Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value).toInstant();
        } catch (DateTimeParseException e) {
            try {
                return Instant.parse(value);
            } catch (DateTimeParseException ignored) {
                return null;
            }
        }
    }
}