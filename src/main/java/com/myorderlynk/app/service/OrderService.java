package com.myorderlynk.app.service;

import com.myorderlynk.app.domain.Order;
import com.myorderlynk.app.domain.OrderItem;
import com.myorderlynk.app.domain.PaymentRecord;
import com.myorderlynk.app.domain.Product;
import com.myorderlynk.app.domain.Vendor;
import com.myorderlynk.app.domain.enums.FulfillmentStatus;
import com.myorderlynk.app.domain.enums.PaymentMethod;
import com.myorderlynk.app.domain.enums.PaymentStatus;
import com.myorderlynk.app.domain.enums.SourceChannel;
import com.myorderlynk.app.domain.enums.VendorStatus;
import com.myorderlynk.app.dto.Mapper;
import com.myorderlynk.app.dto.OrderDtos.CartLine;
import com.myorderlynk.app.dto.OrderDtos.CheckoutRequest;
import com.myorderlynk.app.dto.OrderDtos.FulfillmentUpdateRequest;
import com.myorderlynk.app.dto.OrderDtos.OrderResponse;
import com.myorderlynk.app.dto.OrderDtos.PaymentUpdateRequest;
import com.myorderlynk.app.dto.OrderDtos.QuoteRequest;
import com.myorderlynk.app.dto.OrderDtos.QuoteResponse;
import com.myorderlynk.app.repository.OrderRepository;
import com.myorderlynk.app.repository.PaymentRecordRepository;
import com.myorderlynk.app.repository.ProductRepository;
import com.myorderlynk.app.repository.VendorRepository;
import com.myorderlynk.app.exception.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class OrderService {

    private final OrderRepository orders;
    private final ProductRepository products;
    private final VendorRepository vendors;
    private final PaymentRecordRepository payments;
    private final FeeCalculator feeCalculator;
    private final NotificationService notifications;
    private final AuditService audit;
    private final Mapper mapper;

    public OrderService(OrderRepository orders, ProductRepository products, VendorRepository vendors,
                        PaymentRecordRepository payments, FeeCalculator feeCalculator,
                        NotificationService notifications, AuditService audit, Mapper mapper) {
        this.orders = orders;
        this.products = products;
        this.vendors = vendors;
        this.payments = payments;
        this.feeCalculator = feeCalculator;
        this.notifications = notifications;
        this.audit = audit;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public QuoteResponse quote(QuoteRequest req) {
        Vendor vendor = approvedVendor(req.vendorId());
        BigDecimal subtotal = subtotalOf(req.items(), vendor.getId());
        FeeCalculator.FeeBreakdown fb = feeCalculator.calculate(
                subtotal, req.fulfillmentType(), req.paymentMethod(), vendor.getCommissionRate());
        return new QuoteResponse(fb.productSubtotal(), fb.logisticsFee(), fb.platformFee(),
                fb.processingFee(), fb.totalAmount(), "CAD");
    }

    @Transactional
    public OrderResponse checkout(CheckoutRequest req, UUID customerUserId) {
        Vendor vendor = approvedVendor(req.vendorId());

        Order order = new Order();
        order.setVendorId(vendor.getId());
        order.setCustomerUserId(customerUserId);
        order.setCustomerName(req.customerName());
        order.setCustomerPhone(req.customerPhone());
        order.setCustomerEmail(req.customerEmail());
        order.setCustomerCity(req.customerCity());
        order.setFulfillmentType(req.fulfillmentType());
        order.setSourceChannel(req.sourceChannel() == null ? SourceChannel.VENDOR_LINK : req.sourceChannel());
        order.setCampaign(req.campaign());
        order.setNotes(req.notes());
        order.setPaymentStatus(PaymentStatus.PENDING);
        order.setFulfillmentStatus(FulfillmentStatus.ORDER_RECEIVED);

        BigDecimal subtotal = BigDecimal.ZERO;
        for (CartLine line : req.items()) {
            Product product = products.findById(line.productId())
                    .orElseThrow(() -> ApiException.badRequest("Product " + line.productId() + " not found"));
            if (!product.getVendorId().equals(vendor.getId()) || !product.isActive()) {
                throw ApiException.badRequest("Product '" + product.getName() + "' is not available from this vendor");
            }
            if (product.getQuantityAvailable() < line.quantity()) {
                throw ApiException.badRequest("Insufficient stock for '" + product.getName() + "'");
            }
            BigDecimal lineTotal = product.getPrice().multiply(BigDecimal.valueOf(line.quantity()));
            OrderItem item = new OrderItem();
            item.setProductId(product.getId());
            item.setVendorId(vendor.getId());
            item.setProductNameSnapshot(product.getName());
            item.setQuantity(line.quantity());
            item.setUnitPrice(product.getPrice());
            item.setLineTotal(lineTotal);
            order.addItem(item);
            subtotal = subtotal.add(lineTotal);

            product.setQuantityAvailable(product.getQuantityAvailable() - line.quantity());
            products.save(product);
        }

        FeeCalculator.FeeBreakdown fb = feeCalculator.calculate(
                subtotal, req.fulfillmentType(), req.paymentMethod(), vendor.getCommissionRate());
        order.setProductSubtotal(fb.productSubtotal());
        order.setLogisticsFee(fb.logisticsFee());
        order.setPlatformFee(fb.platformFee());
        order.setProcessingFee(fb.processingFee());
        order.setTotalAmount(fb.totalAmount());
        order.setVendorPayable(fb.vendorPayable());
        order.setLogisticsPayable(fb.logisticsPayable());
        order.setPlatformRevenue(fb.platformRevenue());
        order.setPublicOrderId(generateOrderId());

        orders.save(order);

        audit.logChange(order.getId(), "FULFILLMENT", null, FulfillmentStatus.ORDER_RECEIVED.name(), "SYSTEM",
                "Order created");
        notifications.notifyOrder(order, "EMAIL", "ORDER_RECEIVED", order.getCustomerEmail(),
                "Your order " + order.getPublicOrderId() + " has been received by " + vendor.getBusinessName() + ".");
        notifications.notifyOrder(order, "DASHBOARD", "NEW_ORDER_ALERT", vendor.getBusinessName(),
                "New order " + order.getPublicOrderId() + " for " + order.getTotalAmount() + " " + order.getCurrency() + ".");

        return mapper.order(order, vendor.getBusinessName());
    }

    /** Customer self-service tracking: order id must match a phone or email on the order. */
    @Transactional(readOnly = true)
    public OrderResponse track(String publicOrderId, String contact) {
        Order order = orders.findByPublicOrderId(publicOrderId.trim())
                .orElseThrow(() -> ApiException.notFound("Order not found"));
        String needle = contact == null ? "" : contact.trim().toLowerCase();
        boolean matches = needle.equalsIgnoreCase(order.getCustomerPhone())
                || (order.getCustomerEmail() != null && needle.equalsIgnoreCase(order.getCustomerEmail()));
        if (!matches) {
            throw ApiException.notFound("Order not found");
        }
        return mapper.order(order, vendorName(order.getVendorId()));
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> vendorOrders(UUID vendorId) {
        String name = vendorName(vendorId);
        return orders.findByVendorIdOrderByCreatedAtDesc(vendorId).stream()
                .map(o -> mapper.order(o, name)).toList();
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> customerOrders(UUID customerUserId) {
        return orders.findByCustomerUserIdOrderByCreatedAtDesc(customerUserId).stream()
                .map(o -> mapper.order(o, vendorName(o.getVendorId()))).toList();
    }

    @Transactional(readOnly = true)
    public OrderResponse getForVendor(UUID vendorId, UUID orderId) {
        Order order = vendorOwnedOrder(vendorId, orderId);
        return mapper.order(order, vendorName(vendorId));
    }

    @Transactional
    public OrderResponse updateFulfillment(UUID vendorId, UUID orderId, FulfillmentUpdateRequest req, String actor) {
        Order order = vendorOwnedOrder(vendorId, orderId);
        FulfillmentStatus from = order.getFulfillmentStatus();
        FulfillmentStatus to = req.status();
        if (from == to) {
            return mapper.order(order, vendorName(vendorId));
        }
        if (!FulfillmentFlows.isValidTransition(order.getFulfillmentType(), from, to)) {
            throw ApiException.badRequest("Cannot move order from " + from + " to " + to
                    + " for " + order.getFulfillmentType() + " fulfillment");
        }
        order.setFulfillmentStatus(to);
        if (to == FulfillmentStatus.READY_FOR_PICKUP && order.getPickupCode() == null) {
            order.setPickupCode(com.myorderlynk.app.service.util.CodeGenerator.pickupCode());
        }
        orders.save(order);

        audit.logChange(orderId, "FULFILLMENT", from.name(), to.name(), actor, req.note());
        notifyFulfillment(order, to);
        return mapper.order(order, vendorName(vendorId));
    }

    @Transactional
    public OrderResponse updatePayment(UUID orderId, PaymentUpdateRequest req, String actor, UUID actingVendorId) {
        Order order = orders.findById(orderId).orElseThrow(() -> ApiException.notFound("Order not found"));
        if (actingVendorId != null && !order.getVendorId().equals(actingVendorId)) {
            throw ApiException.forbidden("This order belongs to another vendor");
        }
        PaymentStatus from = order.getPaymentStatus();
        PaymentStatus to = req.status();
        order.setPaymentStatus(to);

        BigDecimal amount = req.amount() != null ? req.amount() : order.getTotalAmount();
        if (to == PaymentStatus.PAID || to == PaymentStatus.PARTIAL) {
            PaymentRecord record = new PaymentRecord();
            record.setOrderId(order.getId());
            record.setCustomerUserId(order.getCustomerUserId());
            record.setVendorId(order.getVendorId());
            record.setAmountPaid(amount);
            record.setPaymentMethod(req.method() == null ? PaymentMethod.OTHER : req.method());
            record.setPaymentStatus(to);
            record.setTransactionReference(req.transactionReference());
            record.setPaidDate(Instant.now());
            payments.save(record);

            // Advance fulfillment to PAID if still at the start of the flow.
            if (to == PaymentStatus.PAID && order.getFulfillmentStatus() == FulfillmentStatus.ORDER_RECEIVED) {
                order.setFulfillmentStatus(FulfillmentStatus.PAID);
                audit.logChange(orderId, "FULFILLMENT", FulfillmentStatus.ORDER_RECEIVED.name(),
                        FulfillmentStatus.PAID.name(), actor, "Auto-advanced on payment");
            }
        } else if (to == PaymentStatus.REFUNDED) {
            order.setRefundedAmount(amount);
        }
        orders.save(order);

        audit.logChange(orderId, "PAYMENT", from.name(), to.name(), actor, req.transactionReference());
        if (to == PaymentStatus.PAID) {
            notifications.notifyOrder(order, "EMAIL", "PAYMENT_CONFIRMED", order.getCustomerEmail(),
                    "Payment confirmed for order " + order.getPublicOrderId() + ".");
        }
        return mapper.order(order, vendorName(order.getVendorId()));
    }

    // ---- helpers ----

    private void notifyFulfillment(Order order, FulfillmentStatus to) {
        switch (to) {
            case READY_FOR_PICKUP -> notifications.notifyOrder(order, "EMAIL", "READY_FOR_PICKUP",
                    order.getCustomerEmail(), "Order " + order.getPublicOrderId()
                            + " is ready for pickup. Pickup code: " + order.getPickupCode());
            case SHIPPED -> notifications.notifyOrder(order, "EMAIL", "ORDER_SHIPPED",
                    order.getCustomerEmail(), "Order " + order.getPublicOrderId() + " has shipped.");
            case OUT_FOR_DELIVERY -> notifications.notifyOrder(order, "EMAIL", "OUT_FOR_DELIVERY",
                    order.getCustomerEmail(), "Order " + order.getPublicOrderId() + " is out for delivery.");
            case DELIVERED, COMPLETED -> notifications.notifyOrder(order, "EMAIL", "ORDER_COMPLETED",
                    order.getCustomerEmail(), "Order " + order.getPublicOrderId() + " is " + to.name().toLowerCase() + ".");
            default -> notifications.notifyOrder(order, "EMAIL", "STATUS_UPDATE",
                    order.getCustomerEmail(), "Order " + order.getPublicOrderId() + " status: " + to.name() + ".");
        }
    }

    private Vendor approvedVendor(UUID vendorId) {
        Vendor vendor = vendors.findById(vendorId).orElseThrow(() -> ApiException.notFound("Vendor not found"));
        if (!vendor.isActive() || vendor.getVerificationStatus() != VendorStatus.APPROVED) {
            throw ApiException.badRequest("This vendor is not currently accepting orders");
        }
        return vendor;
    }

    private BigDecimal subtotalOf(List<CartLine> lines, UUID vendorId) {
        BigDecimal subtotal = BigDecimal.ZERO;
        for (CartLine line : lines) {
            Product product = products.findById(line.productId())
                    .orElseThrow(() -> ApiException.badRequest("Product " + line.productId() + " not found"));
            if (!product.getVendorId().equals(vendorId) || !product.isActive()) {
                throw ApiException.badRequest("Product '" + product.getName() + "' is not available from this vendor");
            }
            subtotal = subtotal.add(product.getPrice().multiply(BigDecimal.valueOf(line.quantity())));
        }
        return subtotal;
    }

    private Order vendorOwnedOrder(UUID vendorId, UUID orderId) {
        Order order = orders.findById(orderId).orElseThrow(() -> ApiException.notFound("Order not found"));
        if (!order.getVendorId().equals(vendorId)) {
            throw ApiException.forbidden("This order belongs to another vendor");
        }
        return order;
    }

    private String vendorName(UUID vendorId) {
        return vendors.findById(vendorId).map(Vendor::getBusinessName).orElse("Vendor");
    }

    private String generateOrderId() {
        String id = com.myorderlynk.app.service.util.CodeGenerator.orderId();
        while (orders.existsByPublicOrderId(id)) {
            id = com.myorderlynk.app.service.util.CodeGenerator.orderId();
        }
        return id;
    }
}
