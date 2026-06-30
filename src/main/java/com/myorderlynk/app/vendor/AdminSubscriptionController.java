package com.myorderlynk.app.vendor;

import com.myorderlynk.app.common.PageRequests;
import com.myorderlynk.app.common.PageResponse;
import com.myorderlynk.app.common.enums.VendorPlan;
import com.myorderlynk.app.security.access.IsAdmin;
import com.myorderlynk.app.vendor.SubscriptionDtos.GenerateResult;
import com.myorderlynk.app.vendor.SubscriptionDtos.InvoiceResponse;
import com.myorderlynk.app.vendor.SubscriptionDtos.PlanResponse;
import com.myorderlynk.app.vendor.SubscriptionDtos.UpdatePlanRequest;
import com.myorderlynk.app.vendor.VendorDtos.VendorResponse;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

/**
 * Admin console for vendor subscriptions: edit tier pricing, assign vendors to tiers, and manage the
 * monthly subscription invoices.
 */
@RestController
@RequestMapping("/api/admin/subscriptions")
@IsAdmin
public class AdminSubscriptionController {

    private final SubscriptionPlanService planService;
    private final SubscriptionBillingService billing;
    private final VendorSubscriptionInvoiceRepository invoices;

    public AdminSubscriptionController(SubscriptionPlanService planService, SubscriptionBillingService billing,
                                       VendorSubscriptionInvoiceRepository invoices) {
        this.planService = planService;
        this.billing = billing;
        this.invoices = invoices;
    }

    // ---- Plan catalog (tier pricing) ----

    @GetMapping("/plans")
    public List<PlanResponse> plans() {
        return planService.all().stream().map(SubscriptionDtos::toResponse).toList();
    }

    @PutMapping("/plans/{plan}")
    public PlanResponse updatePlan(@PathVariable VendorPlan plan, @Valid @RequestBody UpdatePlanRequest req) {
        return SubscriptionDtos.toResponse(planService.update(
                plan, req.displayName(), req.monthlyFee(), req.commissionRate(), req.currency()));
    }

    // ---- Vendor tier assignment ----

    /** Move a vendor onto a tier; materializes the tier's commission rate onto the vendor. */
    @PostMapping("/vendors/{vendorId}/plan")
    public VendorResponse assignPlan(@PathVariable UUID vendorId, @RequestParam VendorPlan plan) {
        return billing.assignPlan(vendorId, plan);
    }

    // ---- Invoices ----

    /** Manually trigger generation for a period (defaults to the current month). Idempotent. */
    @PostMapping("/invoices/generate")
    public GenerateResult generate(
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth period) {
        YearMonth target = period != null ? period : YearMonth.now();
        return new GenerateResult(target.toString(), billing.generateMonthlyInvoices(target));
    }

    @GetMapping("/invoices")
    public PageResponse<InvoiceResponse> list(@RequestParam(required = false) SubscriptionInvoiceStatus status,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "20") int size) {
        var pageable = PageRequests.of(page, size);
        var result = status == null
                ? invoices.findAllByOrderByCreatedAtDesc(pageable)
                : invoices.findByStatus(status, pageable);
        return PageResponse.of(result.map(SubscriptionDtos::toResponse));
    }

    @GetMapping("/invoices/vendor/{vendorId}")
    public List<InvoiceResponse> vendorInvoices(@PathVariable UUID vendorId) {
        return billing.forVendor(vendorId).stream().map(SubscriptionDtos::toResponse).toList();
    }

    @PostMapping("/invoices/{id}/mark-paid")
    public InvoiceResponse markPaid(@PathVariable UUID id, @RequestParam(required = false) String reference) {
        return SubscriptionDtos.toResponse(billing.markPaid(id, reference));
    }

    @PostMapping("/invoices/{id}/waive")
    public InvoiceResponse waive(@PathVariable UUID id) {
        return SubscriptionDtos.toResponse(billing.waive(id));
    }
}
