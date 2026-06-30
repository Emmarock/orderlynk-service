package com.myorderlynk.app.order;

import com.myorderlynk.app.common.enums.FulfillmentType;
import com.myorderlynk.app.common.enums.PaymentMethod;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/** Locks the Phase 1 leak-plugging math: logistics markup-on-top and processing gross-up. */
class FeeCalculatorTest {

    /** A calculator backed by a fixed settings policy (no DB needed). */
    private FeeCalculator calculatorWith(FeeSettings settings) {
        FeeSettingsService stub = new FeeSettingsService(null, null) {
            @Override
            public FeeSettings current() {
                return settings;
            }
        };
        return new FeeCalculator(stub);
    }

    /** Defaults mirror application.yaml: 3% service, 2.9%+0.5% buffer, gross-up on, 12% logistics markup. */
    private FeeSettings defaultSettings() {
        return new FeeSettings(); // field initializers already encode the Phase 1 policy
    }

    @Test
    void logisticsMarkupIsAddedOnTopSoCarrierIsPaidInFull() {
        FeeCalculator calc = calculatorWith(defaultSettings());

        // Live carrier rate of 10.00; 12% markup => customer pays 11.20, carrier still gets 10.00.
        FeeCalculator.FeeBreakdown fb = calc.calculate(
                new BigDecimal("100.00"), FulfillmentType.LOCAL_DELIVERY, PaymentMethod.CASH,
                new BigDecimal("0.07"), new BigDecimal("10.00"));

        assertThat(fb.logisticsFee()).isEqualByComparingTo("11.20");      // base + markup, customer-facing
        assertThat(fb.logisticsPayable()).isEqualByComparingTo("10.00");  // carrier paid in full
        // platformRevenue = platformFee(3) + commission(7) + logistics markup(1.20)
        assertThat(fb.platformRevenue()).isEqualByComparingTo("11.20");
    }

    @Test
    void pickupCarriesNoLogisticsMarkup() {
        FeeCalculator.FeeBreakdown fb = calculatorWith(defaultSettings()).calculate(
                new BigDecimal("100.00"), FulfillmentType.LOCAL_PICKUP, PaymentMethod.CASH,
                new BigDecimal("0.07"), null);

        assertThat(fb.logisticsFee()).isEqualByComparingTo("0.00");
        assertThat(fb.logisticsPayable()).isEqualByComparingTo("0.00");
    }

    @Test
    void cashPaymentHasNoProcessingFee() {
        FeeCalculator.FeeBreakdown fb = calculatorWith(defaultSettings()).calculate(
                new BigDecimal("100.00"), FulfillmentType.LOCAL_PICKUP, PaymentMethod.CASH,
                new BigDecimal("0.07"), null);

        assertThat(fb.processingFee()).isEqualByComparingTo("0.00");
    }

    @Test
    void cardProcessingFeeIsGrossedUpToFullyRecoverProcessorCut() {
        FeeCalculator.FeeBreakdown fb = calculatorWith(defaultSettings()).calculate(
                new BigDecimal("100.00"), FulfillmentType.LOCAL_PICKUP, PaymentMethod.CARD,
                new BigDecimal("0.07"), null);

        // base = subtotal 100 + logistics 0 + platformFee 3 = 103
        // r = 0.029 + 0.005 = 0.034 ; fee = (103*0.034 + 0.30)/(1-0.034) = 3.802/0.966 = 3.9358 -> 3.94
        assertThat(fb.processingFee()).isEqualByComparingTo("3.94");

        // The processor's cut on the grand total must be covered by the fee we charged.
        BigDecimal total = fb.totalAmount();
        BigDecimal processorCut = total.multiply(new BigDecimal("0.034")).add(new BigDecimal("0.30"));
        assertThat(fb.processingFee()).isGreaterThanOrEqualTo(processorCut.subtract(new BigDecimal("0.01")));
    }

    @Test
    void grossUpCanBeDisabled() {
        FeeSettings settings = defaultSettings();
        settings.setGrossUpProcessing(false);
        FeeCalculator.FeeBreakdown fb = calculatorWith(settings).calculate(
                new BigDecimal("100.00"), FulfillmentType.LOCAL_PICKUP, PaymentMethod.CARD,
                new BigDecimal("0.07"), null);

        // Without gross-up: 103 * 0.034 + 0.30 = 3.802 -> 3.80
        assertThat(fb.processingFee()).isEqualByComparingTo("3.80");
    }
}