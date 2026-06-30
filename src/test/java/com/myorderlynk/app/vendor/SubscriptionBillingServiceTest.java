package com.myorderlynk.app.vendor;

import com.myorderlynk.app.common.enums.VendorPlan;
import com.myorderlynk.app.common.enums.VendorStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.YearMonth;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Phase 2: plan assignment materializes the commission rate, and monthly billing is idempotent. */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SubscriptionBillingServiceTest {

    @Autowired
    SubscriptionBillingService billing;
    @Autowired
    SubscriptionPlanService planService;
    @Autowired
    VendorRepository vendors;
    @Autowired
    VendorSubscriptionInvoiceRepository invoices;

    private Vendor newVendor(VendorPlan plan) {
        Vendor v = new Vendor();
        v.setBusinessName("Test Co");
        v.setStoreSlug("test-" + UUID.randomUUID());
        v.setVerificationStatus(VendorStatus.APPROVED);
        v.setActive(true);
        v.setPlan(plan);
        return vendors.save(v);
    }

    @Test
    void assignPlanMaterializesCatalogCommissionRate() {
        Vendor v = newVendor(VendorPlan.STARTER); // grandfathered default 0.07
        assertThat(v.getCommissionRate()).isEqualByComparingTo("0.07");

        billing.assignPlan(v.getId(), VendorPlan.GROWTH);

        Vendor reloaded = vendors.findById(v.getId()).orElseThrow();
        assertThat(reloaded.getPlan()).isEqualTo(VendorPlan.GROWTH);
        assertThat(reloaded.getCommissionRate())
                .isEqualByComparingTo(planService.byPlan(VendorPlan.GROWTH).getCommissionRate());
    }

    @Test
    void monthlyGenerationBillsPaidPlansOnceAndIsIdempotent() {
        Vendor growth = newVendor(VendorPlan.GROWTH);
        billing.assignPlan(growth.getId(), VendorPlan.GROWTH); // materialize fee/rate

        YearMonth period = YearMonth.of(2026, 7);
        int first = billing.generateMonthlyInvoices(period);
        int second = billing.generateMonthlyInvoices(period); // re-run must not double-bill

        assertThat(first).isGreaterThanOrEqualTo(1);
        assertThat(second).isZero();

        var invs = billing.forVendor(growth.getId());
        assertThat(invs).hasSize(1);
        assertThat(invs.get(0).getAmount())
                .isEqualByComparingTo(planService.byPlan(VendorPlan.GROWTH).getMonthlyFee());
        assertThat(invs.get(0).getStatus()).isEqualTo(SubscriptionInvoiceStatus.DUE);
    }

    @Test
    void starterVendorsAreNotBilled() {
        Vendor starter = newVendor(VendorPlan.STARTER);

        billing.generateMonthlyInvoices(YearMonth.of(2026, 7));

        assertThat(billing.forVendor(starter.getId())).isEmpty();
    }

    @Test
    void markPaidSettlesInvoice() {
        Vendor pro = newVendor(VendorPlan.PRO);
        billing.assignPlan(pro.getId(), VendorPlan.PRO);
        billing.generateMonthlyInvoices(YearMonth.of(2026, 8));
        var inv = billing.forVendor(pro.getId()).get(0);

        var paid = billing.markPaid(inv.getId(), "manual-test");

        assertThat(paid.getStatus()).isEqualTo(SubscriptionInvoiceStatus.PAID);
        assertThat(paid.getPaidAt()).isNotNull();
        assertThat(paid.getReference()).isEqualTo("manual-test");
    }
}
