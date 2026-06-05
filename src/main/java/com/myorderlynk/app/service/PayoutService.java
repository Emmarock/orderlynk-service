package com.myorderlynk.app.service;

import com.myorderlynk.app.domain.Order;
import com.myorderlynk.app.domain.Payout;
import com.myorderlynk.app.domain.Vendor;
import com.myorderlynk.app.domain.enums.PaymentStatus;
import com.myorderlynk.app.dto.Mapper;
import com.myorderlynk.app.dto.PayoutDtos.PayoutResponse;
import com.myorderlynk.app.repo.OrderRepository;
import com.myorderlynk.app.repo.PayoutRepository;
import com.myorderlynk.app.repo.VendorRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/**
 * Builds vendor payout summaries (PRD §10 Payout Reporting, §14 Weekly Payout Report).
 * Payouts themselves are manual in the MVP; this produces the reconciliation ledger.
 */
@Service
public class PayoutService {

    private static final Logger log = LoggerFactory.getLogger(PayoutService.class);

    private final OrderRepository orders;
    private final PayoutRepository payouts;
    private final VendorRepository vendors;
    private final Mapper mapper;

    public PayoutService(OrderRepository orders, PayoutRepository payouts, VendorRepository vendors, Mapper mapper) {
        this.orders = orders;
        this.payouts = payouts;
        this.vendors = vendors;
        this.mapper = mapper;
    }

    @Transactional
    public PayoutResponse generate(UUID vendorId, LocalDate periodStart, LocalDate periodEnd) {
        Instant start = periodStart.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant end = periodEnd.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();

        List<Order> periodOrders = orders.findByVendorIdAndCreatedAtBetween(vendorId, start, end);

        BigDecimal gross = BigDecimal.ZERO;
        BigDecimal platformFees = BigDecimal.ZERO;
        BigDecimal logisticsFees = BigDecimal.ZERO;
        BigDecimal refunds = BigDecimal.ZERO;
        BigDecimal vendorPayable = BigDecimal.ZERO;

        for (Order o : periodOrders) {
            if (o.getPaymentStatus() == PaymentStatus.PAID || o.getPaymentStatus() == PaymentStatus.PARTIAL) {
                gross = gross.add(o.getProductSubtotal());
                platformFees = platformFees.add(o.getPlatformRevenue());
                logisticsFees = logisticsFees.add(o.getLogisticsFee());
                vendorPayable = vendorPayable.add(o.getVendorPayable());
            }
            refunds = refunds.add(o.getRefundedAmount());
        }

        Payout payout = new Payout();
        payout.setVendorId(vendorId);
        payout.setPeriodStart(periodStart);
        payout.setPeriodEnd(periodEnd);
        payout.setGrossSales(gross);
        payout.setPlatformFees(platformFees);
        payout.setLogisticsFees(logisticsFees);
        payout.setRefunds(refunds);
        payout.setNetPayout(vendorPayable.subtract(refunds));
        payout.setPayoutStatus("PENDING");
        payouts.save(payout);
        return mapper.payout(payout);
    }

    @Transactional(readOnly = true)
    public List<PayoutResponse> forVendor(UUID vendorId) {
        return payouts.findByVendorIdOrderByPeriodEndDesc(vendorId).stream().map(mapper::payout).toList();
    }

    @Transactional
    public PayoutResponse markPaid(UUID payoutId) {
        Payout payout = payouts.findById(payoutId)
                .orElseThrow(() -> com.myorderlynk.app.web.error.ApiException.notFound("Payout not found"));
        payout.setPayoutStatus("PAID");
        payout.setPaidDate(Instant.now());
        return mapper.payout(payouts.save(payout));
    }

    /** Weekly backend job: generate last week's payout report for every vendor (PRD §14). */
    @Scheduled(cron = "${app.payout.weekly-cron:0 0 6 * * MON}")
    @Transactional
    public void generateWeeklyPayouts() {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate end = today.with(DayOfWeek.SUNDAY).minusWeeks(1);
        LocalDate start = end.minusDays(6);
        List<Vendor> all = vendors.findAll();
        for (Vendor vendor : all) {
            generate(vendor.getId(), start, end);
        }
        log.info("Generated weekly payout reports for {} vendors ({} to {})", all.size(), start, end);
    }
}
