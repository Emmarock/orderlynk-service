package com.myorderlynk.app.order;

import com.myorderlynk.app.batch.ShipmentRequest;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/** Phase 3 fee helpers: cargo handling fee, instant payout fee, and the shipment charge with it. */
class FeeSettingsPhase3Test {

    @Test
    void cargoHandlingFeeIsAPercentOfBaseCharge() {
        FeeSettings s = new FeeSettings(); // default cargoHandlingFeeRate = 0.02
        assertThat(s.cargoHandlingFeeFor(new BigDecimal("200.00"))).isEqualByComparingTo("4.00");
        assertThat(s.cargoHandlingFeeFor(BigDecimal.ZERO)).isEqualByComparingTo("0.00");
        assertThat(s.cargoHandlingFeeFor(null)).isEqualByComparingTo("0.00");
    }

    @Test
    void instantPayoutFeeIsAPercentOfPayout() {
        FeeSettings s = new FeeSettings(); // default instantPayoutFeeRate = 0.01
        assertThat(s.instantPayoutFeeFor(new BigDecimal("100.00"))).isEqualByComparingTo("1.00");
        assertThat(s.instantPayoutFeeFor(BigDecimal.ZERO)).isEqualByComparingTo("0.00");
    }

    @Test
    void shipmentTotalIsBaseChargePlusPlatformCargoFee() {
        ShipmentRequest s = new ShipmentRequest();
        s.setActualWeight(new BigDecimal("10"));   // 10kg
        s.setRatePerKg(new BigDecimal("20.00"));   // -> base 200
        s.setHandlingFee(BigDecimal.ZERO);
        s.setDeliveryFee(BigDecimal.ZERO);
        assertThat(s.baseCharge()).isEqualByComparingTo("200.00");

        FeeSettings settings = new FeeSettings();
        s.setPlatformCargoFee(settings.cargoHandlingFeeFor(s.baseCharge())); // 2% of 200 = 4
        assertThat(s.computeCharge()).isEqualByComparingTo("204.00");
    }
}
