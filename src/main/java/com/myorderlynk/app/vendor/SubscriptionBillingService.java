package com.myorderlynk.app.vendor;

import com.myorderlynk.app.common.enums.VendorPlan;
import com.myorderlynk.app.common.enums.VendorStatus;
import com.myorderlynk.app.exception.ApiException;
import com.myorderlynk.app.vendor.VendorDtos.VendorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

/**
 * Drives vendor subscriptions: assigning a plan (which materializes the catalog commission rate onto
 * the vendor) and generating/settling the monthly subscription invoice. Generation is idempotent per
 * (vendor, month). Actual collection is left as an integration point — invoices are created {@code DUE}
 * and settled by {@link #markPaid} (admin/manual) or a future Stripe charge that calls the same path.
 */
@Service
public class SubscriptionBillingService {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionBillingService.class);

    private final VendorRepository vendors;
    private final SubscriptionPlanRepository plans;
    private final SubscriptionPlanService planService;
    private final VendorSubscriptionInvoiceRepository invoices;
    private final VendorMapper vendorMapper;
    private final com.myorderlynk.app.payment.PaymentClient paymentClient;

    public SubscriptionBillingService(VendorRepository vendors, SubscriptionPlanRepository plans,
                                      SubscriptionPlanService planService,
                                      VendorSubscriptionInvoiceRepository invoices, VendorMapper vendorMapper,
                                      com.myorderlynk.app.payment.PaymentClient paymentClient) {
        this.vendors = vendors;
        this.plans = plans;
        this.planService = planService;
        this.invoices = invoices;
        this.vendorMapper = vendorMapper;
        this.paymentClient = paymentClient;
    }

    /**
     * Move a vendor onto a plan and materialize that plan's commission rate onto the vendor (the
     * effective rate used in all fee math). Does not back-bill; the new monthly fee applies from the
     * next generation run.
     */
    @Transactional
    public VendorResponse assignPlan(UUID vendorId, VendorPlan plan) {
        Vendor vendor = vendors.findById(vendorId)
                .orElseThrow(() -> ApiException.notFound("Vendor not found"));
        SubscriptionPlan catalog = planService.byPlan(plan);
        vendor.setPlan(plan);
        vendor.setCommissionRate(catalog.getCommissionRate());
        vendors.save(vendor);
        log.info("Vendor {} assigned plan {} (commission -> {}, fee {} {}/mo)", vendorId, plan,
                catalog.getCommissionRate(), catalog.getMonthlyFee(), catalog.getCurrency());
        return vendorMapper.vendor(vendor);
    }

    /**
     * Generate this period's subscription invoices for every vendor on a paid plan (monthlyFee &gt; 0),
     * skipping suspended vendors and any (vendor, period) already invoiced. Returns the number created.
     */
    @Transactional
    public int generateMonthlyInvoices(YearMonth period) {
        LocalDate periodStart = period.atDay(1);
        LocalDate periodEnd = period.atEndOfMonth();

        List<VendorPlan> paidPlans = plans.findAll().stream()
                .filter(p -> p.getMonthlyFee() != null && p.getMonthlyFee().signum() > 0)
                .map(SubscriptionPlan::getPlan)
                .toList();
        if (paidPlans.isEmpty()) {
            return 0;
        }

        int generated = 0;
        for (Vendor vendor : vendors.findByPlanIn(paidPlans)) {
            if (vendor.getVerificationStatus() == VendorStatus.SUSPENDED) {
                continue;
            }
            if (invoices.existsByVendorIdAndPeriodStart(vendor.getId(), periodStart)) {
                continue;
            }
            SubscriptionPlan catalog = plans.findByPlan(vendor.getPlan()).orElse(null);
            if (catalog == null || catalog.getMonthlyFee().signum() <= 0) {
                continue;
            }
            invoices.save(new VendorSubscriptionInvoice(vendor.getId(), vendor.getPlan(),
                    periodStart, periodEnd, catalog.getMonthlyFee(), catalog.getCurrency()));
            generated++;
        }
        log.info("Subscription billing for {}: generated {} invoice(s)", period, generated);
        return generated;
    }

    /** Ids of every invoice still awaiting collection — drives the collection pass. */
    @Transactional(readOnly = true)
    public List<UUID> dueInvoiceIds() {
        return invoices.findByStatusOrderByCreatedAtAsc(SubscriptionInvoiceStatus.DUE).stream()
                .map(VendorSubscriptionInvoice::getId)
                .toList();
    }

    /**
     * Attempt to auto-collect one DUE invoice by netting it out of the vendor's balance in the
     * payment-service. On success the invoice is marked PAID with the settlement reference; on failure
     * (insufficient balance or the service being unreachable) it stays DUE for the next pass. Returns
     * whether it was collected.
     */
    @Transactional
    public boolean collectInvoice(UUID invoiceId) {
        VendorSubscriptionInvoice inv = invoices.findById(invoiceId).orElse(null);
        if (inv == null || inv.getStatus() != SubscriptionInvoiceStatus.DUE) {
            return false;
        }
        String reference = paymentClient.chargeVendor(inv.getVendorId(), inv.getAmount(), inv.getCurrency(),
                "SUBSCRIPTION", "SUBINV-" + inv.getId());
        if (reference == null) {
            return false;
        }
        inv.setStatus(SubscriptionInvoiceStatus.PAID);
        inv.setPaidAt(Instant.now());
        inv.setReference(reference);
        invoices.save(inv);
        log.info("Subscription invoice {} auto-collected (vendor {}, ref {})", invoiceId, inv.getVendorId(), reference);
        return true;
    }

    @Transactional
    public VendorSubscriptionInvoice markPaid(UUID invoiceId, String reference) {
        VendorSubscriptionInvoice inv = require(invoiceId);
        inv.setStatus(SubscriptionInvoiceStatus.PAID);
        inv.setPaidAt(Instant.now());
        if (reference != null && !reference.isBlank()) {
            inv.setReference(reference);
        }
        log.info("Subscription invoice {} marked PAID (vendor {})", invoiceId, inv.getVendorId());
        return invoices.save(inv);
    }

    @Transactional
    public VendorSubscriptionInvoice waive(UUID invoiceId) {
        VendorSubscriptionInvoice inv = require(invoiceId);
        inv.setStatus(SubscriptionInvoiceStatus.WAIVED);
        log.info("Subscription invoice {} WAIVED (vendor {})", invoiceId, inv.getVendorId());
        return invoices.save(inv);
    }

    @Transactional(readOnly = true)
    public List<VendorSubscriptionInvoice> forVendor(UUID vendorId) {
        return invoices.findByVendorIdOrderByPeriodStartDesc(vendorId);
    }

    private VendorSubscriptionInvoice require(UUID invoiceId) {
        return invoices.findById(invoiceId)
                .orElseThrow(() -> ApiException.notFound("Subscription invoice not found"));
    }
}
