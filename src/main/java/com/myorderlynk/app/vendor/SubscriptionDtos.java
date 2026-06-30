package com.myorderlynk.app.vendor;

import com.myorderlynk.app.common.enums.VendorPlan;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** DTOs for the vendor subscription catalog and monthly invoices ({@code /api/admin/subscriptions}). */
public final class SubscriptionDtos {

    private SubscriptionDtos() {
    }

    public record PlanResponse(
            VendorPlan plan,
            String displayName,
            BigDecimal monthlyFee,
            BigDecimal commissionRate,
            String currency) {
    }

    /** Update one tier's pricing. {@code commissionRate} is a fraction in [0, 1]. */
    public record UpdatePlanRequest(
            @NotBlank String displayName,
            @NotNull @PositiveOrZero BigDecimal monthlyFee,
            @NotNull @DecimalMin("0.0") @DecimalMax("1.0") BigDecimal commissionRate,
            @NotBlank String currency) {
    }

    public record InvoiceResponse(
            UUID id,
            UUID vendorId,
            VendorPlan plan,
            LocalDate periodStart,
            LocalDate periodEnd,
            BigDecimal amount,
            String currency,
            SubscriptionInvoiceStatus status,
            Instant paidAt,
            Instant createdAt) {
    }

    /** Result of a monthly generation run. */
    public record GenerateResult(String period, int generated) {
    }

    public static PlanResponse toResponse(SubscriptionPlan p) {
        return new PlanResponse(p.getPlan(), p.getDisplayName(), p.getMonthlyFee(),
                p.getCommissionRate(), p.getCurrency());
    }

    public static InvoiceResponse toResponse(VendorSubscriptionInvoice i) {
        return new InvoiceResponse(i.getId(), i.getVendorId(), i.getPlan(), i.getPeriodStart(),
                i.getPeriodEnd(), i.getAmount(), i.getCurrency(), i.getStatus(), i.getPaidAt(),
                i.getCreatedAt());
    }
}
