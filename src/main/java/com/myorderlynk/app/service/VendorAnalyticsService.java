package com.myorderlynk.app.service;

import com.myorderlynk.app.domain.Order;
import com.myorderlynk.app.domain.OrderItem;
import com.myorderlynk.app.domain.enums.PaymentStatus;
import com.myorderlynk.app.dto.AnalyticsDtos.BroadcastResult;
import com.myorderlynk.app.dto.AnalyticsDtos.CustomerSummary;
import com.myorderlynk.app.dto.AnalyticsDtos.ProductSalesSummary;
import com.myorderlynk.app.dto.AnalyticsDtos.VendorAnalytics;
import com.myorderlynk.app.repository.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Vendor-facing analytics derived from order history: the customer list (for
 * broadcasts), top customers/products, and headline sales metrics. All views
 * accept a date range so vendors can scope to a period.
 */
@Service
public class VendorAnalyticsService {

    private static final int TOP_N = 5;

    private final OrderRepository orders;
    private final NotificationService notifications;

    public VendorAnalyticsService(OrderRepository orders, NotificationService notifications) {
        this.orders = orders;
        this.notifications = notifications;
    }

    /** Distinct customers who have ordered from the vendor, most recent first. */
    @Transactional(readOnly = true)
    public List<CustomerSummary> customers(UUID vendorId, Instant from, Instant to) {
        return aggregateCustomers(load(vendorId, from, to)).stream()
                .sorted(Comparator.comparing(CustomerSummary::lastOrderAt, Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .toList();
    }

    @Transactional(readOnly = true)
    public VendorAnalytics analytics(UUID vendorId, Instant from, Instant to) {
        List<Order> os = load(vendorId, from, to);

        long paidOrders = os.stream().filter(o -> o.getPaymentStatus() == PaymentStatus.PAID).count();
        BigDecimal gross = os.stream()
                .map(Order::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<CustomerSummary> customers = aggregateCustomers(os);
        List<CustomerSummary> topCustomers = customers.stream()
                .sorted(Comparator.comparing(CustomerSummary::totalSpent).reversed()
                        .thenComparing(CustomerSummary::orderCount, Comparator.reverseOrder()))
                .limit(TOP_N)
                .toList();

        List<ProductSalesSummary> topProducts = aggregateProducts(os).stream()
                .sorted(Comparator.comparingLong(ProductSalesSummary::quantitySold).reversed()
                        .thenComparing(ProductSalesSummary::revenue, Comparator.reverseOrder()))
                .limit(TOP_N)
                .toList();

        return new VendorAnalytics(os.size(), paidOrders, gross, customers.size(), topCustomers, topProducts);
    }

    /**
     * Broadcast a message to the vendor's customers (those reachable by email within
     * the given range). Delivery is recorded via {@link NotificationService}.
     */
    @Transactional
    public BroadcastResult broadcast(UUID vendorId, String subject, String message, Instant from, Instant to) {
        List<CustomerSummary> customers = aggregateCustomers(load(vendorId, from, to));
        String body = subject + "\n\n" + message;
        int recipients = 0;
        for (CustomerSummary c : customers) {
            if (c.email() != null && !c.email().isBlank()) {
                notifications.notifyBroadcast(null, "EMAIL", c.email(), "VENDOR_BROADCAST", body);
                recipients++;
            }
        }
        return new BroadcastResult(recipients, customers.size());
    }

    // ---- helpers ----

    private List<Order> load(UUID vendorId, Instant from, Instant to) {
        Instant start = from == null ? Instant.EPOCH : from;
        Instant end = to == null ? Instant.now() : to;
        return orders.findByVendorIdAndCreatedAtBetweenOrderByCreatedAtDesc(vendorId, start, end);
    }

    /** Group orders into distinct customers. Orders arrive newest-first, so the first seen wins for display + lastOrderAt. */
    private List<CustomerSummary> aggregateCustomers(List<Order> os) {
        Map<String, CustomerAcc> byKey = new LinkedHashMap<>();
        for (Order o : os) {
            byKey.computeIfAbsent(customerKey(o), k -> new CustomerAcc(o)).add(o);
        }
        List<CustomerSummary> out = new ArrayList<>(byKey.size());
        for (CustomerAcc a : byKey.values()) {
            out.add(new CustomerSummary(a.name, a.phone, a.email, a.city, a.orderCount, a.totalSpent, a.lastOrderAt));
        }
        return out;
    }

    /**
     * Stable identity for a customer. Phone is the primary key — it's required on every
     * order and is the real cross-channel identity for these (WhatsApp-first) vendors, so
     * it merges a person's guest and signed-in orders. Falls back to email, account id, then name.
     */
    private static String customerKey(Order o) {
        String phoneDigits = o.getCustomerPhone() == null ? "" : o.getCustomerPhone().replaceAll("\\D", "");
        if (!phoneDigits.isBlank()) {
            return "p:" + phoneDigits;
        }
        if (o.getCustomerEmail() != null && !o.getCustomerEmail().isBlank()) {
            return "e:" + o.getCustomerEmail().trim().toLowerCase();
        }
        if (o.getCustomerUserId() != null) {
            return "u:" + o.getCustomerUserId();
        }
        return "n:" + (o.getCustomerName() == null ? "" : o.getCustomerName().trim().toLowerCase());
    }

    private List<ProductSalesSummary> aggregateProducts(List<Order> os) {
        Map<UUID, ProductAcc> byProduct = new LinkedHashMap<>();
        for (Order o : os) {
            for (OrderItem item : o.getItems()) {
                byProduct.computeIfAbsent(item.getProductId(), k -> new ProductAcc(item.getProductNameSnapshot()))
                        .add(item);
            }
        }
        List<ProductSalesSummary> out = new ArrayList<>(byProduct.size());
        byProduct.forEach((id, a) -> out.add(new ProductSalesSummary(id, a.name, a.quantity, a.revenue)));
        return out;
    }

    private static final class CustomerAcc {
        final String name;
        final String phone;
        final String email;
        final String city;
        final Instant lastOrderAt;
        long orderCount;
        BigDecimal totalSpent = BigDecimal.ZERO;

        CustomerAcc(Order latest) {
            // os is newest-first, so the order that creates the accumulator is the most recent one.
            this.name = latest.getCustomerName();
            this.phone = latest.getCustomerPhone();
            this.email = latest.getCustomerEmail();
            this.city = latest.getCustomerCity();
            this.lastOrderAt = latest.getCreatedAt();
        }

        void add(Order o) {
            orderCount++;
            totalSpent = totalSpent.add(o.getTotalAmount());
        }
    }

    private static final class ProductAcc {
        final String name;
        long quantity;
        BigDecimal revenue = BigDecimal.ZERO;

        ProductAcc(String name) {
            this.name = name;
        }

        void add(OrderItem item) {
            quantity += item.getQuantity();
            revenue = revenue.add(item.getLineTotal());
        }
    }
}