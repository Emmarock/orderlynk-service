package com.myorderlynk.app.finance;
import com.myorderlynk.app.vendor.VendorAnalyticsService;

import com.myorderlynk.app.order.Order;
import com.myorderlynk.app.finance.Payout;
import com.myorderlynk.app.identity.User;
import com.myorderlynk.app.vendor.Vendor;
import com.myorderlynk.app.common.enums.PaymentStatus;
import com.myorderlynk.app.vendor.AnalyticsDtos.VendorAnalytics;
import com.myorderlynk.app.finance.PayoutDtos.PayoutResponse;
import com.myorderlynk.app.exception.ApiException;
import com.myorderlynk.app.order.FeeSettingsService;
import com.myorderlynk.app.order.OrderRepository;
import com.myorderlynk.app.finance.PayoutRepository;
import com.myorderlynk.app.identity.UserRepository;
import com.myorderlynk.app.vendor.VendorRepository;
import com.myorderlynk.app.notification.EmailService;
import com.myorderlynk.app.common.PageResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
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
    private final UserRepository users;
    private final PayoutMapper mapper;
    private final EmailService emailService;
    private final VendorAnalyticsService analyticsService;
    private final FeeSettingsService feeSettings;
    private final com.myorderlynk.app.payment.PaymentClient paymentClient;

    public PayoutService(OrderRepository orders, PayoutRepository payouts, VendorRepository vendors,
                         UserRepository users, PayoutMapper mapper, EmailService emailService,
                         VendorAnalyticsService analyticsService, FeeSettingsService feeSettings,
                         com.myorderlynk.app.payment.PaymentClient paymentClient) {
        this.orders = orders;
        this.payouts = payouts;
        this.vendors = vendors;
        this.users = users;
        this.mapper = mapper;
        this.emailService = emailService;
        this.analyticsService = analyticsService;
        this.feeSettings = feeSettings;
        this.paymentClient = paymentClient;
    }

    /**
     * Instantly pay out {@code amount} of the vendor's own connected-account balance to their bank, for
     * a platform fee (from fee settings) charged to their card on file. The actual money movement and
     * fee collection happen in the payment-service (Stripe instant payout + card charge); here we
     * compute the fee, drive that flow, and record a reporting payout row for the vendor's history.
     * The vendor's bank receives the full {@code amount}; the fee is a separate card charge.
     */
    @Transactional
    public PayoutResponse requestInstantPayout(UUID vendorId, BigDecimal amount, String currency) {
        BigDecimal amt = amount == null ? BigDecimal.ZERO : amount;
        if (amt.signum() <= 0) {
            throw ApiException.badRequest("Instant payout amount must be positive");
        }
        String cur = (currency == null || currency.isBlank()) ? "CAD" : currency;
        BigDecimal fee = feeSettings.current().instantPayoutFeeFor(amt);
        String reference = "INSTPAY-" + vendorId + "-" + UUID.randomUUID().toString().substring(0, 8);

        com.myorderlynk.app.payment.PaymentDtos.InstantPayoutResult result;
        try {
            result = paymentClient.requestInstantPayout(vendorId, amt, cur, fee, reference);
        } catch (Exception e) {
            log.warn("Instant payout failed for vendor {}: {}", vendorId, e.getMessage());
            throw ApiException.badRequest("Instant payout could not be completed (a card on file and an "
                    + "active Stripe account are required)");
        }

        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        Payout payout = new Payout();
        payout.setVendorId(vendorId);
        payout.setPeriodStart(today);
        payout.setPeriodEnd(today);
        payout.setGrossSales(amt);
        payout.setNetPayout(amt); // the vendor's bank receives the full amount; the fee is charged to their card
        payout.setInstantPayout(true);
        payout.setInstantPayoutFee(fee);
        String status = result == null || result.status() == null ? "PROCESSING" : result.status().toUpperCase();
        payout.setPayoutStatus("INSTANT_" + status);
        payout.setPaidDate(Instant.now());
        log.info("Instant payout for vendor {} amount={} {} fee={} status={}", vendorId, amt, cur, fee, status);
        return mapper.payout(payouts.save(payout));
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
        log.info("Payout generated: vendor={} period={}..{} orders={} gross={} net={}",
                vendorId, periodStart, periodEnd, periodOrders.size(), gross, payout.getNetPayout());
        return mapper.payout(payout);
    }

    @Transactional(readOnly = true)
    public List<PayoutResponse> forVendor(UUID vendorId) {
        return payouts.findByVendorIdOrderByPeriodEndDesc(vendorId).stream().map(mapper::payout).toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<PayoutResponse> forVendorPaged(UUID vendorId, Pageable pageable) {
        return PageResponse.of(payouts.findByVendorIdOrderByPeriodEndDesc(vendorId, pageable).map(mapper::payout));
    }

    @Transactional
    public PayoutResponse markPaid(UUID payoutId) {
        Payout payout = payouts.findById(payoutId)
                .orElseThrow(() -> ApiException.notFound("Payout not found"));
        payout.setPayoutStatus("PAID");
        payout.setPaidDate(Instant.now());
        log.info("Payout {} marked PAID (vendor={} net={})", payoutId, payout.getVendorId(), payout.getNetPayout());
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
            PayoutResponse payout = generate(vendor.getId(), start, end);
            sendWeeklySummary(vendor, start, end, payout);
        }
        log.info("Generated weekly payout reports for {} vendors ({} to {})", all.size(), start, end);
    }

    /** Emails the vendor's weekly sales summary, if they have an owner email and opted into email. */
    private void sendWeeklySummary(Vendor vendor, LocalDate start, LocalDate end, PayoutResponse payout) {
        if (!vendor.isNotifyByEmail() || vendor.getOwnerUserId() == null) {
            return;
        }
        String email = users.findById(vendor.getOwnerUserId()).map(User::getEmail).orElse(null);
        if (email == null) {
            return;
        }
        Instant from = start.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant to = end.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        VendorAnalytics analytics = analyticsService.analytics(vendor.getId(), from, to);
        emailService.sendWeeklySalesSummary(email, vendor.getBusinessName(), start, end,
                analytics.totalOrders(), payout.grossSales(), payout.netPayout(), analytics.topProducts());
    }
}
