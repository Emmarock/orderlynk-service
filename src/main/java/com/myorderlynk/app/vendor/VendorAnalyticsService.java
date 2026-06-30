package com.myorderlynk.app.vendor;
import com.myorderlynk.app.notification.NotificationService;

import com.myorderlynk.app.order.Order;
import com.myorderlynk.app.order.OrderItem;
import com.myorderlynk.app.common.enums.FulfillmentStatus;
import com.myorderlynk.app.common.enums.PaymentStatus;
import com.myorderlynk.app.vendor.AnalyticsDtos.BroadcastResult;
import com.myorderlynk.app.vendor.AnalyticsDtos.CustomerSummary;
import com.myorderlynk.app.vendor.AnalyticsDtos.ProductSalesSummary;
import com.myorderlynk.app.vendor.AnalyticsDtos.VendorAnalytics;
import com.myorderlynk.app.order.OrderRepository;
import com.myorderlynk.app.common.PageResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Vendor-facing analytics derived from order history: the customer list (for
 * broadcasts), top customers/products, and headline sales metrics. All views
 * accept a date range so vendors can scope to a period.
 */
@Service
@Slf4j
public class VendorAnalyticsService {

    private static final int TOP_N = 5;

    /** Fulfillment statuses considered "closed" — anything else still needs vendor action. */
    private static final Set<FulfillmentStatus> TERMINAL_FULFILLMENT = Set.of(
            FulfillmentStatus.COMPLETED, FulfillmentStatus.DELIVERED, FulfillmentStatus.CANCELLED);

    private final OrderRepository orders;
    private final com.myorderlynk.app.booking.BookingRepository bookings;
    private final NotificationService notifications;

    public VendorAnalyticsService(OrderRepository orders,
                                  com.myorderlynk.app.booking.BookingRepository bookings,
                                  NotificationService notifications) {
        this.orders = orders;
        this.bookings = bookings;
        this.notifications = notifications;
    }

    /** Distinct customers who have ordered from or booked the vendor, most recent first. */
    @Transactional(readOnly = true)
    public List<CustomerSummary> customers(UUID vendorId, Instant from, Instant to) {
        return aggregateCustomers(activities(vendorId, from, to)).stream()
                .sorted(Comparator.comparing(CustomerSummary::lastOrderAt, Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .toList();
    }

    /** Paginated view of {@link #customers}: the full distinct-customer list, sliced in memory. */
    @Transactional(readOnly = true)
    public PageResponse<CustomerSummary> customersPaged(UUID vendorId, Instant from, Instant to, int page, int size) {
        return PageResponse.of(customers(vendorId, from, to), page, size);
    }

    @Transactional(readOnly = true)
    public VendorAnalytics analytics(UUID vendorId, Instant from, Instant to) {
        List<Order> os = load(vendorId, from, to);

        long paidOrders = os.stream().filter(o -> o.getPaymentStatus() == PaymentStatus.PAID).count();
        long openFulfillment = os.stream()
                // A null status counts as open; Set.of(...).contains(null) would otherwise NPE.
                .filter(o -> o.getFulfillmentStatus() == null || !TERMINAL_FULFILLMENT.contains(o.getFulfillmentStatus()))
                .count();
        List<com.myorderlynk.app.booking.Booking> bs = loadBookings(vendorId, from, to);
        BigDecimal gross = os.stream().map(o -> nz(o.getTotalAmount())).reduce(BigDecimal.ZERO, BigDecimal::add)
                .add(bs.stream().map(b -> nz(b.getTotalAmount())).reduce(BigDecimal.ZERO, BigDecimal::add));

        List<CustomerSummary> customers = aggregateCustomers(activities(os, bs));
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

        return new VendorAnalytics(os.size(), paidOrders, openFulfillment, gross, customers.size(), topCustomers, topProducts);
    }

    /**
     * Broadcast a message to the vendor's customers (those reachable by email within
     * the given range). Delivery is recorded via {@link NotificationService}.
     */
    @Transactional
    public BroadcastResult broadcast(UUID vendorId, String subject, String message, Instant from, Instant to) {
        List<CustomerSummary> customers = aggregateCustomers(activities(vendorId, from, to));
        String body = subject + "\n\n" + message;
        int recipients = 0;
        for (CustomerSummary c : customers) {
            if (c.email() != null && !c.email().isBlank()) {
                notifications.notifyBroadcast(null, "EMAIL", c.email(), "VENDOR_BROADCAST", body);
                recipients++;
            }
        }
        log.info("Broadcast by vendor {}: sent to {} of {} customers", vendorId, recipients, customers.size());
        return new BroadcastResult(recipients, customers.size());
    }

    // ---- helpers ----

    private List<Order> load(UUID vendorId, Instant from, Instant to) {
        Instant start = from == null ? Instant.EPOCH : from;
        Instant end = to == null ? Instant.now() : to;
        return orders.findByVendorIdAndCreatedAtBetweenOrderByCreatedAtDesc(vendorId, start, end);
    }

    private List<com.myorderlynk.app.booking.Booking> loadBookings(UUID vendorId, Instant from, Instant to) {
        Instant start = from == null ? Instant.EPOCH : from;
        Instant end = to == null ? Instant.now() : to;
        return bookings.findByVendorIdAndCreatedAtBetweenOrderByCreatedAtDesc(vendorId, start, end);
    }

    /**
     * A single customer-facing transaction (an order or a service booking), reduced to the fields
     * the customer rollup needs. {@code at} drives "first seen wins" so the newest record supplies
     * the display name/contact.
     */
    private record Activity(String name, String phone, String email, String city,
                            BigDecimal amount, Instant at) {
    }

    /** Vendor's orders + bookings in range as a single newest-first activity stream. */
    private List<Activity> activities(UUID vendorId, Instant from, Instant to) {
        return activities(load(vendorId, from, to), loadBookings(vendorId, from, to));
    }

    private List<Activity> activities(List<Order> os, List<com.myorderlynk.app.booking.Booking> bs) {
        List<Activity> all = new ArrayList<>(os.size() + bs.size());
        for (Order o : os) {
            all.add(new Activity(o.getCustomerName(), o.getCustomerPhone(), o.getCustomerEmail(),
                    o.getDeliveryAddress() == null ? null : o.getDeliveryAddress().getCity(),
                    nz(o.getTotalAmount()), o.getCreatedAt()));
        }
        for (com.myorderlynk.app.booking.Booking b : bs) {
            all.add(new Activity(b.getCustomerName(), b.getCustomerPhone(), b.getCustomerEmail(),
                    b.getServiceAddress() == null ? null : b.getServiceAddress().getCity(),
                    nz(b.getTotalAmount()), b.getCreatedAt()));
        }
        all.sort(Comparator.comparing(Activity::at, Comparator.nullsLast(Comparator.naturalOrder())).reversed());
        return all;
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    /** Group activities into distinct customers. Activities arrive newest-first, so the first seen wins for display + lastOrderAt. */
    private List<CustomerSummary> aggregateCustomers(List<Activity> activities) {
        Map<String, CustomerAcc> byKey = new LinkedHashMap<>();
        for (Activity a : activities) {
            byKey.computeIfAbsent(customerKey(a), k -> new CustomerAcc(a)).add(a);
        }
        List<CustomerSummary> out = new ArrayList<>(byKey.size());
        for (CustomerAcc a : byKey.values()) {
            out.add(new CustomerSummary(a.name, a.phone, a.email, a.city, a.orderCount, a.totalSpent, a.lastOrderAt));
        }
        return out;
    }

    /**
     * Stable identity for a customer. Phone is the primary key — it's required on every
     * order/booking and is the real cross-channel identity for these (WhatsApp-first) vendors, so
     * it merges a person's guest and signed-in activity. Falls back to email, then name.
     */
    private static String customerKey(Activity a) {
        String phoneDigits = a.phone() == null ? "" : a.phone().replaceAll("\\D", "");
        if (!phoneDigits.isBlank()) {
            return "p:" + phoneDigits;
        }
        if (a.email() != null && !a.email().isBlank()) {
            return "e:" + a.email().trim().toLowerCase();
        }
        return "n:" + (a.name() == null ? "" : a.name().trim().toLowerCase());
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

        CustomerAcc(Activity latest) {
            // activities are newest-first, so the one that creates the accumulator is the most recent.
            this.name = latest.name();
            this.phone = latest.phone();
            this.email = latest.email();
            this.city = latest.city();
            this.lastOrderAt = latest.at();
        }

        void add(Activity a) {
            orderCount++;
            totalSpent = totalSpent.add(nz(a.amount()));
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