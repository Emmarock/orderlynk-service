package com.myorderlynk.app.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.myorderlynk.app.domain.Order;
import com.myorderlynk.app.domain.PaymentRecord;
import com.myorderlynk.app.domain.enums.FulfillmentStatus;
import com.myorderlynk.app.domain.enums.PaymentMethod;
import com.myorderlynk.app.domain.enums.PaymentStatus;
import com.myorderlynk.app.repository.OrderRepository;
import com.myorderlynk.app.repository.PaymentRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

/**
 * Applies inbound payment-service domain events to local order/payment state.
 * Idempotent: each event id is processed at most once (deduplicated in the same
 * transaction as the state change), so re-delivered events are safe.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentEventService {

    private final OrderRepository orders;
    private final PaymentRecordRepository payments;
    private final ProcessedPaymentEventRepository processed;

    // Instantiated directly (matches ShippoWebhookController/TwilioWhatsAppProvider):
    // Boot 4 auto-configures only the Jackson 3 ObjectMapper, so there is no
    // com.fasterxml bean to inject. A field initializer keeps it out of the
    // @RequiredArgsConstructor-generated constructor.
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Transactional
    public void apply(String rawBody) {
        JsonNode envelope = read(rawBody);
        String eventId = envelope.path("eventId").asText(null);
        String eventType = envelope.path("eventType").asText("");
        JsonNode payload = envelope.path("payload");

        if (eventId == null) {
            log.warn("Payment event missing eventId; ignoring");
            return;
        }
        if (processed.existsByEventId(eventId)) {
            log.debug("Payment event {} already processed; skipping", eventId);
            return;
        }

        switch (eventType) {
            case "PAYMENT_SUCCEEDED" -> onSucceeded(payload);
            case "PAYMENT_REFUNDED" -> onRefunded(payload);
            case "PAYMENT_FAILED" -> onFailed(payload);
            default -> log.info("Payment event {} ({}) acknowledged, no local action", eventId, eventType);
        }

        ProcessedPaymentEvent record = new ProcessedPaymentEvent();
        record.setEventId(eventId);
        record.setEventType(eventType);
        processed.save(record);
    }

    private void onSucceeded(JsonNode payload) {
        findOrder(payload).ifPresent(order -> {
            if (order.getPaymentStatus() == PaymentStatus.PAID) {
                return;
            }
            BigDecimal amount = decimal(payload, "grossAmount", order.getTotalAmount());
            String reference = payload.path("reference").asText(null);

            order.setPaymentStatus(PaymentStatus.PAID);
            if (order.getFulfillmentStatus() == FulfillmentStatus.ORDER_RECEIVED) {
                order.setFulfillmentStatus(FulfillmentStatus.PAID);
            }
            orders.save(order);

            PaymentRecord rec = new PaymentRecord();
            rec.setOrderId(order.getId());
            rec.setCustomerUserId(order.getCustomerUserId());
            rec.setVendorId(order.getVendorId());
            rec.setAmountPaid(amount);
            rec.setPaymentMethod(PaymentMethod.STRIPE);
            rec.setPaymentStatus(PaymentStatus.PAID);
            rec.setTransactionReference(reference);
            rec.setStripePaymentId(reference);
            rec.setPaidDate(Instant.now());
            payments.save(rec);

            log.info("Order {} marked PAID from payment-service event (ref {})",
                    order.getPublicOrderId(), reference);
        });
    }

    private void onRefunded(JsonNode payload) {
        findOrder(payload).ifPresent(order -> {
            BigDecimal amount = decimal(payload, "amount", BigDecimal.ZERO);
            BigDecimal refunded = order.getRefundedAmount() == null ? BigDecimal.ZERO : order.getRefundedAmount();
            order.setRefundedAmount(refunded.add(amount));
            if (order.getRefundedAmount().compareTo(order.getTotalAmount()) >= 0) {
                order.setPaymentStatus(PaymentStatus.REFUNDED);
            }
            orders.save(order);
            log.info("Order {} refunded {} from payment-service event", order.getPublicOrderId(), amount);
        });
    }

    private void onFailed(JsonNode payload) {
        findOrder(payload).ifPresent(order -> {
            if (order.getPaymentStatus() == PaymentStatus.PENDING) {
                order.setPaymentStatus(PaymentStatus.FAILED);
                orders.save(order);
                log.info("Order {} marked FAILED from payment-service event", order.getPublicOrderId());
            }
        });
    }

    private Optional<Order> findOrder(JsonNode payload) {
        String orderId = payload.path("orderId").asText(null);
        if (orderId == null) {
            log.warn("Payment event payload has no orderId; cannot map to an order");
            return Optional.empty();
        }
        Optional<Order> order = orders.findByPublicOrderId(orderId);
        if (order.isEmpty()) {
            log.warn("No order found for publicOrderId {} from payment event", orderId);
        }
        return order;
    }

    private BigDecimal decimal(JsonNode payload, String field, BigDecimal fallback) {
        JsonNode node = payload.path(field);
        return node.isMissingNode() || node.isNull() ? fallback : new BigDecimal(node.asText());
    }

    private JsonNode read(String rawBody) {
        try {
            return objectMapper.readTree(rawBody);
        } catch (Exception e) {
            throw new IllegalArgumentException("Malformed payment event body", e);
        }
    }
}