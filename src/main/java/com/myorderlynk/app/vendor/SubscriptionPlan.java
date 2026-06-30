package com.myorderlynk.app.vendor;

import com.myorderlynk.app.common.BaseEntity;
import com.myorderlynk.app.common.enums.VendorPlan;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Admin-editable pricing for one subscription tier (one row per {@link VendorPlan}). Holds the
 * monthly fee billed to the vendor and the commission rate that {@link SubscriptionBillingService}
 * materializes onto a vendor when the plan is assigned. Seeded with the proposed defaults on first
 * boot (see {@link SubscriptionPlanService}); edited via the admin subscriptions API.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "subscription_plans")
public class SubscriptionPlan extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, unique = true, length = 20)
    private VendorPlan plan;

    /** Customer-facing label, e.g. "Growth". */
    @Column(nullable = false)
    private String displayName;

    /** Monthly subscription fee in {@link #currency}. 0 = free tier (no invoice generated). */
    @Column(nullable = false)
    private BigDecimal monthlyFee;

    /** Commission rate materialized onto a vendor when this plan is assigned (e.g. 0.06 = 6%). */
    @Column(nullable = false)
    private BigDecimal commissionRate;

    /** Billing currency for the monthly fee. */
    @Column(nullable = false, length = 8)
    private String currency = "CAD";

    public SubscriptionPlan(VendorPlan plan, String displayName, BigDecimal monthlyFee,
                            BigDecimal commissionRate, String currency) {
        this.plan = plan;
        this.displayName = displayName;
        this.monthlyFee = monthlyFee;
        this.commissionRate = commissionRate;
        this.currency = currency;
    }
}
