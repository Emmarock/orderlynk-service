package com.myorderlynk.app.vendor;

import com.myorderlynk.app.common.enums.VendorPlan;
import com.myorderlynk.app.common.enums.VendorStatus;
import com.myorderlynk.app.payment.PaymentClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.YearMonth;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/** Increment 2: a DUE subscription invoice is auto-collected via the payment-service platform charge. */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SubscriptionCollectionTest {

    @MockitoBean
    PaymentClient paymentClient;
    @Autowired
    SubscriptionBillingService billing;
    @Autowired
    VendorRepository vendors;

    /** A GROWTH vendor with one generated invoice; returns the vendor id. */
    private UUID growthVendorWithInvoice() {
        Vendor v = new Vendor();
        v.setBusinessName("Test Co");
        v.setStoreSlug("test-" + UUID.randomUUID());
        v.setVerificationStatus(VendorStatus.APPROVED);
        v.setActive(true);
        v.setPlan(VendorPlan.GROWTH);
        v = vendors.save(v);
        billing.assignPlan(v.getId(), VendorPlan.GROWTH);
        billing.generateMonthlyInvoices(YearMonth.of(2026, 7));
        return v.getId();
    }

    @Test
    void collectsDueInvoiceWhenChargeSucceeds() {
        when(paymentClient.chargeVendor(any(), any(), any(), eq("SUBSCRIPTION"), any()))
                .thenReturn("TXN-SUBINV-OK");
        UUID vendorId = growthVendorWithInvoice();
        UUID invoiceId = billing.forVendor(vendorId).get(0).getId();

        boolean collected = billing.collectInvoice(invoiceId);

        assertThat(collected).isTrue();
        VendorSubscriptionInvoice inv = billing.forVendor(vendorId).get(0);
        assertThat(inv.getStatus()).isEqualTo(SubscriptionInvoiceStatus.PAID);
        assertThat(inv.getReference()).isEqualTo("TXN-SUBINV-OK");
        assertThat(inv.getPaidAt()).isNotNull();
    }

    @Test
    void leavesInvoiceDueWhenChargeCannotBeCollected() {
        when(paymentClient.chargeVendor(any(), any(), any(), eq("SUBSCRIPTION"), any()))
                .thenReturn(null); // e.g. insufficient vendor balance, or service unreachable
        UUID vendorId = growthVendorWithInvoice();
        UUID invoiceId = billing.forVendor(vendorId).get(0).getId();

        boolean collected = billing.collectInvoice(invoiceId);

        assertThat(collected).isFalse();
        assertThat(billing.forVendor(vendorId).get(0).getStatus()).isEqualTo(SubscriptionInvoiceStatus.DUE);
        assertThat(billing.dueInvoiceIds()).contains(invoiceId);
    }
}
