package com.myorderlynk.app.batch;

import com.myorderlynk.app.batch.BatchDtos.PaymentInitResponse;
import com.myorderlynk.app.batch.ShipmentRequestDtos.ShipmentRequestCreate;
import com.myorderlynk.app.batch.ShipmentRequestDtos.ShipmentRequestResponse;
import com.myorderlynk.app.batch.ShipmentRequestDtos.WeighRequest;
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
 * Customer-supplied "Send My Items" cargo requests (spec §7.2, §8.3, §13.2). Submitted against a
 * batch, received and weighed by cargo staff, invoiced on the actual weight, paid via Stripe (keyed
 * by the {@code SR-} public id), then shipped through the cycle.
 */
@Service
@Slf4j
public class ShipmentRequestService {

    private static final String AUDIT_TYPE = "SHIPMENT_REQUEST";

    private final ShipmentRequestRepository requests;
    private final BatchRepository batches;
    private final VendorRepository vendors;
    private final BatchMapper mapper;
    private final PaymentClient paymentClient;
    private final PaymentServiceProperties paymentProps;
    private final BatchNotificationService notifications;
    private final AuditService audit;

    public ShipmentRequestService(ShipmentRequestRepository requests, BatchRepository batches,
                                  VendorRepository vendors, BatchMapper mapper, PaymentClient paymentClient,
                                  PaymentServiceProperties paymentProps, BatchNotificationService notifications,
                                  AuditService audit) {
        this.requests = requests;
        this.batches = batches;
        this.vendors = vendors;
        this.mapper = mapper;
        this.paymentClient = paymentClient;
        this.paymentProps = paymentProps;
        this.notifications = notifications;
        this.audit = audit;
    }

    /** Vendor records a manual payment collected out-of-band; only for admin-enabled (non-card) vendors. */
    @Transactional
    public ShipmentRequestResponse recordManualPayment(UUID vendorId, UUID requestId, BigDecimal amount, String reference, String actor) {
        Vendor vendor = vendors.findById(vendorId).orElseThrow(() -> ApiException.notFound("Vendor not found"));
        if (!vendor.isAlternativePaymentsEnabled()) {
            throw ApiException.forbidden("Your account isn't enabled for non-card payments");
        }
        ShipmentRequest s = owned(vendorId, requestId);
        if (s.getActualWeight() == null) {
            throw ApiException.badRequest("Weigh the items to set the charge before recording a payment");
        }
        BigDecimal amt = amount != null ? amount : s.balanceDue();
        if (amt.signum() <= 0) {
            throw ApiException.badRequest("Payment amount must be positive");
        }
        s.setAmountPaid(s.getAmountPaid().add(amt));
        if (s.getAmountPaid().compareTo(s.getTotalCharge()) >= 0) {
            s.setPaymentStatus(PaymentStatus.PAID);
            if (terminalBeforeShipping(s.getStatus())) {
                transition(s, ShipmentRequestStatus.ADDED_TO_BATCH, actor, "Paid (manual)");
            }
            notifyCustomer(s, "PAYMENT_CONFIRMED", "Payment received for " + s.getPublicRequestId()
                    + " — your items are added to the batch.");
        } else if (s.getAmountPaid().signum() > 0) {
            s.setPaymentStatus(PaymentStatus.PARTIAL);
        }
        audit.logChange(s.getId(), "SHIPMENT_PAYMENT", null, "MANUAL", actor, reference);
        requests.save(s);
        log.info("Manual payment {} {} recorded on {} by {}", amt, s.getCurrency(), s.getPublicRequestId(), actor);
        return response(s);
    }

    @Transactional
    public ShipmentRequestResponse create(ShipmentRequestCreate req, UUID customerUserId) {
        Batch batch = batches.findById(req.batchId())
                .orElseThrow(() -> ApiException.notFound("Batch not found"));
        approvedVendor(batch.getVendorId());
        if (batch.getBatchType() == BatchType.PRODUCT_BATCH) {
            throw ApiException.badRequest("This batch does not accept customer shipment requests");
        }
        if (!BatchMapper.openForOrders(batch)) {
            throw ApiException.badRequest("This batch is not currently accepting shipment requests");
        }

        ShipmentRequest s = new ShipmentRequest();
        s.setBatchId(batch.getId());
        s.setVendorId(batch.getVendorId());
        s.setCustomerUserId(customerUserId);
        s.setCustomerName(req.customerName());
        s.setCustomerPhone(req.customerPhone());
        s.setCustomerEmail(req.customerEmail());
        s.setItemDescription(req.itemDescription());
        s.setPackageCount(req.packageCount());
        s.setEstimatedWeight(req.estimatedWeight());
        s.setDeclaredValue(req.declaredValue());
        s.setRatePerKg(batch.getRatePerKg() == null ? BigDecimal.ZERO : batch.getRatePerKg());
        s.setHandlingFee(batch.getHandlingFee() == null ? BigDecimal.ZERO : batch.getHandlingFee());
        s.setCurrency(batch.getCurrency());
        s.setOriginDropOffLocation(req.originDropOffLocation());
        s.setDestinationLocation(req.destinationLocation());
        if (req.deliveryPreference() != null && !req.deliveryPreference().isBlank()) {
            s.setDeliveryPreference(req.deliveryPreference());
        }
        s.setRestrictedItemsConfirmed(req.restrictedItemsConfirmed());
        s.setSourceChannel(req.sourceChannel() == null ? SourceChannel.VENDOR_LINK : req.sourceChannel());
        s.setNotes(req.notes());
        // Preliminary estimate (not payable until actual weight is recorded).
        s.setTotalCharge(s.computeCharge());
        s.setStatus(ShipmentRequestStatus.AWAITING_DROP_OFF);
        s.setPublicRequestId(generateId());
        requests.save(s);
        log.info("Shipment request {} created: batch={} vendor={}", s.getPublicRequestId(),
                batch.getId(), batch.getVendorId());

        audit.logChange(s.getId(), AUDIT_TYPE, null, s.getStatus().name(), "SYSTEM", "Shipment request created");
        notifications.notifyProvider(AUDIT_TYPE, s.getId(), "SHIPMENT_REQUEST_CREATED",
                "New shipment request " + s.getPublicRequestId() + " for " + batch.getBatchName() + ".");
        notifications.notifyCustomer(AUDIT_TYPE, s.getId(), s.getCustomerEmail(), s.getCustomerPhone(),
                "SHIPMENT_REQUEST_CREATED", "Your shipment request " + s.getPublicRequestId()
                        + " is received. Please drop your items at: "
                        + (s.getOriginDropOffLocation() == null ? "the collection point" : s.getOriginDropOffLocation()));
        return mapper.shipment(s, batch.getBatchName(), vendorName(batch.getVendorId()));
    }

    // ---- cargo staff operations ----

    @Transactional
    public ShipmentRequestResponse receive(UUID vendorId, UUID requestId, String actor) {
        ShipmentRequest s = owned(vendorId, requestId);
        transition(s, ShipmentRequestStatus.RECEIVED_AT_COLLECTION, actor, "Received at collection point");
        notifyCustomer(s, "ITEM_RECEIVED", "We've received your items for " + s.getPublicRequestId()
                + ". They'll be weighed and invoiced shortly.");
        return response(s);
    }

    /** Record actual weight (+ optional rate/fee overrides), recalculate the charge and invoice. */
    @Transactional
    public ShipmentRequestResponse weigh(UUID vendorId, UUID requestId, WeighRequest req, String actor) {
        ShipmentRequest s = owned(vendorId, requestId);
        s.setActualWeight(req.actualWeight());
        if (req.ratePerKg() != null) s.setRatePerKg(req.ratePerKg());
        if (req.handlingFee() != null) s.setHandlingFee(req.handlingFee());
        if (req.deliveryFee() != null) s.setDeliveryFee(req.deliveryFee());
        s.setTotalCharge(s.computeCharge());
        s.setPaymentStatus(PaymentStatus.PENDING);
        transition(s, ShipmentRequestStatus.INVOICE_GENERATED, actor,
                "Weighed " + req.actualWeight() + "kg → " + s.getTotalCharge() + " " + s.getCurrency());
        notifyCustomer(s, "INVOICE_GENERATED", "Your items have been weighed (" + req.actualWeight()
                + "kg). Final charge: " + s.getTotalCharge() + " " + s.getCurrency()
                + ". Please pay to add them to the batch.");
        return response(s);
    }

    @Transactional
    public ShipmentRequestResponse updateStatus(UUID vendorId, UUID requestId, ShipmentRequestStatus status,
                                                String note, String actor) {
        ShipmentRequest s = owned(vendorId, requestId);
        transition(s, status, actor, note);
        if (status == ShipmentRequestStatus.READY_FOR_PICKUP && s.getPickupCode() == null) {
            s.setPickupCode(CodeGenerator.pickupCode());
            requests.save(s);
        }
        notifyCustomer(s, "SHIPMENT_STATUS_CHANGED", "Your shipment " + s.getPublicRequestId()
                + " is now " + status + ".");
        return response(s);
    }

    // ---- payments ----

    @Transactional(readOnly = true)
    public PaymentInitResponse initiatePayment(String publicRequestId, UUID customerUserId, String contact) {
        ShipmentRequest s = requests.findByPublicRequestId(publicRequestId.trim())
                .orElseThrow(() -> ApiException.notFound("Shipment request not found"));
        if (!ownsRequest(s, customerUserId, contact)) {
            throw ApiException.forbidden("You can only pay for your own shipment request");
        }
        if (s.getActualWeight() == null) {
            throw ApiException.badRequest("Your items haven't been weighed yet — payment opens after weighing");
        }
        if (!paymentProps.isEnabled()) {
            throw ApiException.badRequest("Online card payment is not available right now");
        }
        BigDecimal amount = s.balanceDue();
        if (amount.signum() <= 0) {
            throw ApiException.badRequest("This shipment request is already paid");
        }
        Vendor vendor = vendors.findById(s.getVendorId()).orElseThrow(() -> ApiException.notFound("Vendor not found"));
        CreatePaymentResponse r = initiateStripe(s, vendor, amount);
        if (r == null || r.clientSecret() == null) {
            throw ApiException.badRequest("Could not start the card payment — please try again");
        }
        return new PaymentInitResponse(s.getPublicRequestId(), r.clientSecret(), r.reference(), amount, s.getCurrency());
    }

    @Transactional
    public void recordStripePayment(String publicRequestId, BigDecimal amount, String reference) {
        requests.findByPublicRequestId(publicRequestId).ifPresentOrElse(s -> {
            s.setAmountPaid(s.getAmountPaid().add(amount));
            if (s.getAmountPaid().compareTo(s.getTotalCharge()) >= 0) {
                s.setPaymentStatus(PaymentStatus.PAID);
                if (terminalBeforeShipping(s.getStatus())) {
                    transition(s, ShipmentRequestStatus.ADDED_TO_BATCH, "stripe", "Paid via Stripe");
                }
                notifyCustomer(s, "PAYMENT_CONFIRMED", "Payment received for " + s.getPublicRequestId()
                        + " — your items are added to the batch.");
            } else if (s.getAmountPaid().signum() > 0) {
                s.setPaymentStatus(PaymentStatus.PARTIAL);
            }
            audit.logChange(s.getId(), "SHIPMENT_PAYMENT", null, s.getPaymentStatus().name(), "stripe", reference);
            requests.save(s);
        }, () -> log.warn("No shipment request for {} from Stripe event", publicRequestId));
    }

    @Transactional
    public void recordStripeRefund(String publicRequestId, BigDecimal amount, String reference) {
        requests.findByPublicRequestId(publicRequestId).ifPresent(s -> {
            s.setRefundedAmount(s.getRefundedAmount().add(amount));
            s.setAmountPaid(s.getAmountPaid().subtract(amount).max(BigDecimal.ZERO));
            if (s.getRefundedAmount().compareTo(s.getTotalCharge()) >= 0) {
                s.setPaymentStatus(PaymentStatus.REFUNDED);
            }
            requests.save(s);
        });
    }

    private CreatePaymentResponse initiateStripe(ShipmentRequest s, Vendor vendor, BigDecimal amount) {
        if (!paymentProps.isEnabled() || amount == null || amount.signum() <= 0) {
            return null;
        }
        String customerId = s.getCustomerUserId() != null
                ? s.getCustomerUserId().toString() : "guest:" + s.getPublicRequestId();
        try {
            return paymentClient.createModulePayment(s.getPublicRequestId(), customerId, s.getVendorId(),
                    s.getCurrency(), amount, vendor.getCommissionRate(), "cargo");
        } catch (Exception e) {
            log.warn("payment-service createModulePayment failed for {} ({}); request unaffected",
                    s.getPublicRequestId(), e.getMessage());
            return null;
        }
    }

    // ---- queries ----

    @Transactional(readOnly = true)
    public List<ShipmentRequestResponse> byBatch(UUID vendorId, UUID batchId) {
        return requests.findByBatchIdOrderByCreatedAtDesc(batchId).stream()
                .filter(s -> s.getVendorId().equals(vendorId))
                .map(this::response).toList();
    }

    @Transactional(readOnly = true)
    public List<ShipmentRequestResponse> forVendor(UUID vendorId) {
        return requests.findByVendorIdOrderByCreatedAtDesc(vendorId).stream().map(this::response).toList();
    }

    @Transactional(readOnly = true)
    public List<ShipmentRequestResponse> customerRequests(UUID customerUserId) {
        return requests.findByCustomerUserIdOrderByCreatedAtDesc(customerUserId).stream().map(this::response).toList();
    }

    @Transactional(readOnly = true)
    public ShipmentRequestResponse track(String publicRequestId, String contact) {
        ShipmentRequest s = requests.findByPublicRequestId(publicRequestId.trim())
                .orElseThrow(() -> ApiException.notFound("Shipment request not found"));
        if (!ownsRequest(s, null, contact)) {
            throw ApiException.notFound("Shipment request not found");
        }
        return response(s);
    }

    // ---- helpers ----

    private boolean terminalBeforeShipping(ShipmentRequestStatus s) {
        return s == ShipmentRequestStatus.INVOICE_GENERATED || s == ShipmentRequestStatus.PAYMENT_PENDING
                || s == ShipmentRequestStatus.WEIGHED || s == ShipmentRequestStatus.RECEIVED_AT_COLLECTION;
    }

    private void transition(ShipmentRequest s, ShipmentRequestStatus to, String actor, String note) {
        ShipmentRequestStatus from = s.getStatus();
        s.setStatus(to);
        requests.save(s);
        audit.logChange(s.getId(), AUDIT_TYPE, from.name(), to.name(), actor, note);
        log.info("Shipment request {} {} -> {} (by {})", s.getPublicRequestId(), from, to, actor);
    }

    private void notifyCustomer(ShipmentRequest s, String template, String body) {
        notifications.notifyCustomer(AUDIT_TYPE, s.getId(), s.getCustomerEmail(), s.getCustomerPhone(), template, body);
    }

    private Vendor approvedVendor(UUID vendorId) {
        Vendor v = vendors.findById(vendorId).orElseThrow(() -> ApiException.notFound("Vendor not found"));
        if (!v.isActive() || v.getVerificationStatus() != VendorStatus.APPROVED) {
            throw ApiException.badRequest("This cargo partner is not currently accepting requests");
        }
        return v;
    }

    private boolean ownsRequest(ShipmentRequest s, UUID customerUserId, String contact) {
        if (customerUserId != null && customerUserId.equals(s.getCustomerUserId())) {
            return true;
        }
        if (contact == null || contact.isBlank()) {
            return false;
        }
        String needle = contact.trim();
        return needle.equalsIgnoreCase(s.getCustomerPhone())
                || (s.getCustomerEmail() != null && needle.equalsIgnoreCase(s.getCustomerEmail()));
    }

    private ShipmentRequest owned(UUID vendorId, UUID requestId) {
        ShipmentRequest s = requests.findById(requestId)
                .orElseThrow(() -> ApiException.notFound("Shipment request not found"));
        if (!s.getVendorId().equals(vendorId)) {
            throw ApiException.forbidden("This shipment request belongs to another vendor");
        }
        return s;
    }

    private ShipmentRequestResponse response(ShipmentRequest s) {
        return mapper.shipment(s, batchName(s.getBatchId()), vendorName(s.getVendorId()));
    }

    private String batchName(UUID batchId) {
        return batches.findById(batchId).map(Batch::getBatchName).orElse("Batch");
    }

    private String vendorName(UUID vendorId) {
        return vendors.findById(vendorId).map(Vendor::getBusinessName).orElse("Vendor");
    }

    private String generateId() {
        String id = CodeGenerator.shipmentRequestId();
        while (requests.existsByPublicRequestId(id)) {
            id = CodeGenerator.shipmentRequestId();
        }
        return id;
    }
}
