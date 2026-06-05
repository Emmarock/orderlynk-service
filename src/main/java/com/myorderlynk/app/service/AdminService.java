package com.myorderlynk.app.service;

import com.myorderlynk.app.domain.NotificationLog;
import com.myorderlynk.app.domain.Order;
import com.myorderlynk.app.domain.StatusChangeLog;
import com.myorderlynk.app.domain.Vendor;
import com.myorderlynk.app.domain.enums.VendorStatus;
import com.myorderlynk.app.dto.Mapper;
import com.myorderlynk.app.dto.OrderDtos.OrderResponse;
import com.myorderlynk.app.dto.VendorDtos.VendorResponse;
import com.myorderlynk.app.repo.OrderRepository;
import com.myorderlynk.app.repo.VendorRepository;
import com.myorderlynk.app.web.error.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class AdminService {

    private final VendorRepository vendors;
    private final OrderRepository orders;
    private final NotificationService notifications;
    private final AuditService audit;
    private final Mapper mapper;

    public AdminService(VendorRepository vendors, OrderRepository orders, NotificationService notifications,
                        AuditService audit, Mapper mapper) {
        this.vendors = vendors;
        this.orders = orders;
        this.notifications = notifications;
        this.audit = audit;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public List<VendorResponse> listVendors(VendorStatus status) {
        List<Vendor> list = status == null ? vendors.findAll() : vendors.findByVerificationStatus(status);
        return list.stream().map(mapper::vendor).toList();
    }

    @Transactional
    public VendorResponse approveVendor(UUID vendorId) {
        Vendor vendor = require(vendorId);
        vendor.setVerificationStatus(VendorStatus.APPROVED);
        vendor.setActive(true);
        vendors.save(vendor);
        return mapper.vendor(vendor);
    }

    @Transactional
    public VendorResponse rejectVendor(UUID vendorId) {
        Vendor vendor = require(vendorId);
        vendor.setVerificationStatus(VendorStatus.REJECTED);
        vendor.setActive(false);
        vendors.save(vendor);
        return mapper.vendor(vendor);
    }

    @Transactional
    public VendorResponse suspendVendor(UUID vendorId) {
        Vendor vendor = require(vendorId);
        vendor.setVerificationStatus(VendorStatus.SUSPENDED);
        vendor.setActive(false);
        vendors.save(vendor);
        return mapper.vendor(vendor);
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> allOrders() {
        return orders.findAllByOrderByCreatedAtDesc().stream()
                .map(o -> mapper.order(o, vendorName(o.getVendorId()))).toList();
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrder(UUID orderId) {
        Order order = orders.findById(orderId).orElseThrow(() -> ApiException.notFound("Order not found"));
        return mapper.order(order, vendorName(order.getVendorId()));
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
}
