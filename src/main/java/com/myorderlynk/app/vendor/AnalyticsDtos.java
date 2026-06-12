package com.myorderlynk.app.vendor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** DTOs for vendor customer lists, broadcasts and sales analytics. */
public final class AnalyticsDtos {

    private AnalyticsDtos() {
    }

    /** A distinct customer of a vendor, aggregated across their orders. */
    public record CustomerSummary(
            String name,
            String phone,
            String email,
            String city,
            long orderCount,
            BigDecimal totalSpent,
            Instant lastOrderAt) {
    }

    /** A product's sales performance for a vendor. */
    public record ProductSalesSummary(
            UUID productId,
            String productName,
            long quantitySold,
            BigDecimal revenue) {
    }

    /** Headline metrics plus the top-5 customers and products for a vendor (within an optional date range). */
    public record VendorAnalytics(
            long totalOrders,
            long paidOrders,
            BigDecimal grossRevenue,
            long uniqueCustomers,
            List<CustomerSummary> topCustomers,
            List<ProductSalesSummary> topProducts) {
    }

    /** A message a vendor broadcasts to its customers. */
    public record BroadcastRequest(
            @NotBlank @Size(max = 200) String subject,
            @NotBlank @Size(max = 2000) String message) {
    }

    /** Outcome of a broadcast: how many customers were targeted vs. actually reachable. */
    public record BroadcastResult(
            int recipients,
            int totalCustomers) {
    }
}