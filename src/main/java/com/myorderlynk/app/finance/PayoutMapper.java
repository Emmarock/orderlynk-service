package com.myorderlynk.app.finance;

import org.springframework.stereotype.Component;

/** Maps {@link Payout} entities to API response records. */
@Component
public class PayoutMapper {

    public PayoutDtos.PayoutResponse payout(Payout p) {
        return new PayoutDtos.PayoutResponse(
                p.getId(), p.getVendorId(), p.getPeriodStart(), p.getPeriodEnd(), p.getGrossSales(),
                p.getPlatformFees(), p.getLogisticsFees(), p.getRefunds(), p.getNetPayout(),
                p.getPayoutStatus(), p.getPaidDate());
    }
}
