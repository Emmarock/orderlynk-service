package com.myorderlynk.app.service.notification;

import com.myorderlynk.app.domain.Order;
import com.myorderlynk.app.domain.Vendor;
import com.myorderlynk.app.domain.enums.FulfillmentStatus;
import com.myorderlynk.app.domain.enums.PaymentStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;

/**
 * Builds and publishes WhatsApp messages for order events, to both the customer (their
 * phone) and the vendor (their WhatsApp number). Customer messages are transactional and
 * always sent; vendor messages respect the vendor's {@code notifyByWhatsapp} preference.
 */
@Slf4j
@Service
public class WhatsAppService {

    private final ApplicationEventPublisher events;
    private final String baseUrl;

    public WhatsAppService(ApplicationEventPublisher events,
                           @Value("${app.public-base-url:http://localhost:5173}") String baseUrl) {
        this.events = events;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    /** New order placed → confirm to the customer, alert the vendor. */
    public void orderCreated(Order order, Vendor vendor) {
        toCustomer(order, "Hi " + firstName(order) + "! Your order " + order.getPublicOrderId()
                + " with " + vendor.getBusinessName() + " has been received. Track it here: " + trackUrl(order));
        toVendor(vendor, "🛒 New order " + order.getPublicOrderId() + " from " + order.getCustomerName()
                + " for " + money(order.getTotalAmount(), order.getCurrency()) + ". Manage it: " + dashboardUrl());
    }

    /** Fulfillment status changed → update both parties. */
    public void fulfillmentUpdated(Order order, Vendor vendor, FulfillmentStatus status) {
        toCustomer(order, customerFulfillmentMessage(order, vendor, status));
        toVendor(vendor, "Order " + order.getPublicOrderId() + " is now " + label(status.name()) + ".");
    }

    /** Payment status changed → update both parties. */
    public void paymentUpdated(Order order, Vendor vendor, PaymentStatus status) {
        String state = label(status.name());
        String customerMsg = status == PaymentStatus.PAID
                ? "✅ Payment received for order " + order.getPublicOrderId() + ". Thank you! Track it: " + trackUrl(order)
                : "Payment update for order " + order.getPublicOrderId() + ": " + state + ". " + trackUrl(order);
        toCustomer(order, customerMsg);
        toVendor(vendor, "Payment for order " + order.getPublicOrderId() + " is now " + state + ".");
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

    // ---- recipients ----

    private void toCustomer(Order order, String body) {
        publish(order.getCustomerPhone(), body);
    }

    private void toVendor(Vendor vendor, String body) {
        if (vendor.isNotifyByWhatsapp()) {
            publish(vendor.getWhatsappNumber(), body);
        }
    }

    private void publish(String phone, String body) {
        if (phone == null || phone.isBlank()) {
            return;
        }
        events.publishEvent(new WhatsAppRequestedEvent(phone, body));
    }

    // ---- helpers ----

    private String trackUrl(Order order) {
        return baseUrl + "/track?orderId=" + enc(order.getPublicOrderId()) + "&contact=" + enc(order.getCustomerPhone());
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