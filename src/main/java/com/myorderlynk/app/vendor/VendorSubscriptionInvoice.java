package com.myorderlynk.app.vendor;

import com.myorderlynk.app.common.BaseEntity;
import com.myorderlynk.app.common.enums.VendorPlan;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A single month's subscription charge for a vendor on a paid plan. One per (vendor, period); the
 * unique constraint makes monthly generation idempotent so a re-run never double-bills.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "vendor_subscription_invoices",
        uniqueConstraints = @UniqueConstraint(name = "uq_vsi_vendor_period",
                columnNames = {"vendor_id", "period_start"}),
        indexes = @Index(name = "idx_vsi_vendor", columnList = "vendor_id"))
public class VendorSubscriptionInvoice extends BaseEntity {

    @Column(name = "vendor_id", nullable = false)
    private UUID vendorId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private VendorPlan plan;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(nullable = false, length = 8)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SubscriptionInvoiceStatus status = SubscriptionInvoiceStatus.DUE;

    /** When the invoice was collected/settled (PAID), else null. */
    private Instant paidAt;

    /** Payment reference once collected (Stripe charge id, or an admin note). */
    private String reference;

    public VendorSubscriptionInvoice(UUID vendorId, VendorPlan plan, LocalDate periodStart,
                                     LocalDate periodEnd, BigDecimal amount, String currency) {
        this.vendorId = vendorId;
        this.plan = plan;
        this.periodStart = periodStart;
        this.periodEnd = periodEnd;
        this.amount = amount;
        this.currency = currency;
        this.status = SubscriptionInvoiceStatus.DUE;
    }
}
