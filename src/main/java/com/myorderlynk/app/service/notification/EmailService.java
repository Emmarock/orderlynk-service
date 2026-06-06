package com.myorderlynk.app.service.notification;

import com.myorderlynk.app.domain.Order;
import com.myorderlynk.app.domain.enums.FulfillmentStatus;
import com.myorderlynk.app.dto.AnalyticsDtos.ProductSalesSummary;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds and publishes {@link EmailRequestedEvent}s for each kind of system email.
 * Publishing is cheap and non-blocking; rendering + delivery happen asynchronously in
 * {@link EmailEventListener}. Order/customer emails are skipped when no email is on file.
 */
@Slf4j
@Service
public class EmailService {

    private final ApplicationEventPublisher events;
    private final String baseUrl;

    public EmailService(ApplicationEventPublisher events,
                        @Value("${app.public-base-url:http://localhost:5173}") String baseUrl) {
        this.events = events;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    // ---- Account ----

    public void sendWelcome(String toEmail, String name) {
        publish(toEmail, "Welcome to MyOrderLynk 🎉", "welcome",
                Map.of("name", orDefault(name, "there"), "appUrl", baseUrl));
    }

    public void sendEmailVerification(String toEmail, String name, String token) {
        String verifyUrl = baseUrl + "/verify-email?token=" + enc(token);
        publish(toEmail, "Verify your email", "verify-email",
                Map.of("name", orDefault(name, "there"), "verifyUrl", verifyUrl));
    }

    public void sendPasswordReset(String toEmail, String name, String token) {
        String resetUrl = baseUrl + "/reset-password?token=" + enc(token);
        publish(toEmail, "Reset your password", "password-reset",
                Map.of("name", orDefault(name, "there"), "resetUrl", resetUrl));
    }

    // ---- Orders ----

    public void sendOrderCreated(Order order, String vendorName) {
        if (skip(order)) return;
        publish(order.getCustomerEmail(), "We received your order " + order.getPublicOrderId(), "order-created",
                Map.of(
                        "customerName", order.getCustomerName(),
                        "vendorName", vendorName,
                        "orderId", order.getPublicOrderId(),
                        "total", money(order.getTotalAmount(), order.getCurrency()),
                        "trackUrl", trackUrl(order)));
    }

    public void sendPaymentReceived(Order order, String vendorName, BigDecimal amount) {
        if (skip(order)) return;
        publish(order.getCustomerEmail(), "Payment received for " + order.getPublicOrderId(), "payment-received",
                Map.of(
                        "customerName", order.getCustomerName(),
                        "vendorName", vendorName,
                        "orderId", order.getPublicOrderId(),
                        "amount", money(amount, order.getCurrency()),
                        "trackUrl", trackUrl(order)));
    }

    /** Sends the email matching a fulfillment status change; no-op for statuses without a customer email. */
    public void sendOrderStatusChange(Order order, String vendorName, FulfillmentStatus status) {
        if (skip(order)) return;
        String id = order.getPublicOrderId();
        switch (status) {
            case VENDOR_CONFIRMED -> publish(order.getCustomerEmail(), "Order " + id + " confirmed", "order-accepted",
                    Map.of("customerName", order.getCustomerName(), "vendorName", vendorName,
                            "orderId", id, "trackUrl", trackUrl(order)));
            case READY_FOR_PICKUP -> publish(order.getCustomerEmail(), "Order " + id + " is ready for pickup", "order-ready",
                    Map.of("customerName", order.getCustomerName(), "vendorName", vendorName,
                            "orderId", id, "pickupCode", orDefault(order.getPickupCode(), "—"),
                            "trackUrl", trackUrl(order)));
            case DELIVERED, COMPLETED -> publish(order.getCustomerEmail(), "Order " + id + " delivered", "order-delivered",
                    Map.of("customerName", order.getCustomerName(), "vendorName", vendorName,
                            "orderId", id, "trackUrl", trackUrl(order)));
            case CANCELLED -> publish(order.getCustomerEmail(), "Update on order " + id, "order-rejected",
                    Map.of("customerName", order.getCustomerName(), "vendorName", vendorName,
                            "orderId", id, "reason", "The vendor was unable to fulfil this order."));
            default -> { /* no email for intermediate statuses */ }
        }
    }

    // ---- Vendor ----

    public void sendVendorApproved(String toEmail, String vendorName) {
        publish(toEmail, "Your MyOrderLynk store is approved 🎉", "vendor-approved",
                Map.of("vendorName", vendorName, "dashboardUrl", baseUrl + "/vendor"));
    }

    public void sendWeeklySalesSummary(String toEmail, String vendorName, LocalDate periodStart, LocalDate periodEnd,
                                       long orderCount, BigDecimal grossSales, BigDecimal netPayout,
                                       List<ProductSalesSummary> topProducts) {
        Map<String, String> model = new LinkedHashMap<>();
        model.put("vendorName", vendorName);
        model.put("periodStart", periodStart.toString());
        model.put("periodEnd", periodEnd.toString());
        model.put("orderCount", String.valueOf(orderCount));
        model.put("grossSales", money(grossSales, "CAD"));
        model.put("netPayout", money(netPayout, "CAD"));
        model.put("rowsHtml", topProductRows(topProducts));
        model.put("dashboardUrl", baseUrl + "/vendor/manage/earnings");
        publish(toEmail, "Your weekly sales summary", "weekly-sales-summary", model);
    }

    // ---- helpers ----

    private void publish(String to, String subject, String template, Map<String, String> model) {
        if (to == null || to.isBlank()) {
            log.debug("No recipient for '{}' ({}), skipping", subject, template);
            return;
        }
        events.publishEvent(new EmailRequestedEvent(to, subject, template, model));
    }

    private boolean skip(Order order) {
        return order.getCustomerEmail() == null || order.getCustomerEmail().isBlank();
    }

    private String trackUrl(Order order) {
        return baseUrl + "/track?orderId=" + enc(order.getPublicOrderId()) + "&contact=" + enc(order.getCustomerEmail());
    }

    private static String topProductRows(List<ProductSalesSummary> products) {
        if (products == null || products.isEmpty()) {
            return "<tr><td colspan=\"2\" style=\"padding:12px 14px;color:#9ca3af;font-size:13px;\">No sales this period</td></tr>";
        }
        StringBuilder sb = new StringBuilder();
        for (ProductSalesSummary p : products) {
            sb.append("<tr style=\"border-top:1px solid #ece7df;\">")
                    .append("<td style=\"padding:10px 14px;font-size:14px;\">").append(escape(p.productName())).append("</td>")
                    .append("<td align=\"right\" style=\"padding:10px 14px;font-size:14px;font-weight:600;\">").append(p.quantitySold()).append("</td>")
                    .append("</tr>");
        }
        return sb.toString();
    }

    private static String money(BigDecimal amount, String currency) {
        BigDecimal value = (amount == null ? BigDecimal.ZERO : amount).setScale(2, RoundingMode.HALF_UP);
        return (currency == null ? "CAD" : currency) + " " + value;
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String orDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String enc(String value) {
        return value == null ? "" : UriUtils.encode(value, StandardCharsets.UTF_8);
    }
}