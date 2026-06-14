package com.myorderlynk.app.admin;

import com.myorderlynk.app.vendor.VendorDtos.VendorResponse;

import java.math.BigDecimal;
import java.util.List;

/** DTOs for the platform admin console. */
public final class AdminDtos {

    private AdminDtos() {
    }

    /** Platform headline metrics plus a bounded preview of vendors awaiting approval. */
    public record AdminSummary(
            long vendorCount,
            long activeVendorCount,
            long pendingCount,
            long orderCount,
            long paidOrderCount,
            BigDecimal grossRevenue,
            BigDecimal platformRevenue,
            List<VendorResponse> pendingVendors) {
    }
}
