package com.myorderlynk.app.web;

import com.myorderlynk.app.domain.NotificationLog;
import com.myorderlynk.app.domain.StatusChangeLog;
import com.myorderlynk.app.domain.enums.VendorStatus;
import com.myorderlynk.app.dto.OrderDtos.OrderResponse;
import com.myorderlynk.app.dto.OrderDtos.PaymentUpdateRequest;
import com.myorderlynk.app.dto.PayoutDtos.GeneratePayoutRequest;
import com.myorderlynk.app.dto.PayoutDtos.PayoutResponse;
import com.myorderlynk.app.dto.VendorDtos.VendorResponse;
import com.myorderlynk.app.security.CurrentUser;
import com.myorderlynk.app.service.AdminService;
import com.myorderlynk.app.service.OrderService;
import com.myorderlynk.app.service.PayoutService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final AdminService adminService;
    private final OrderService orderService;
    private final PayoutService payoutService;
    private final CurrentUser currentUser;

    public AdminController(AdminService adminService, OrderService orderService, PayoutService payoutService,
                           CurrentUser currentUser) {
        this.adminService = adminService;
        this.orderService = orderService;
        this.payoutService = payoutService;
        this.currentUser = currentUser;
    }

    // ---- Vendor approval (PRD §14) ----

    @GetMapping("/vendors")
    public List<VendorResponse> vendors(@RequestParam(required = false) VendorStatus status) {
        return adminService.listVendors(status);
    }

    @PostMapping("/vendors/{id}/approve")
    public VendorResponse approve(@PathVariable UUID id) {
        return adminService.approveVendor(id);
    }

    @PostMapping("/vendors/{id}/reject")
    public VendorResponse reject(@PathVariable UUID id) {
        return adminService.rejectVendor(id);
    }

    @PostMapping("/vendors/{id}/suspend")
    public VendorResponse suspend(@PathVariable UUID id) {
        return adminService.suspendVendor(id);
    }

    // ---- Order oversight ----

    @GetMapping("/orders")
    public List<OrderResponse> orders() {
        return adminService.allOrders();
    }

    @GetMapping("/orders/{id}")
    public OrderResponse order(@PathVariable UUID id) {
        return adminService.getOrder(id);
    }

    @GetMapping("/orders/{id}/history")
    public List<StatusChangeLog> orderHistory(@PathVariable UUID id) {
        return adminService.orderHistory(id);
    }

    @GetMapping("/orders/{id}/notifications")
    public List<NotificationLog> orderNotifications(@PathVariable UUID id) {
        return adminService.orderNotifications(id);
    }

    @PatchMapping("/orders/{id}/payment")
    public OrderResponse updatePayment(@PathVariable UUID id, @Valid @RequestBody PaymentUpdateRequest req) {
        return orderService.updatePayment(id, req, "admin:" + currentUser.require().userId(), null);
    }

    // ---- Payout reporting ----

    @PostMapping("/payouts/generate")
    public PayoutResponse generatePayout(@Valid @RequestBody GeneratePayoutRequest req) {
        return payoutService.generate(req.vendorId(), req.periodStart(), req.periodEnd());
    }

    @GetMapping("/payouts/vendor/{vendorId}")
    public List<PayoutResponse> vendorPayouts(@PathVariable UUID vendorId) {
        return payoutService.forVendor(vendorId);
    }

    @PatchMapping("/payouts/{id}/mark-paid")
    public PayoutResponse markPaid(@PathVariable UUID id) {
        return payoutService.markPaid(id);
    }
}
