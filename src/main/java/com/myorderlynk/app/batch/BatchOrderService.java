package com.myorderlynk.app.batch;

import com.myorderlynk.app.batch.BatchDtos.PaymentInitResponse;
import com.myorderlynk.app.batch.BatchOrderDtos.BatchOrderRequest;
import com.myorderlynk.app.batch.BatchOrderDtos.BatchOrderResponse;
import com.myorderlynk.app.batch.BatchOrderDtos.CartLine;
import com.myorderlynk.app.common.Address;
import com.myorderlynk.app.common.AuditService;
import com.myorderlynk.app.common.CodeGenerator;
import com.myorderlynk.app.common.enums.PaymentStatus;
import com.myorderlynk.app.common.enums.SourceChannel;
import com.myorderlynk.app.common.enums.VendorStatus;
import com.myorderlynk.app.exception.ApiException;
import com.myorderlynk.app.payment.PaymentClient;
import com.myorderlynk.app.payment.PaymentDtos.CreatePaymentResponse;
import com.myorderlynk.app.payment.PaymentServiceProperties;
import com.myorderlynk.app.vendor.Vendor;
import com.myorderlynk.app.vendor.VendorRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Batch-product orders (spec §7.1, §8.2, §13.1): customer checkout into a batch with up-front Stripe
 * card payment, then the vendor advances the order through the shipment cycle. Payment is keyed by
 * the {@code BO-} public id so the shared payment-service webhook routes settlements back here.
 */
@Service
@Slf4j
public class BatchOrderService {

    private static final String AUDIT_TYPE = "BATCH_ORDER";

    private final BatchOrderRepository orders;
    private final BatchRepository batches;
    private final BatchProductRepository batchProducts;
    private final VendorRepository vendors;
    private final BatchMapper mapper;
    private final PaymentClient paymentClient;
    private final PaymentServiceProperties paymentProps;
    private final BatchNotificationService notifications;
    private final AuditService audit;

    public BatchOrderService(BatchOrderRepository orders, BatchRepository batches,
                             BatchProductRepository batchProducts, VendorRepository vendors, BatchMapper mapper,
                             PaymentClient paymentClient, PaymentServiceProperties paymentProps,
                             BatchNotificationService notifications, AuditService audit) {
        this.orders = orders;
        this.batches = batches;
        this.batchProducts = batchProducts;
        this.vendors = vendors;
        this.mapper = mapper;
        this.paymentClient = paymentClient;
        this.paymentProps = paymentProps;
        this.notifications = notifications;
        this.audit = audit;
    }

    @Transactional
    public BatchOrderResponse create(BatchOrderRequest req, UUID customerUserId) {
        Batch batch = batches.findById(req.batchId())
                .orElseThrow(() -> ApiException.notFound("Batch not found"));
        Vendor vendor = approvedVendor(batch.getVendorId());
        if (!BatchMapper.openForOrders(batch)) {
            throw ApiException.badRequest("This batch is not currently accepting orders");
        }

        BatchOrder order = new BatchOrder();
        order.setBatchId(batch.getId());
        order.setVendorId(batch.getVendorId());
        order.setCustomerUserId(customerUserId);
        order.setCustomerName(req.customerName());
        order.setCustomerPhone(req.customerPhone());
        order.setCustomerEmail(req.customerEmail());
        order.setCurrency(batch.getCurrency());
        if (req.fulfillmentType() != null) order.setFulfillmentType(req.fulfillmentType());
        order.setDeliveryAddress(new Address(req.customerHouseNumber(), req.customerStreet(), req.customerCity(),
                req.customerState(), req.customerPostcode(), req.customerCountry()));
        order.setSourceChannel(req.sourceChannel() == null ? SourceChannel.VENDOR_LINK : req.sourceChannel());
        order.setNotes(req.notes());

        BigDecimal subtotal = BigDecimal.ZERO;
        for (CartLine line : req.items()) {
            BatchProduct bp = batchProducts.findById(line.batchProductId())
                    .orElseThrow(() -> ApiException.badRequest("Batch product not found"));
            if (!bp.getBatchId().equals(batch.getId()) || bp.getStatus() != BatchProductStatus.AVAILABLE) {
                throw ApiException.badRequest("'" + bp.getNameSnapshot() + "' is not available in this batch");
            }
            if (line.quantity() < bp.getMinOrderQuantity()) {
                throw ApiException.badRequest("Minimum order for '" + bp.getNameSnapshot() + "' is "
                        + bp.getMinOrderQuantity());
            }
            Integer remaining = bp.remaining();
            if (remaining != null && line.quantity() > remaining) {
                throw ApiException.badRequest("Only " + remaining + " of '" + bp.getNameSnapshot() + "' left");
            }
            BigDecimal lineTotal = bp.getBatchPrice().multiply(BigDecimal.valueOf(line.quantity()));
            BatchOrderItem item = new BatchOrderItem();
            item.setBatchProductId(bp.getId());
            item.setProductNameSnapshot(bp.getNameSnapshot());
            item.setQuantity(line.quantity());
            item.setUnitPrice(bp.getBatchPrice());
            item.setLineTotal(lineTotal);
            order.addItem(item);
            subtotal = subtotal.add(lineTotal);

            bp.setSoldQuantity(bp.getSoldQuantity() + line.quantity());
            if (bp.getQuantityLimit() > 0 && bp.getSoldQuantity() >= bp.getQuantityLimit()) {
                bp.setStatus(BatchProductStatus.SOLD_OUT);
            }
            batchProducts.save(bp);
        }

        order.setProductSubtotal(subtotal);
        order.setTotalAmount(subtotal.add(order.getDeliveryFee()));
        order.setPublicOrderId(generateId());
        orders.save(order);
        log.info("Batch order {} created: batch={} vendor={} total={}", order.getPublicOrderId(),
                batch.getId(), batch.getVendorId(), order.getTotalAmount());

        audit.logChange(order.getId(), AUDIT_TYPE, null, order.getStatus().name(), "SYSTEM", "Batch order created");
        notifications.notifyProvider(AUDIT_TYPE, order.getId(), "BATCH_ORDER_CREATED",
                "New batch order " + order.getPublicOrderId() + " for " + batch.getBatchName() + ".");
        notifications.notifyCustomer(AUDIT_TYPE, order.getId(), order.getCustomerEmail(), order.getCustomerPhone(),
                "BATCH_ORDER_CREATED", "Your order " + order.getPublicOrderId() + " for " + batch.getBatchName()
                        + " has been received. Please complete payment to confirm your spot.");

        CreatePaymentResponse pay = initiateStripe(order, vendor, order.getTotalAmount());
        return response(order, batch.getBatchName(), vendor.getBusinessName(), pay);
    }

    // ---- payments ----

    @Transactional(readOnly = true)
    public PaymentInitResponse initiatePayment(String publicOrderId, UUID customerUserId, String contact) {
        BatchOrder o = orders.findByPublicOrderId(publicOrderId.trim())
                .orElseThrow(() -> ApiException.notFound("Order not found"));
        if (!ownsOrder(o, customerUserId, contact)) {
            throw ApiException.forbidden("You can only pay for your own order");
        }
        if (!paymentProps.isEnabled()) {
            throw ApiException.badRequest("Online card payment is not available right now");
        }
        BigDecimal amount = o.balanceDue();
        if (amount.signum() <= 0) {
            throw ApiException.badRequest("This order is already paid");
        }
        Vendor vendor = vendors.findById(o.getVendorId()).orElseThrow(() -> ApiException.notFound("Vendor not found"));
        CreatePaymentResponse r = initiateStripe(o, vendor, amount);
        if (r == null || r.clientSecret() == null) {
            throw ApiException.badRequest("Could not start the card payment — please try again");
        }
        return new PaymentInitResponse(o.getPublicOrderId(), r.clientSecret(), r.reference(), amount, o.getCurrency());
    }

    @Transactional
    public void recordStripePayment(String publicOrderId, BigDecimal amount, String reference) {
        orders.findByPublicOrderId(publicOrderId).ifPresentOrElse(o -> {
            o.setAmountPaid(o.getAmountPaid().add(amount));
            applyPaymentStatus(o, reference);
            orders.save(o);
        }, () -> log.warn("No batch order for {} from Stripe event", publicOrderId));
    }

    @Transactional
    public void recordStripeRefund(String publicOrderId, BigDecimal amount, String reference) {
        orders.findByPublicOrderId(publicOrderId).ifPresent(o -> {
            o.setRefundedAmount(o.getRefundedAmount().add(amount));
            o.setAmountPaid(o.getAmountPaid().subtract(amount).max(BigDecimal.ZERO));
            if (o.getRefundedAmount().compareTo(o.getTotalAmount()) >= 0) {
                o.setPaymentStatus(PaymentStatus.REFUNDED);
            }
            orders.save(o);
            log.info("Batch order {} refunded {}", publicOrderId, amount);
        });
    }

    private void applyPaymentStatus(BatchOrder o, String reference) {
        if (o.getAmountPaid().compareTo(o.getTotalAmount()) >= 0) {
            o.setPaymentStatus(PaymentStatus.PAID);
            if (o.getStatus() == BatchOrderStatus.ORDER_RECEIVED || o.getStatus() == BatchOrderStatus.PAYMENT_PENDING) {
                transition(o, BatchOrderStatus.ASSIGNED_TO_BATCH, "stripe", "Paid via Stripe");
            }
            notifications.notifyCustomer(AUDIT_TYPE, o.getId(), o.getCustomerEmail(), o.getCustomerPhone(),
                    "PAYMENT_CONFIRMED", "Payment received for " + o.getPublicOrderId() + " — you're booked into the batch.");
        } else if (o.getAmountPaid().signum() > 0) {
            o.setPaymentStatus(PaymentStatus.PARTIAL);
        }
        audit.logChange(o.getId(), "BATCH_ORDER_PAYMENT", null, o.getPaymentStatus().name(), "stripe", reference);
    }

    private CreatePaymentResponse initiateStripe(BatchOrder o, Vendor vendor, BigDecimal amount) {
        if (!paymentProps.isEnabled() || amount == null || amount.signum() <= 0) {
            return null;
        }
        String customerId = o.getCustomerUserId() != null
                ? o.getCustomerUserId().toString() : "guest:" + o.getPublicOrderId();
        try {
            return paymentClient.createModulePayment(o.getPublicOrderId(), customerId, o.getVendorId(),
                    o.getCurrency(), amount, vendor.getCommissionRate(), "batch");
        } catch (Exception e) {
            log.warn("payment-service createModulePayment failed for {} ({}); order unaffected",
                    o.getPublicOrderId(), e.getMessage());
            return null;
        }
    }

    // ---- vendor operations ----

    @Transactional(readOnly = true)
    public List<BatchOrderResponse> byBatch(UUID vendorId, UUID batchId) {
        return orders.findByBatchIdOrderByCreatedAtDesc(batchId).stream()
                .filter(o -> o.getVendorId().equals(vendorId))
                .map(o -> response(o, batchName(o.getBatchId()), vendorName(vendorId))).toList();
    }

    @Transactional(readOnly = true)
    public List<BatchOrderResponse> forVendor(UUID vendorId) {
        String name = vendorName(vendorId);
        return orders.findByVendorIdOrderByCreatedAtDesc(vendorId).stream()
                .map(o -> response(o, batchName(o.getBatchId()), name)).toList();
    }

    @Transactional
    public BatchOrderResponse updateStatus(UUID vendorId, UUID orderId, BatchOrderStatus status, String note, String actor) {
        BatchOrder o = owned(vendorId, orderId);
        transition(o, status, actor, note);
        notifications.notifyCustomer(AUDIT_TYPE, o.getId(), o.getCustomerEmail(), o.getCustomerPhone(),
                "ORDER_STATUS_CHANGED", "Your batch order " + o.getPublicOrderId() + " is now " + status + ".");
        if (status == BatchOrderStatus.READY_FOR_PICKUP && o.getPickupCode() == null) {
            o.setPickupCode(CodeGenerator.pickupCode());
            orders.save(o);
        }
        return response(o, batchName(o.getBatchId()), vendorName(vendorId));
    }

    // ---- customer ----

    @Transactional(readOnly = true)
    public List<BatchOrderResponse> customerOrders(UUID customerUserId) {
        return orders.findByCustomerUserIdOrderByCreatedAtDesc(customerUserId).stream()
                .map(o -> response(o, batchName(o.getBatchId()), vendorName(o.getVendorId()))).toList();
    }

    @Transactional(readOnly = true)
    public BatchOrderResponse track(String publicOrderId, String contact) {
        BatchOrder o = orders.findByPublicOrderId(publicOrderId.trim())
                .orElseThrow(() -> ApiException.notFound("Order not found"));
        if (!ownsOrder(o, null, contact)) {
            throw ApiException.notFound("Order not found");
        }
        return response(o, batchName(o.getBatchId()), vendorName(o.getVendorId()));
    }

    // ---- helpers ----

    private void transition(BatchOrder o, BatchOrderStatus to, String actor, String note) {
        BatchOrderStatus from = o.getStatus();
        o.setStatus(to);
        orders.save(o);
        audit.logChange(o.getId(), AUDIT_TYPE, from.name(), to.name(), actor, note);
        log.info("Batch order {} {} -> {} (by {})", o.getPublicOrderId(), from, to, actor);
    }

    private Vendor approvedVendor(UUID vendorId) {
        Vendor v = vendors.findById(vendorId).orElseThrow(() -> ApiException.notFound("Vendor not found"));
        if (!v.isActive() || v.getVerificationStatus() != VendorStatus.APPROVED) {
            throw ApiException.badRequest("This vendor is not currently accepting orders");
        }
        return v;
    }

    private boolean ownsOrder(BatchOrder o, UUID customerUserId, String contact) {
        if (customerUserId != null && customerUserId.equals(o.getCustomerUserId())) {
            return true;
        }
        if (contact == null || contact.isBlank()) {
            return false;
        }
        String needle = contact.trim();
        return needle.equalsIgnoreCase(o.getCustomerPhone())
                || (o.getCustomerEmail() != null && needle.equalsIgnoreCase(o.getCustomerEmail()));
    }

    private BatchOrder owned(UUID vendorId, UUID orderId) {
        BatchOrder o = orders.findById(orderId).orElseThrow(() -> ApiException.notFound("Order not found"));
        if (!o.getVendorId().equals(vendorId)) {
            throw ApiException.forbidden("This order belongs to another vendor");
        }
        return o;
    }

    private BatchOrderResponse response(BatchOrder o, String batchName, String vendorName) {
        return mapper.order(o, batchName, vendorName);
    }

    private BatchOrderResponse response(BatchOrder o, String batchName, String vendorName, CreatePaymentResponse pay) {
        return pay == null ? mapper.order(o, batchName, vendorName)
                : mapper.order(o, batchName, vendorName, pay.clientSecret(), pay.reference());
    }

    private String batchName(UUID batchId) {
        return batches.findById(batchId).map(Batch::getBatchName).orElse("Batch");
    }

    private String vendorName(UUID vendorId) {
        return vendors.findById(vendorId).map(Vendor::getBusinessName).orElse("Vendor");
    }

    private String generateId() {
        String id = CodeGenerator.batchOrderId();
        while (orders.existsByPublicOrderId(id)) {
            id = CodeGenerator.batchOrderId();
        }
        return id;
    }
}
