package com.myorderlynk.app.service.notification;

import com.myorderlynk.app.domain.Order;
import com.myorderlynk.app.domain.Vendor;
import com.myorderlynk.app.domain.enums.FulfillmentStatus;
import com.myorderlynk.app.domain.enums.PaymentStatus;
import com.myorderlynk.app.service.OrderLinks;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/**
 * Builds and publishes WhatsApp messages for order events, to both the customer (their
 * phone) and the vendor (their WhatsApp number). Each message carries a template key + ordered
 * variables (used for an approved Content template when configured) and a plain-text fallback.
 *
 * <p>Content template variable contracts (configure matching templates in Twilio):
 * <ul>
 *   <li>{@code order-created}: 1=customer first name, 2=order id, 3=vendor name, 4=track url</li>
 *   <li>{@code order-status}: 1=order id, 2=status, 3=track url</li>
 *   <li>{@code payment}: 1=order id, 2=payment status, 3=track url</li>
 *   <li>{@code vendor-new-order}: 1=order id, 2=customer name, 3=total</li>
 *   <li>{@code vendor-order-status}: 1=order id, 2=status</li>
 *   <li>{@code vendor-payment}: 1=order id, 2=payment status</li>
 * </ul>
 * Customer messages are transactional (always sent); vendor messages respect {@code notifyByWhatsapp}.
 */
@Slf4j
@Service
public class WhatsAppService {

    private final ApplicationEventPublisher events;
    private final OrderLinks orderLinks;
    private final String baseUrl;

    public WhatsAppService(ApplicationEventPublisher events, OrderLinks orderLinks,
                           @Value("${app.public-base-url:http://localhost:5173}") String baseUrl) {
        this.events = events;
        this.orderLinks = orderLinks;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    public void orderCreated(Order order, Vendor vendor) {
        String track = trackUrl(order);
        String id = order.getPublicOrderId();
        publish(order.getCustomerPhone(), "order-created",
                List.of(firstName(order), id, vendor.getBusinessName(), track),
                "Hi " + firstName(order) + "! Your order " + id + " with " + vendor.getBusinessName()
                        + " has been received. Track it here: " + track,
                order.getId());
        if (vendor.isNotifyByWhatsapp()) {
            publish(vendor.getWhatsappNumber(), "vendor-new-order",
                    List.of(id, order.getCustomerName(), money(order.getTotalAmount(), order.getCurrency())),
                    "🛒 New order " + id + " from " + order.getCustomerName()
                            + " for " + money(order.getTotalAmount(), order.getCurrency()) + ". Manage it: " + dashboardUrl(),
                    order.getId());
        }
    }

    public void fulfillmentUpdated(Order order, Vendor vendor, FulfillmentStatus status) {
        String id = order.getPublicOrderId();
        String state = label(status.name());
        publish(order.getCustomerPhone(), "order-status",
                List.of(id, state, trackUrl(order)),
                customerFulfillmentMessage(order, vendor, status),
                order.getId());
        if (vendor.isNotifyByWhatsapp()) {
            publish(vendor.getWhatsappNumber(), "vendor-order-status",
                    List.of(id, state), "Order " + id + " is now " + state + ".", order.getId());
        }
    }

    public void paymentUpdated(Order order, Vendor vendor, PaymentStatus status) {
        String id = order.getPublicOrderId();
        String state = label(status.name());
        String customerMsg = status == PaymentStatus.PAID
                ? "✅ Payment received for order " + id + ". Thank you! Track it: " + trackUrl(order)
                : "Payment update for order " + id + ": " + state + ". " + trackUrl(order);
        publish(order.getCustomerPhone(), "payment",
                List.of(id, state, trackUrl(order)), customerMsg, order.getId());
        if (vendor.isNotifyByWhatsapp()) {
            publish(vendor.getWhatsappNumber(), "vendor-payment",
                    List.of(id, state), "Payment for order " + id + " is now " + state + ".", order.getId());
        }
    }

    // ---- message building ----

    private String customerFulfillmentMessage(Order order, Vendor vendor, FulfillmentStatus status) {
        String id = order.getPublicOrderId();
        String track = " Track it: " + trackUrl(order);
        return switch (status) {
            case VENDOR_CONFIRMED -> vendor.getBusinessName() + " confirmed your order " + id + " and is preparing it." + track;
            case READY_FOR_PICKUP -> "Your order " + id + " is ready for pickup"
                    + (order.getPickupCode() != null ? " — pickup code " + order.getPickupCode() + "." : ".") + track;
            case SHIPPED -> "Your order " + id + " has shipped." + track;
            case OUT_FOR_DELIVERY -> "Your order " + id + " is out for delivery." + track;
            case DELIVERED, COMPLETED -> "Your order " + id + " has been delivered. Enjoy!" + track;
            case CANCELLED -> "Your order " + id + " has been cancelled. If this is unexpected, contact "
                    + vendor.getBusinessName() + ".";
            default -> "Update on your order " + id + ": " + label(status.name()) + "." + track;
        };
    }

    private void publish(String phone, String template, List<String> variables, String body, UUID orderId) {
        if (phone == null || phone.isBlank()) {
            return;
        }
        events.publishEvent(new WhatsAppRequestedEvent(phone, template, variables, body, orderId));
    }

    private String trackUrl(Order order) {
        return orderLinks.trackUrl(order);
    }

    private String dashboardUrl() {
        return baseUrl + "/vendor/manage/orders";
    }

    private static String firstName(Order order) {
        String name = order.getCustomerName();
        return name == null || name.isBlank() ? "there" : name.split("\\s+")[0];
    }

    private static String label(String enumName) {
        String lower = enumName.replace('_', ' ').toLowerCase();
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    private static String money(BigDecimal amount, String currency) {
        BigDecimal v = (amount == null ? BigDecimal.ZERO : amount).setScale(2, RoundingMode.HALF_UP);
        return (currency == null ? "CAD" : currency) + " " + v;
    }

    private static String enc(String value) {
        return value == null ? "" : UriUtils.encode(value, StandardCharsets.UTF_8);
    }
}