package com.myorderlynk.app.order;
import com.myorderlynk.app.notification.NotificationService;
import com.myorderlynk.app.common.AuditService;

import com.myorderlynk.app.common.Address;
import com.myorderlynk.app.order.Order;
import com.myorderlynk.app.order.OrderItem;
import com.myorderlynk.app.order.PaymentRecord;
import com.myorderlynk.app.catalog.Product;
import com.myorderlynk.app.vendor.Vendor;
import com.myorderlynk.app.common.enums.FulfillmentStatus;
import com.myorderlynk.app.common.enums.FulfillmentType;
import com.myorderlynk.app.common.enums.PaymentMethod;
import com.myorderlynk.app.common.enums.PaymentStatus;
import com.myorderlynk.app.common.enums.SourceChannel;
import com.myorderlynk.app.common.enums.VendorStatus;
import com.myorderlynk.app.order.OrderDtos.CartLine;
import com.myorderlynk.app.order.OrderDtos.CheckoutRequest;
import com.myorderlynk.app.order.OrderDtos.FulfillmentUpdateRequest;
import com.myorderlynk.app.order.OrderDtos.OrderResponse;
import com.myorderlynk.app.order.OrderDtos.PaymentUpdateRequest;
import com.myorderlynk.app.order.OrderDtos.QuoteRequest;
import com.myorderlynk.app.order.OrderDtos.QuoteResponse;
import com.myorderlynk.app.order.OrderRepository;
import com.myorderlynk.app.common.PageResponse;
import com.myorderlynk.app.payment.PaymentClient;
import com.myorderlynk.app.payment.PaymentServiceProperties;
import com.myorderlynk.app.order.PaymentRecordRepository;
import com.myorderlynk.app.catalog.ProductRepository;
import com.myorderlynk.app.identity.AuthService;
import com.myorderlynk.app.identity.UserRepository;
import com.myorderlynk.app.vendor.VendorRepository;
import com.myorderlynk.app.exception.ApiException;
import com.myorderlynk.app.finance.VatLedgerService;
import com.myorderlynk.app.security.JwtService;
import com.myorderlynk.app.notification.EmailService;
import com.myorderlynk.app.notification.WhatsAppService;
import com.myorderlynk.app.shipping.ShippingRate;
import com.myorderlynk.app.shipping.ShippingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriUtils;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
public class OrderService {

    private final OrderRepository orders;
    private final ProductRepository products;
    private final VendorRepository vendors;
    private final PaymentRecordRepository payments;
    private final UserRepository users;
    private final FeeCalculator feeCalculator;
    private final NotificationService notifications;
    private final AuditService audit;
    private final OrderMapper mapper;
    private final EmailService emailService;
    private final AuthService authService;
    private final WhatsAppService whatsAppService;
    private final OrderLinks orderLinks;
    private final JwtService jwtService;
    private final ShippingService shippingService;
    private final PaymentClient paymentClient;
    private final PaymentServiceProperties paymentServiceProperties;
    private final VatLedgerService vatLedger;
    private final String publicBaseUrl;

    public OrderService(OrderRepository orders, ProductRepository products, VendorRepository vendors,
                        PaymentRecordRepository payments, UserRepository users, FeeCalculator feeCalculator,
                        NotificationService notifications, AuditService audit, OrderMapper mapper,
                        EmailService emailService, AuthService authService, WhatsAppService whatsAppService,
                        OrderLinks orderLinks, JwtService jwtService, ShippingService shippingService,
                        PaymentClient paymentClient, PaymentServiceProperties paymentServiceProperties,
                        VatLedgerService vatLedger,
                        @Value("${app.public-base-url:http://localhost:5173}") String publicBaseUrl) {
        this.orders = orders;
        this.products = products;
        this.vendors = vendors;
        this.payments = payments;
        this.users = users;
        this.feeCalculator = feeCalculator;
        this.notifications = notifications;
        this.audit = audit;
        this.mapper = mapper;
        this.emailService = emailService;
        this.authService = authService;
        this.whatsAppService = whatsAppService;
        this.orderLinks = orderLinks;
        this.jwtService = jwtService;
        this.shippingService = shippingService;
        this.paymentClient = paymentClient;
        this.paymentServiceProperties = paymentServiceProperties;
        this.vatLedger = vatLedger;
        this.publicBaseUrl = publicBaseUrl;
    }

    /** Resolve a signed track token to its order (keeps order id + contact out of the URL). */
    @Transactional(readOnly = true)
    public OrderResponse trackByToken(String token) {
        JwtService.OrderTrackToken claims = jwtService.parseOrderTrackToken(token);
        return track(claims.publicOrderId(), claims.contact());
    }

    /**
     * Resolve a signed order link into a card-payment session: returns the order plus a Stripe client
     * secret so the customer can pay by card from a link the vendor shared (WhatsApp, Instagram, …).
     * The PaymentIntent is idempotent per order id, so re-opening the link reuses the same intent.
     * An already-paid order comes back with no secret (the page shows "paid"); a card payment that
     * can't be started surfaces a clear error rather than a broken page.
     */
    @Transactional(readOnly = true)
    public OrderResponse payByToken(String token) {
        JwtService.OrderTrackToken claims = jwtService.parseOrderTrackToken(token);
        Order order = orders.findByPublicOrderId(claims.publicOrderId().trim())
                .orElseThrow(() -> ApiException.notFound("Order not found"));
        String needle = claims.contact() == null ? "" : claims.contact().trim();
        boolean matches = needle.equalsIgnoreCase(order.getCustomerPhone())
                || (order.getCustomerEmail() != null && needle.equalsIgnoreCase(order.getCustomerEmail()));
        if (!matches) {
            throw ApiException.notFound("Order not found");
        }

        Vendor vendor = vendors.findById(order.getVendorId()).orElse(null);
        if (order.getPaymentStatus() == PaymentStatus.PAID) {
            return mapper.order(order, vendor);
        }
        if (!paymentServiceProperties.isEnabled()) {
            throw ApiException.badRequest("Card payments aren't available right now");
        }
        String clientSecret = null;
        String paymentReference = null;
        try {
            var payment = paymentClient.createPayment(order); // idempotent on the public order id
            if (payment != null) {
                clientSecret = payment.clientSecret();
                paymentReference = payment.reference();
            }
        } catch (Exception e) {
            log.warn("pay-by-token createPayment failed for order {} ({})", order.getPublicOrderId(), e.getMessage());
            throw ApiException.badRequest("Could not start card payment — please try again");
        }
        if (clientSecret == null) {
            throw ApiException.badRequest("Could not start card payment — please try again");
        }
        return mapper.order(order, vendor, clientSecret, paymentReference);
    }

    @Transactional(readOnly = true)
    public QuoteResponse quote(QuoteRequest req) {
        Vendor vendor = approvedVendor(req.vendorId());
        Taxable taxable = taxableOf(req.items(), vendor.getId());
        Address destination = new Address(req.customerHouseNumber(), req.customerStreet(),
                req.customerCity(), req.customerState(), req.customerPostcode(), req.customerCountry());
        ShippingRate rate = liveShippingRate(vendor, req.items(), req.fulfillmentType(), destination,
                req.customerName(), req.customerPhone(), req.customerEmail(), req.shippingServiceToken());
        BigDecimal logisticsOverride = rate == null ? null : rate.amount();
        FeeCalculator.FeeBreakdown fb = feeCalculator.calculate(
                taxable.subtotal(), taxable.vat(), vendor.getVatCollector(),
                req.fulfillmentType(), req.paymentMethod(), vendor.getCommissionRate(), logisticsOverride);
        return new QuoteResponse(fb.productSubtotal(), fb.vatAmount(), fb.logisticsFee(), fb.platformFee(),
                fb.processingFee(), fb.totalAmount(), "CAD",
                rate != null,
                rate == null ? null : rate.carrier(),
                rate == null ? null : rate.serviceLevelName(),
                rate == null ? null : rate.serviceToken(),
                rate == null ? null : rate.estimatedDays());
    }

    @Transactional
    public OrderResponse checkout(CheckoutRequest req, UUID customerUserId) {
        Vendor vendor = approvedVendor(req.vendorId());
        if (req.paymentMethod() != PaymentMethod.CARD && !vendor.isAlternativePaymentsEnabled()) {
            throw ApiException.badRequest("This vendor currently only accepts card payments");
        }
        log.info("Checkout: vendor={} items={} customerUser={} fulfillment={} payment={}",
                vendor.getId(), req.items().size(), customerUserId, req.fulfillmentType(), req.paymentMethod());

        // Guests with a new email get an account pre-created + a set-password invite; existing emails
        // are linked to their account. Either way the order is attributed to a user id.
        UUID buyerId = customerUserId != null ? customerUserId
                : authService.resolveOrInviteCustomer(req.customerName(), req.customerEmail(),
                        req.customerPhone(), req.customerCity(), req.customerCountry());

        Order order = new Order();
        order.setVendorId(vendor.getId());
        order.setCustomerUserId(buyerId);
        order.setCustomerName(req.customerName());
        order.setCustomerPhone(req.customerPhone());
        order.setCustomerEmail(req.customerEmail());
        order.setDeliveryAddress(new Address(req.customerHouseNumber(), req.customerStreet(),
                req.customerCity(), req.customerState(), req.customerPostcode(), req.customerCountry()));
        order.setFulfillmentType(req.fulfillmentType());
        order.setSourceChannel(req.sourceChannel() == null ? SourceChannel.VENDOR_LINK : req.sourceChannel());
        order.setCampaign(req.campaign());
        order.setNotes(req.notes());
        order.setPaymentStatus(PaymentStatus.PENDING);
        order.setFulfillmentStatus(FulfillmentStatus.ORDER_RECEIVED);

        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal vatAmount = BigDecimal.ZERO;
        List<String> lowStockHits = new ArrayList<>();
        for (CartLine line : req.items()) {
            Product product = products.findById(line.productId())
                    .orElseThrow(() -> ApiException.badRequest("Product " + line.productId() + " not found"));
            if (!product.getVendorId().equals(vendor.getId()) || !product.isActive()) {
                throw ApiException.badRequest("Product '" + product.getName() + "' is not available from this vendor");
            }
            if (product.getQuantityAvailable() < line.quantity()) {
                throw ApiException.badRequest("Insufficient stock for '" + product.getName() + "'");
            }
            String selectedColor = resolveOption("colour", product.getName(), product.getColors(), line.selectedColor());
            String selectedSize = resolveOption("size", product.getName(), product.getSizes(), line.selectedSize());
            BigDecimal unitPrice = product.effectivePrice();
            BigDecimal lineTotal = unitPrice.multiply(BigDecimal.valueOf(line.quantity()));
            OrderItem item = new OrderItem();
            item.setProductId(product.getId());
            item.setVendorId(vendor.getId());
            item.setProductNameSnapshot(product.getName());
            item.setSelectedColor(selectedColor);
            item.setSelectedSize(selectedSize);
            item.setQuantity(line.quantity());
            item.setUnitPrice(unitPrice);
            item.setLineTotal(lineTotal);
            order.addItem(item);
            subtotal = subtotal.add(lineTotal);
            vatAmount = vatAmount.add(product.vatFor(line.quantity()));

            product.setQuantityAvailable(product.getQuantityAvailable() - line.quantity());
            products.save(product);

            if (product.getLowStockThreshold() > 0 && product.getQuantityAvailable() <= product.getLowStockThreshold()) {
                lowStockHits.add(product.getName() + " (" + product.getQuantityAvailable() + " left)");
            }
        }

        ShippingRate shippingRate = liveShippingRate(vendor, req.items(), req.fulfillmentType(),
                order.getDeliveryAddress(), req.customerName(), req.customerPhone(), req.customerEmail(),
                req.shippingServiceToken());
        BigDecimal logisticsOverride = shippingRate == null ? null : shippingRate.amount();
        FeeCalculator.FeeBreakdown fb = feeCalculator.calculate(
                subtotal, vatAmount, vendor.getVatCollector(),
                req.fulfillmentType(), req.paymentMethod(), vendor.getCommissionRate(), logisticsOverride);
        order.setProductSubtotal(fb.productSubtotal());
        order.setVatAmount(fb.vatAmount());
        // Snapshot who collects the VAT (only when there is VAT), so the order is self-describing.
        order.setVatCollector(fb.vatAmount().signum() > 0 ? vendor.getVatCollector() : null);
        order.setLogisticsFee(fb.logisticsFee());
        order.setPlatformFee(fb.platformFee());
        order.setProcessingFee(fb.processingFee());
        order.setTotalAmount(fb.totalAmount());
        order.setVendorPayable(fb.vendorPayable());
        order.setLogisticsPayable(fb.logisticsPayable());
        order.setPlatformRevenue(fb.platformRevenue());
        order.setPublicOrderId(generateOrderId());

        orders.save(order);
        log.info("Order placed: {} vendor={} total={} {}", order.getPublicOrderId(), vendor.getId(),
                order.getTotalAmount(), order.getCurrency());

        // Record the VAT transaction in the ledger (no-op when the order carries no VAT).
        vatLedger.recordForOrder(order);

        // Initiate the payment with the standalone payment-service. Best-effort and
        // flag-gated: a payment-service outage must not block order placement — the
        // order stays PENDING and the customer can be re-prompted. The returned client
        // secret is threaded into the response so the frontend can confirm the card.
        String clientSecret = null;
        String paymentReference = null;
        if (paymentServiceProperties.isEnabled()) {
            try {
                var payment = paymentClient.createPayment(order);
                if (payment != null) {
                    clientSecret = payment.clientSecret();
                    paymentReference = payment.reference();
                }
            } catch (Exception e) {
                log.warn("payment-service createPayment failed for order {} ({}); order still placed",
                        order.getPublicOrderId(), e.getMessage());
            }
        }

        if (shippingRate != null) {
            try {
                shippingService.createShipmentForOrder(order, vendor, shippingRate);
            } catch (Exception e) {
                log.warn("Could not persist shipment for order {} ({}); order still placed",
                        order.getPublicOrderId(), e.getMessage());
            }
        }

        audit.logChange(order.getId(), "FULFILLMENT", null, FulfillmentStatus.ORDER_RECEIVED.name(), "SYSTEM",
                "Order created");
        notifyOrderReceived(order, vendor);
        emailService.sendOrderCreated(order, vendor.getBusinessName());
        whatsAppService.orderCreated(order, vendor);
        notifications.notifyOrder(order, "DASHBOARD", "NEW_ORDER_ALERT", vendor.getBusinessName(),
                "New order " + order.getPublicOrderId() + " for " + order.getTotalAmount() + " " + order.getCurrency() + ".");
        if (!lowStockHits.isEmpty()) {
            log.warn("Low stock after order {} for vendor {}: {}", order.getPublicOrderId(), vendor.getId(), lowStockHits);
            notifications.notifyOrder(order, "DASHBOARD", "LOW_STOCK_ALERT", vendor.getBusinessName(),
                    "Low stock after order " + order.getPublicOrderId() + ": " + String.join(", ", lowStockHits));
        }

        return mapper.order(order, vendor, clientSecret, paymentReference);
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
        return mapper.order(order, vendors.findById(order.getVendorId()).orElse(null));
    }

    /** Vendor's orders, optionally restricted to a created-at window (null bounds = unbounded). */
    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> vendorOrders(UUID vendorId, Instant from, Instant to, Pageable pageable) {
        String name = vendorName(vendorId);
        Page<Order> page = (from == null && to == null)
                ? orders.findByVendorIdOrderByCreatedAtDesc(vendorId, pageable)
                : orders.findByVendorIdAndCreatedAtBetweenOrderByCreatedAtDesc(
                        vendorId, from == null ? Instant.EPOCH : from, to == null ? Instant.now() : to, pageable);
        return PageResponse.of(page.map(o -> mapper.order(o, name)));
    }

    /**
     * All of a single customer's orders with this vendor, matched by normalized phone (digits only),
     * mirroring the customer-identity key used in {@code VendorAnalyticsService}. Newest-first; bounded
     * because it's scoped to one customer.
     */
    @Transactional(readOnly = true)
    public List<OrderResponse> vendorCustomerOrders(UUID vendorId, String phone) {
        String target = phone == null ? "" : phone.replaceAll("\\D", "");
        String name = vendorName(vendorId);
        return orders.findByVendorIdOrderByCreatedAtDesc(vendorId).stream()
                .filter(o -> o.getCustomerPhone() != null
                        && o.getCustomerPhone().replaceAll("\\D", "").equals(target))
                .map(o -> mapper.order(o, name))
                .toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> customerOrders(UUID customerUserId, Pageable pageable) {
        return PageResponse.of(orders.findByCustomerUserIdOrderByCreatedAtDesc(customerUserId, pageable)
                .map(o -> mapper.order(o, vendors.findById(o.getVendorId()).orElse(null))));
    }

    @Transactional(readOnly = true)
    public OrderResponse getForVendor(UUID vendorId, UUID orderId) {
        Order order = vendorOwnedOrder(vendorId, orderId);
        return mapper.order(order, vendorName(vendorId), true);
    }

    @Transactional
    public OrderResponse updateFulfillment(UUID vendorId, UUID orderId, FulfillmentUpdateRequest req, String actor) {
        Order order = vendorOwnedOrder(vendorId, orderId);
        FulfillmentStatus from = order.getFulfillmentStatus();
        FulfillmentStatus to = req.status();
        if (from == to) {
            return mapper.order(order, vendorName(vendorId), true);
        }
        if (!FulfillmentFlows.isValidTransition(order.getFulfillmentType(), from, to)) {
            throw ApiException.badRequest("Cannot move order from " + from + " to " + to
                    + " for " + order.getFulfillmentType() + " fulfillment");
        }
        order.setFulfillmentStatus(to);
        if (to == FulfillmentStatus.READY_FOR_PICKUP && order.getPickupCode() == null) {
            order.setPickupCode(com.myorderlynk.app.common.CodeGenerator.pickupCode());
        }
        orders.save(order);
        log.info("Fulfillment {}: {} -> {} (order {}, by {})", orderId, from, to, order.getPublicOrderId(), actor);

        audit.logChange(orderId, "FULFILLMENT", from.name(), to.name(), actor, req.note());
        notifyFulfillment(order, to);
        emailService.sendOrderStatusChange(order, vendorName(vendorId), to);
        vendors.findById(vendorId).ifPresent(v -> whatsAppService.fulfillmentUpdated(order, v, to));
        return mapper.order(order, vendorName(vendorId), true);
    }

    @Transactional
    public OrderResponse updatePayment(UUID orderId, PaymentUpdateRequest req, String actor, UUID actingVendorId) {
        Order order = orders.findById(orderId).orElseThrow(() -> ApiException.notFound("Order not found"));
        if (actingVendorId != null && !order.getVendorId().equals(actingVendorId)) {
            throw ApiException.forbidden("This order belongs to another vendor");
        }
        // A vendor may only record non-card payments if an admin has enabled alternative methods.
        if (actingVendorId != null && req.method() != null && req.method() != PaymentMethod.CARD
                && !vendors.findById(actingVendorId).map(Vendor::isAlternativePaymentsEnabled).orElse(false)) {
            throw ApiException.forbidden("Your account isn't enabled for non-card payments");
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
        log.info("Payment {}: {} -> {} amount={} (order {}, by {})", orderId, from, to, amount,
                order.getPublicOrderId(), actor);

        audit.logChange(orderId, "PAYMENT", from.name(), to.name(), actor, req.transactionReference());
        if (to == PaymentStatus.PAID) {
            notifications.notifyOrder(order, "EMAIL", "PAYMENT_CONFIRMED", order.getCustomerEmail(),
                    "Payment confirmed for order " + order.getPublicOrderId() + ".");
            emailService.sendPaymentReceived(order, vendorName(order.getVendorId()), amount);
        }
        if (from != to) {
            vendors.findById(order.getVendorId()).ifPresent(v -> whatsAppService.paymentUpdated(order, v, to));
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

    /**
     * Live carrier rate for a shipping order, or null when the fulfillment type isn't shipped,
     * shipping isn't configured, the destination is incomplete, or rating fails — in every
     * "null" case the caller keeps the flat per-fulfillment logistics fee.
     */
    private ShippingRate liveShippingRate(Vendor vendor, List<CartLine> items, FulfillmentType type,
                                          Address destination, String name, String phone, String email,
                                          String serviceToken) {
        if (!ShippingService.isShippingFulfillment(type) || !shippingService.liveRatingAvailable()) {
            return null;
        }
        if (destination == null || destination.isEmpty()) {
            return null;
        }
        return shippingService.priceForCheckout(vendor, items, destination, name, phone, email, serviceToken)
                .orElse(null);
    }

    /**
     * Validate a customer's chosen variant option against what the product actually offers, and
     * return the canonical value to snapshot on the order item. When the product defines options,
     * the choice is required and must match one (case-insensitively). When it defines none, any
     * stray selection is ignored (returns null) so the item carries no variant.
     */
    private static String resolveOption(String label, String productName, List<String> options, String selected) {
        if (options == null || options.isEmpty()) {
            return null;
        }
        String chosen = selected == null ? "" : selected.trim();
        if (chosen.isEmpty()) {
            throw ApiException.badRequest("Please choose a " + label + " for '" + productName + "'");
        }
        return options.stream()
                .filter(o -> o.equalsIgnoreCase(chosen))
                .findFirst()
                .orElseThrow(() -> ApiException.badRequest(
                        "'" + chosen + "' is not an available " + label + " for '" + productName + "'"));
    }

    private Vendor approvedVendor(UUID vendorId) {
        Vendor vendor = vendors.findById(vendorId).orElseThrow(() -> ApiException.notFound("Vendor not found"));
        if (!vendor.isActive() || vendor.getVerificationStatus() != VendorStatus.APPROVED) {
            throw ApiException.badRequest("This vendor is not currently accepting orders");
        }
        return vendor;
    }

    /** Product subtotal and VAT for a set of cart lines, validating each belongs to the vendor. */
    private record Taxable(BigDecimal subtotal, BigDecimal vat) {}

    private Taxable taxableOf(List<CartLine> lines, UUID vendorId) {
        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal vat = BigDecimal.ZERO;
        for (CartLine line : lines) {
            Product product = products.findById(line.productId())
                    .orElseThrow(() -> ApiException.badRequest("Product " + line.productId() + " not found"));
            if (!product.getVendorId().equals(vendorId) || !product.isActive()) {
                throw ApiException.badRequest("Product '" + product.getName() + "' is not available from this vendor");
            }
            subtotal = subtotal.add(product.effectivePrice().multiply(BigDecimal.valueOf(line.quantity())));
            vat = vat.add(product.vatFor(line.quantity()));
        }
        return new Taxable(subtotal, vat);
    }

    private Order vendorOwnedOrder(UUID vendorId, UUID orderId) {
        Order order = orders.findById(orderId).orElseThrow(() -> ApiException.notFound("Order not found"));
        if (!order.getVendorId().equals(vendorId)) {
            throw ApiException.forbidden("This order belongs to another vendor");
        }
        return order;
    }

    /**
     * Notifies the customer that their order was received, via email when an email is on file,
     * otherwise WhatsApp (the phone is always present). The message always carries a tracking
     * link, and — only when the customer isn't already an OrderLynk user — a registration link.
     */
    private void notifyOrderReceived(Order order, Vendor vendor) {
        boolean hasEmail = order.getCustomerEmail() != null && !order.getCustomerEmail().isBlank();
        String channel = hasEmail ? "EMAIL" : "WHATSAPP";
        String recipient = hasEmail ? order.getCustomerEmail() : order.getCustomerPhone();

        // "Already registered" = signed in at checkout, or an account exists for this email.
        boolean registered = order.getCustomerUserId() != null
                || (hasEmail && users.existsByEmailIgnoreCase(order.getCustomerEmail()));

        StringBuilder body = new StringBuilder()
                .append("Hi ").append(order.getCustomerName()).append(",\n\n")
                .append("Your order ").append(order.getPublicOrderId())
                .append(" with ").append(vendor.getBusinessName()).append(" has been received.\n\n")
                .append("Track your order here: ").append(orderLinks.trackUrl(order)).append("\n");
        if (!registered) {
            body.append("\nNew to OrderLynk? Create an account to track all your orders in one place: ")
                    .append(registerLink(order));
        }

        log.info("Order-received notification for {} via {} (registerLinkIncluded={})",
                order.getPublicOrderId(), channel, !registered);
        notifications.notifyOrder(order, channel, "ORDER_RECEIVED", recipient, body.toString());
    }

    /** Registration link, pre-filling the customer's email when we have it. */
    private String registerLink(Order order) {
        String link = base() + "/register";
        if (order.getCustomerEmail() != null && !order.getCustomerEmail().isBlank()) {
            link += "?email=" + enc(order.getCustomerEmail());
        }
        return link;
    }

    private String base() {
        return publicBaseUrl.endsWith("/") ? publicBaseUrl.substring(0, publicBaseUrl.length() - 1) : publicBaseUrl;
    }

    private static String enc(String value) {
        return UriUtils.encode(value, StandardCharsets.UTF_8);
    }

    private String vendorName(UUID vendorId) {
        return vendors.findById(vendorId).map(Vendor::getBusinessName).orElse("Vendor");
    }

    private String generateOrderId() {
        String id = com.myorderlynk.app.common.CodeGenerator.orderId();
        while (orders.existsByPublicOrderId(id)) {
            id = com.myorderlynk.app.common.CodeGenerator.orderId();
        }
        return id;
    }
}
