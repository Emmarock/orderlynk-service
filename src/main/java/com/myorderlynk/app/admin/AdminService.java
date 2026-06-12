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
import com.myorderlynk.app.order.OrderDtos.OrderResponse;
import com.myorderlynk.app.vendor.VendorDtos.VendorResponse;
import com.myorderlynk.app.identity.User;
import com.myorderlynk.app.order.OrderRepository;
import com.myorderlynk.app.identity.UserRepository;
import com.myorderlynk.app.vendor.VendorRepository;
import com.myorderlynk.app.exception.ApiException;
import com.myorderlynk.app.notification.EmailService;
import lombok.extern.slf4j.Slf4j;
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

    @Transactional(readOnly = true)
    public List<VendorResponse> listVendors(VendorStatus status) {
        List<Vendor> list = status == null ? vendors.findAll() : vendors.findByVerificationStatus(status);
        return list.stream().map(vendorMapper::vendor).toList();
    }

    @Transactional
    public VendorResponse approveVendor(UUID vendorId) {
        Vendor vendor = require(vendorId);
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

    @Transactional(readOnly = true)
    public List<OrderResponse> allOrders() {
        return orders.findAllByOrderByCreatedAtDesc().stream()
                .map(o -> orderMapper.order(o, vendorName(o.getVendorId()))).toList();
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
