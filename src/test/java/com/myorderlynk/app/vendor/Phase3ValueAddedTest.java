package com.myorderlynk.app.vendor;

import com.myorderlynk.app.common.enums.VendorStatus;
import com.myorderlynk.app.exception.ApiException;
import com.myorderlynk.app.finance.PayoutDtos.PayoutResponse;
import com.myorderlynk.app.finance.PayoutService;
import com.myorderlynk.app.payment.PaymentClient;
import com.myorderlynk.app.payment.PaymentDtos.InstantPayoutResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/** Phase 3: featured-placement purchase (stacking) and instant payout fee. */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class Phase3ValueAddedTest {

    @Autowired
    FeaturedPlacementService featured;
    @Autowired
    VendorRepository vendors;
    @Autowired
    PayoutService payoutService;
    @MockitoBean
    PaymentClient paymentClient;

    private Vendor newVendor() {
        Vendor v = new Vendor();
        v.setBusinessName("Test Co");
        v.setStoreSlug("test-" + UUID.randomUUID());
        v.setVerificationStatus(VendorStatus.APPROVED);
        v.setActive(true);
        return vendors.save(v);
    }

    @Test
    void featuredPurchaseSetsWindowAndLedgerAndStacks() {
        Vendor v = newVendor();
        assertThat(v.isFeatured()).isFalse();

        FeaturedPlacement first = featured.purchase(v.getId());
        assertThat(first.getDays()).isEqualTo(7);
        assertThat(first.getAmount()).isEqualByComparingTo("25.00");
        assertThat(first.getStatus()).isEqualTo(SubscriptionInvoiceStatus.DUE);

        Vendor afterFirst = vendors.findById(v.getId()).orElseThrow();
        assertThat(afterFirst.isFeatured()).isTrue();
        Instant endAfterFirst = afterFirst.getFeaturedUntil();

        // A second purchase stacks on top of the remaining window (≈ +7 more days).
        FeaturedPlacement second = featured.purchase(v.getId());
        assertThat(second.getStartsAt()).isEqualTo(endAfterFirst);
        long daysBetween = ChronoUnit.DAYS.between(endAfterFirst, second.getEndsAt());
        assertThat(daysBetween).isEqualTo(7);
        assertThat(featured.forVendor(v.getId())).hasSize(2);
    }

    @Test
    void instantPayoutChargesFeeAndRecordsPayout() {
        Vendor v = newVendor();
        // payment-service triggers the Stripe instant payout + card fee; here we mock that boundary.
        when(paymentClient.requestInstantPayout(eq(v.getId()), any(), any(), any(), any()))
                .thenReturn(new InstantPayoutResult("po_1", "pending",
                        new BigDecimal("100.00"), new BigDecimal("1.00"), "CAD"));

        PayoutResponse res = payoutService.requestInstantPayout(v.getId(), new BigDecimal("100.00"), "CAD");

        assertThat(res.instantPayout()).isTrue();
        assertThat(res.instantPayoutFee()).isEqualByComparingTo("1.00"); // 1% default fee on 100
        assertThat(res.netPayout()).isEqualByComparingTo("100.00");      // full amount reaches the vendor's bank
        assertThat(res.payoutStatus()).isEqualTo("INSTANT_PENDING");
    }

    @Test
    void instantPayoutSurfacesPaymentServiceFailure() {
        Vendor v = newVendor();
        when(paymentClient.requestInstantPayout(eq(v.getId()), any(), any(), any(), any()))
                .thenThrow(new RuntimeException("no card on file"));

        assertThatThrownBy(() -> payoutService.requestInstantPayout(v.getId(), new BigDecimal("50.00"), "CAD"))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void instantPayoutRejectsNonPositiveAmount() {
        Vendor v = newVendor();
        assertThatThrownBy(() -> payoutService.requestInstantPayout(v.getId(), BigDecimal.ZERO, "CAD"))
                .isInstanceOf(ApiException.class);
    }
}
