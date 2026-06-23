package com.myorderlynk.app.admin;
import com.myorderlynk.app.notification.NotificationService;
import com.myorderlynk.app.common.AuditService;

import com.myorderlynk.app.notification.NotificationLog;
import com.myorderlynk.app.order.Order;
import com.myorderlynk.app.common.StatusChangeLog;
import com.myorderlynk.app.vendor.Vendor;
import com.myorderlynk.app.common.enums.VendorStatus;
import com.myorderlynk.app.order.OrderMapper;
import com.myorderlynk.app.vendor.VendorMapper;
import com.myorderlynk.app.admin.AdminDtos.AdminSummary;
import com.myorderlynk.app.common.enums.PaymentStatus;
import com.myorderlynk.app.order.OrderDtos.OrderResponse;
import com.myorderlynk.app.vendor.VendorDtos.VendorResponse;
import com.myorderlynk.app.identity.User;
import com.myorderlynk.app.order.OrderRepository;
import com.myorderlynk.app.identity.UserRepository;
import com.myorderlynk.app.vendor.VendorRepository;
import com.myorderlynk.app.exception.ApiException;
import com.myorderlynk.app.notification.EmailService;
import com.myorderlynk.app.common.PageResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Slf4j
public class AdminService {

    private final VendorRepository vendors;
    private final OrderRepository orders;
    private final UserRepository users;
    private final NotificationService notifications;
    private final AuditService audit;
    private final VendorMapper vendorMapper;
    private final OrderMapper orderMapper;
    private final EmailService emailService;

    public AdminService(VendorRepository vendors, OrderRepository orders, UserRepository users,
                        NotificationService notifications, AuditService audit, VendorMapper vendorMapper, OrderMapper orderMapper,
                        EmailService emailService) {
        this.vendors = vendors;
        this.orders = orders;
        this.users = users;
        this.notifications = notifications;
        this.audit = audit;
        this.vendorMapper = vendorMapper;
        this.orderMapper = orderMapper;
        this.emailService = emailService;
    }

    private static final List<VendorStatus> PENDING_STATUSES =
            List.of(VendorStatus.SUBMITTED, VendorStatus.UNDER_REVIEW);

    /** Platform headline metrics plus a bounded preview of vendors awaiting approval. */
    @Transactional(readOnly = true)
    public AdminSummary summary() {
        long vendorCount = vendors.count();
        long activeVendorCount = vendors.countByActiveTrue();
        long pendingCount = vendors.countByVerificationStatusIn(PENDING_STATUSES);
        long orderCount = orders.count();
        long paidOrderCount = orders.countByPaymentStatus(PaymentStatus.PAID);
        var grossRevenue = orders.sumTotalAmountByPaymentStatus(PaymentStatus.PAID);
        var platformRevenue = orders.sumPlatformRevenueByPaymentStatus(PaymentStatus.PAID);
        List<VendorResponse> pendingVendors = vendors
                .findByVerificationStatusInOrderByCreatedAtDesc(PENDING_STATUSES, PageRequest.of(0, 10))
                .stream().map(vendorMapper::vendor).toList();
        return new AdminSummary(vendorCount, activeVendorCount, pendingCount, orderCount, paidOrderCount,
                grossRevenue, platformRevenue, pendingVendors);
    }

    @Transactional(readOnly = true)
    public PageResponse<VendorResponse> listVendors(VendorStatus status, Pageable pageable) {
        var page = status == null
                ? vendors.findAll(pageable)
                : vendors.findByVerificationStatus(status, pageable);
        return PageResponse.of(page.map(vendorMapper::vendor));
    }

    @Transactional
    public VendorResponse approveVendor(UUID vendorId) {
        Vendor vendor = require(vendorId);
        // A vendor must verify their email before they can be approved and go live.
        boolean ownerVerified = vendor.getOwnerUserId() != null
                && users.findById(vendor.getOwnerUserId()).map(User::isEmailVerified).orElse(false);
        if (!ownerVerified) {
            throw ApiException.badRequest("This vendor must verify their email address before you can approve them.");
        }
        VendorStatus from = vendor.getVerificationStatus();
        vendor.setVerificationStatus(VendorStatus.APPROVED);
        vendor.setActive(true);
        vendors.save(vendor);
        log.info("Vendor {} approved (was {}) — now active", vendorId, from);
        ownerEmail(vendor).ifPresent(email -> emailService.sendVendorApproved(email, vendor.getBusinessName()));
        return vendorMapper.vendor(vendor);
    }

    @Transactional
    public VendorResponse rejectVendor(UUID vendorId) {
        Vendor vendor = require(vendorId);
        VendorStatus from = vendor.getVerificationStatus();
        vendor.setVerificationStatus(VendorStatus.REJECTED);
        vendor.setActive(false);
        vendors.save(vendor);
        log.info("Vendor {} rejected (was {})", vendorId, from);
        return vendorMapper.vendor(vendor);
    }

    @Transactional
    public VendorResponse suspendVendor(UUID vendorId) {
        Vendor vendor = require(vendorId);
        VendorStatus from = vendor.getVerificationStatus();
        vendor.setVerificationStatus(VendorStatus.SUSPENDED);
        vendor.setActive(false);
        vendors.save(vendor);
        log.warn("Vendor {} suspended (was {}) — deactivated", vendorId, from);
        return vendorMapper.vendor(vendor);
    }

    /** Admin grants/revokes a vendor's ability to accept non-card payment methods (transfers, cash). */
    @Transactional
    public VendorResponse setAlternativePayments(UUID vendorId, boolean enabled) {
        Vendor vendor = require(vendorId);
        vendor.setAlternativePaymentsEnabled(enabled);
        vendors.save(vendor);
        log.info("Vendor {} alternative payments {}", vendorId, enabled ? "ENABLED" : "disabled");
        return vendorMapper.vendor(vendor);
    }

    /** Admin grants/revokes a vendor's ability to use chat-order import (paste a chat → draft order). */
    @Transactional
    public VendorResponse setChatOrders(UUID vendorId, boolean enabled) {
        Vendor vendor = require(vendorId);
        vendor.setChatOrderEnabled(enabled);
        vendors.save(vendor);
        log.info("Vendor {} chat orders {}", vendorId, enabled ? "ENABLED" : "disabled");
        return vendorMapper.vendor(vendor);
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> allOrders(Pageable pageable) {
        return PageResponse.of(orders.findAllByOrderByCreatedAtDesc(pageable)
                .map(o -> orderMapper.order(o, vendorName(o.getVendorId()))));
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrder(UUID orderId) {
        Order order = orders.findById(orderId).orElseThrow(() -> ApiException.notFound("Order not found"));
        return orderMapper.order(order, vendorName(order.getVendorId()));
    }

    @Transactional(readOnly = true)
    public List<StatusChangeLog> orderHistory(UUID orderId) {
        return audit.forOrder(orderId);
    }

    @Transactional(readOnly = true)
    public List<NotificationLog> orderNotifications(UUID orderId) {
        return notifications.forOrder(orderId);
    }

    private Vendor require(UUID vendorId) {
        return vendors.findById(vendorId).orElseThrow(() -> ApiException.notFound("Vendor not found"));
    }

    private String vendorName(UUID vendorId) {
        return vendors.findById(vendorId).map(Vendor::getBusinessName).orElse("Vendor");
    }

    private java.util.Optional<String> ownerEmail(Vendor vendor) {
        if (vendor.getOwnerUserId() == null) return java.util.Optional.empty();
        return users.findById(vendor.getOwnerUserId()).map(User::getEmail);
    }
}
