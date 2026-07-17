package com.myorderlynk.app.shipping;

import com.myorderlynk.app.common.Address;
import com.myorderlynk.app.order.Order;
import com.myorderlynk.app.order.OrderItem;
import com.myorderlynk.app.catalog.Product;
import com.myorderlynk.app.identity.User;
import com.myorderlynk.app.vendor.Vendor;
import com.myorderlynk.app.common.enums.FulfillmentStatus;
import com.myorderlynk.app.common.enums.FulfillmentType;
import com.myorderlynk.app.identity.AddressDtos.AddressDto;
import com.myorderlynk.app.order.OrderDtos.CartLine;
import com.myorderlynk.app.exception.ApiException;
import com.myorderlynk.app.order.OrderRepository;
import com.myorderlynk.app.catalog.ProductRepository;
import com.myorderlynk.app.identity.UserRepository;
import com.myorderlynk.app.vendor.VendorRepository;
import com.myorderlynk.app.common.AuditService;
import com.myorderlynk.app.order.FulfillmentFlows;
import com.myorderlynk.app.notification.NotificationService;
import com.myorderlynk.app.shipping.ShippingDtos.RateOption;
import com.myorderlynk.app.shipping.ShippingDtos.RateQuoteRequest;
import com.myorderlynk.app.shipping.ShippingDtos.RateQuoteResponse;
import com.myorderlynk.app.shipping.ShippingDtos.ShipmentResponse;
import com.myorderlynk.app.shipping.ShippingDtos.TrackingEventResponse;
import com.myorderlynk.app.shipping.ShippingDtos.TrackingResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Orchestrates shipping for orders, on top of a provider-neutral {@link ShippingProvider}
 * (resolved via {@link ShippingProviderRegistry}). Responsibilities:
 * <ul>
 *   <li>Live rate quotes for the cart and for an existing order.</li>
 *   <li>Pricing a checkout's shipping cost (fed into the order's logistics fee).</li>
 *   <li>Persisting a {@link Shipment} per order, buying labels, and refreshing tracking.</li>
 *   <li>Applying carrier tracking webhooks back onto the shipment and order.</li>
 * </ul>
 * All carrier I/O is best-effort: when shipping is not configured, methods used at checkout
 * return empty so callers fall back to the flat per-fulfillment logistics fee.
 */
@Slf4j
@Service
public class ShippingService {

    private final ShippingProviderRegistry registry;
    private final ShippingProperties properties;
    private final ShipmentRepository shipments;
    private final OrderRepository orders;
    private final VendorRepository vendors;
    private final ProductRepository products;
    private final UserRepository users;
    private final AuditService audit;
    private final NotificationService notifications;

    public ShippingService(ShippingProviderRegistry registry, ShippingProperties properties,
                           ShipmentRepository shipments, OrderRepository orders, VendorRepository vendors,
                           ProductRepository products, UserRepository users, AuditService audit,
                           NotificationService notifications) {
        this.registry = registry;
        this.properties = properties;
        this.shipments = shipments;
        this.orders = orders;
        this.vendors = vendors;
        this.products = products;
        this.users = users;
        this.audit = audit;
        this.notifications = notifications;
    }

    /** True when a configured provider can supply live rates/labels. */
    public boolean liveRatingAvailable() {
        return registry.isConfigured();
    }

    /** Whether a fulfillment type ships via a carrier (and so should be rated live). */
    public static boolean isShippingFulfillment(FulfillmentType type) {
        return type == FulfillmentType.DOMESTIC_SHIPPING;
    }

    // ---- Cart-facing rate quote ----

    @Transactional(readOnly = true)
    public RateQuoteResponse rateOptions(RateQuoteRequest req) {
        Vendor vendor = vendors.findById(req.vendorId())
                .orElseThrow(() -> ApiException.notFound("Vendor not found"));
        if (!registry.isConfigured()) {
            throw new ApiException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                    "Shipping rates are not available right now");
        }
        ShippingAddress destination = toShippingAddress(toAddress(req.destination()),
                req.customerName(), req.customerPhone(), req.customerEmail());
        List<ShippingRate> rates = fetchRates(vendor, req.items(), destination, "cart", false);
        List<RateOption> options = rates.stream()
                .sorted(Comparator.comparing(r -> r.amount() == null ? BigDecimal.ZERO : r.amount()))
                .map(ShippingService::toOption)
                .toList();
        String currency = options.isEmpty() ? "CAD" : options.get(0).currency();
        return new RateQuoteResponse(currency, options);
    }

    // ---- Checkout integration ----

    /**
     * Price the shipping leg of a checkout. Returns the rate matching {@code serviceToken}
     * (or the cheapest when none/unmatched), or empty when shipping is unavailable or rating
     * fails — in which case the caller keeps the flat logistics fee.
     */
    public Optional<ShippingRate> priceForCheckout(Vendor vendor, List<CartLine> items, Address destination,
                                                   String customerName, String customerPhone, String customerEmail,
                                                   String serviceToken) {
        if (!registry.isConfigured()) {
            return Optional.empty();
        }
        try {
            ShippingAddress dest = toShippingAddress(destination, customerName, customerPhone, customerEmail);
            List<ShippingRate> rates = fetchRates(vendor, items, dest, "checkout", false);
            return pickRate(rates, serviceToken);
        } catch (ShippingException | ApiException e) {
            // Carrier error or an incomplete address (assertRatable) — never block the order; keep the flat fee.
            log.warn("Live shipping rating unavailable at checkout for vendor {} ({}); falling back to flat fee",
                    vendor.getId(), e.getMessage());
            return Optional.empty();
        }
    }

    /** Persist a RATED shipment for a freshly-placed order, from a rate returned by {@link #priceForCheckout}. */
    @Transactional
    public void createShipmentForOrder(Order order, Vendor vendor, ShippingRate rate) {
        Shipment shipment = new Shipment();
        shipment.setOrderId(order.getId());
        shipment.setVendorId(vendor.getId());
        shipment.setProvider(registry.active().map(ShippingProvider::key).orElse(properties.getProvider()));
        shipment.setStatus(ShipmentStatus.RATED);
        applyRate(shipment, rate);
        applyParcelSnapshot(shipment, buildParcel(cartLines(order.getItems())));
        shipment.setCurrency(rate.currency() != null ? rate.currency() : order.getCurrency());
        shipments.save(shipment);
        log.info("Shipment rated for order {}: {} {} ({} {})", order.getPublicOrderId(),
                rate.carrier(), rate.serviceLevelName(), rate.amount(), rate.currency());
    }

    // ---- Vendor: re-rate an existing order ----

    @Transactional
    public RateQuoteResponse ratesForOrder(UUID vendorId, UUID orderId) {
        Order order = ownedOrder(vendorId, orderId);
        Vendor vendor = vendors.findById(vendorId)
                .orElseThrow(() -> ApiException.notFound("Vendor not found"));
        ShippingAddress dest = toShippingAddress(order.getDeliveryAddress(),
                order.getCustomerName(), order.getCustomerPhone(), order.getCustomerEmail());
        List<ShippingRate> rates = fetchRates(vendor, cartLines(order.getItems()), dest,
                "order:" + order.getPublicOrderId(), true);
        List<RateOption> options = rates.stream()
                .sorted(Comparator.comparing(r -> r.amount() == null ? BigDecimal.ZERO : r.amount()))
                .map(ShippingService::toOption)
                .toList();

        // Upsert the live shipment row so a subsequent buy-label call has a fresh shipment + default rate.
        Optional<ShippingRate> cheapest = pickRate(rates, null);
        if (cheapest.isPresent()) {
            Shipment shipment = shipments.findFirstByOrderIdOrderByCreatedAtDesc(orderId)
                    .filter(s -> s.getStatus() == ShipmentStatus.RATED)
                    .orElseGet(Shipment::new);
            shipment.setOrderId(order.getId());
            shipment.setVendorId(vendorId);
            shipment.setProvider(registry.active().map(ShippingProvider::key).orElse(properties.getProvider()));
            shipment.setStatus(ShipmentStatus.RATED);
            applyRate(shipment, cheapest.get());
            applyParcelSnapshot(shipment, buildParcel(cartLines(order.getItems())));
            shipments.save(shipment);
        }
        String currency = options.isEmpty() ? order.getCurrency() : options.get(0).currency();
        return new RateQuoteResponse(currency, options);
    }

    // ---- Vendor: buy label ----

    @Transactional
    public ShipmentResponse buyLabel(UUID vendorId, UUID orderId, String rateId) {
        Order order = ownedOrder(vendorId, orderId);
        Shipment shipment = shipments.findFirstByOrderIdOrderByCreatedAtDesc(orderId)
                .orElseThrow(() -> ApiException.badRequest("No shipping rate on this order yet — fetch rates first"));
        if (!shipment.getVendorId().equals(vendorId)) {
            throw ApiException.forbidden("This shipment belongs to another vendor");
        }
        String rate = rateId != null && !rateId.isBlank() ? rateId : shipment.getRateId();
        if (rate == null || rate.isBlank()) {
            throw ApiException.badRequest("No shipping rate selected — fetch rates and choose one first");
        }
        if (shipment.getStatus() == ShipmentStatus.PURCHASED && shipment.getLabelUrl() != null && rateId == null) {
            return toResponse(shipment); // already bought; idempotent unless a new rate is forced
        }

        ShippingLabel label;
        try {
            label = registry.require().purchaseLabel(rate);
        } catch (ShippingException e) {
            String msg = e.getMessage() == null ? "" : e.getMessage().toLowerCase();
            if (msg.contains("complete address")) {
                // Rate was generated from an incomplete address; re-rating after a fix yields a buyable rate.
                throw ApiException.badRequest("This order's delivery address is incomplete, so the carrier won't "
                        + "sell a shipping label. Ask the customer to correct their address, then re-fetch rates.");
            }
            throw e;
        }
        shipment.setRateId(rate);
        shipment.setTransactionId(label.transactionId());
        shipment.setStatus(label.status() == ShipmentStatus.UNKNOWN ? ShipmentStatus.PURCHASED : label.status());
        shipment.setTrackingNumber(label.trackingNumber());
        shipment.setTrackingUrl(label.trackingUrlProvider());
        shipment.setLabelUrl(label.labelUrl());
        if (label.eta() != null) {
            shipment.setEta(label.eta());
        }
        shipments.save(shipment);
        log.info("Label purchased for order {} ({}): tracking={} label={}", order.getPublicOrderId(),
                shipment.getCarrier(), shipment.getTrackingNumber(), shipment.getLabelUrl());

        if (shipment.getStatus() == ShipmentStatus.PURCHASED) {
            advanceOrder(order, FulfillmentStatus.PACKED, "Shipping label purchased ("
                    + nullSafe(shipment.getCarrier()) + " " + nullSafe(shipment.getTrackingNumber()) + ")");
        }
        return toResponse(shipment);
    }

    // ---- Vendor: read shipment / refresh tracking ----

    @Transactional(readOnly = true)
    public Optional<ShipmentResponse> getShipment(UUID vendorId, UUID orderId) {
        ownedOrder(vendorId, orderId);
        return shipments.findFirstByOrderIdOrderByCreatedAtDesc(orderId).map(ShippingService::toResponse);
    }

    @Transactional
    public TrackingResponse refreshTracking(UUID vendorId, UUID orderId) {
        ownedOrder(vendorId, orderId);
        Shipment shipment = shipments.findFirstByOrderIdOrderByCreatedAtDesc(orderId)
                .orElseThrow(() -> ApiException.notFound("No shipment for this order"));
        if (shipment.getTrackingNumber() == null || shipment.getTrackingNumber().isBlank()) {
            throw ApiException.badRequest("This shipment has no tracking number yet");
        }
        TrackingInfo info = registry.require().track(carrierToken(shipment), shipment.getTrackingNumber());
        applyTracking(shipment, info.status(), info.eta(), lastDetail(info));
        shipments.save(shipment);
        syncOrderToTracking(shipment, info.status());

        List<TrackingEventResponse> events = info.events() == null ? List.of() : info.events().stream()
                .map(e -> new TrackingEventResponse(e.status(), e.statusDetails(), e.location(), e.occurredAt()))
                .toList();
        return new TrackingResponse(info.carrier(), info.trackingNumber(), info.status(), info.eta(), events);
    }

    // ---- Webhook: carrier-pushed tracking update ----

    @Transactional
    public void applyTrackingWebhook(String trackingNumber, ShipmentStatus status, Instant eta, String statusDetail) {
        if (trackingNumber == null || trackingNumber.isBlank()) {
            return;
        }
        Optional<Shipment> match = shipments.findByTrackingNumber(trackingNumber);
        if (match.isEmpty()) {
            log.info("Shippo webhook: no shipment matches tracking {}", trackingNumber);
            return;
        }
        Shipment shipment = match.get();
        applyTracking(shipment, status, eta, statusDetail);
        shipments.save(shipment);
        syncOrderToTracking(shipment, status);
        log.info("Shippo webhook applied: tracking {} -> {}", trackingNumber, status);
    }

    // ---- internal: rate fetching + parcel building ----

    /**
     * @param checkPickup validate the vendor's pickup address too — only on vendor-facing paths, so the
     *                    vendor-worded "your pickup address is incomplete" message never surfaces to a shopper.
     */
    private List<ShippingRate> fetchRates(Vendor vendor, List<CartLine> items, ShippingAddress destination,
                                          String metadata, boolean checkPickup) {
        ShippingAddress origin = fromVendor(vendor);
        requireDeliveryAddress(destination);
        if (checkPickup) {
            requirePickupAddress(origin);
        }
        ShipmentRequest request = new ShipmentRequest(
                origin, destination, List.of(buildParcel(items)), metadata);
        return registry.require().getRates(request);
    }

    /**
     * Guards against generating a rate the carrier will later refuse to sell a label against
     * ("a rate may only be purchased if it was generated with complete address information").
     * Throws a 400 naming the missing fields so the message is actionable — checkout catches this
     * and falls back to the flat logistics fee, so a placed order is never blocked.
     */
    private void requireDeliveryAddress(ShippingAddress destination) {
        List<String> missing = asAddress(destination).missingShippingFields();
        if (!missing.isEmpty()) {
            throw ApiException.badRequest("The delivery address is incomplete (missing "
                    + String.join(", ", missing) + "). Ask the customer to update it before buying a label.");
        }
    }

    /** Vendor-facing counterpart to {@link #requireDeliveryAddress}; only surfaced on vendor paths. */
    private void requirePickupAddress(ShippingAddress origin) {
        List<String> missing = asAddress(origin).missingShippingFields();
        if (!missing.isEmpty()) {
            throw ApiException.badRequest("Your pickup address is incomplete (missing "
                    + String.join(", ", missing) + "). Update it in your store settings before buying a label.");
        }
    }

    /** Adapt a provider address back to a {@link Address} so completeness rules live in one place. */
    private static Address asAddress(ShippingAddress a) {
        return new Address(null, a.street1(), a.city(), a.state(), a.zip(), a.country());
    }

    private static Optional<ShippingRate> pickRate(List<ShippingRate> rates, String serviceToken) {
        if (rates == null || rates.isEmpty()) {
            return Optional.empty();
        }
        if (serviceToken != null && !serviceToken.isBlank()) {
            Optional<ShippingRate> match = rates.stream()
                    .filter(r -> serviceToken.equalsIgnoreCase(r.serviceToken()))
                    .findFirst();
            if (match.isPresent()) {
                return match;
            }
        }
        return rates.stream().min(Comparator.comparing(r -> r.amount() == null ? BigDecimal.ZERO : r.amount()));
    }

    /**
     * Combine all order lines into a single parcel: total weight (grams) and the largest box
     * dimensions across items (cm). Products without weight/dimensions use the configured
     * defaults so a quote can still be produced.
     */
    private ShippingParcel buildParcel(List<CartLine> items) {
        ShippingProperties.Defaults d = properties.getDefaults();
        BigDecimal totalGrams = BigDecimal.ZERO;
        BigDecimal maxLength = BigDecimal.ZERO;
        BigDecimal maxWidth = BigDecimal.ZERO;
        BigDecimal maxHeight = BigDecimal.ZERO;
        for (CartLine line : items) {
            Product p = products.findById(line.productId()).orElse(null);
            BigDecimal qty = BigDecimal.valueOf(line.quantity());

            BigDecimal unitGrams = (p != null && p.getWeight() != null && p.getWeight().signum() > 0)
                    ? p.getWeightUnit().toGrams(p.getWeight())
                    : d.getWeightGrams();
            totalGrams = totalGrams.add(unitGrams.multiply(qty));

            maxLength = maxLength.max(dimCm(p, p == null ? null : p.getLength(), d.getLengthCm()));
            maxWidth = maxWidth.max(dimCm(p, p == null ? null : p.getWidth(), d.getWidthCm()));
            maxHeight = maxHeight.max(dimCm(p, p == null ? null : p.getHeight(), d.getHeightCm()));
        }
        if (totalGrams.signum() <= 0) {
            totalGrams = d.getWeightGrams();
        }
        if (maxLength.signum() <= 0) maxLength = d.getLengthCm();
        if (maxWidth.signum() <= 0) maxWidth = d.getWidthCm();
        if (maxHeight.signum() <= 0) maxHeight = d.getHeightCm();
        return new ShippingParcel(maxLength, maxWidth, maxHeight, DimensionUnit.CM, totalGrams, WeightUnit.G);
    }

    private static BigDecimal dimCm(Product p, BigDecimal value, BigDecimal fallback) {
        if (p == null || value == null || value.signum() <= 0) {
            return fallback;
        }
        return p.getDimensionUnit().toCentimeters(value);
    }

    private ShippingAddress fromVendor(Vendor vendor) {
        Address a = vendor.getAddress() == null ? new Address() : vendor.getAddress();
        // Carriers require a sender email on the label; use the owner's account email, then payout email.
        String email = vendor.getOwnerUserId() == null ? null
                : users.findById(vendor.getOwnerUserId()).map(User::getEmail).orElse(null);
        if (email == null || email.isBlank()) {
            email = vendor.getPayoutEmail();
        }
        return ShippingAddress.builder()
                .name(vendor.getBusinessName())
                .company(vendor.getBusinessName())
                .street1(street1(a))
                .city(a.getCity())
                .state(a.getState())
                .zip(a.getPostcode())
                .country(a.getCountry())
                .phone(vendor.getWhatsappNumber())
                .email(email)
                .residential(false)
                .build();
    }

    private ShippingAddress toShippingAddress(Address a, String name, String phone, String email) {
        Address addr = a == null ? new Address() : a;
        return ShippingAddress.builder()
                .name(name)
                .street1(street1(addr))
                .city(addr.getCity())
                .state(addr.getState())
                .zip(addr.getPostcode())
                .country(addr.getCountry())
                .phone(phone)
                .email(email)
                .residential(true)
                .build();
    }

    private static String street1(Address a) {
        String house = a.getHouseNumber();
        String street = a.getStreet();
        if (house != null && !house.isBlank() && street != null && !street.isBlank()) {
            return house.trim() + " " + street.trim();
        }
        return street != null && !street.isBlank() ? street : house;
    }

    private static List<CartLine> cartLines(List<OrderItem> items) {
        List<CartLine> lines = new ArrayList<>();
        for (OrderItem i : items) {
            // Variant selection doesn't affect parcel weight/dimensions, so it's irrelevant to rating.
            lines.add(new CartLine(i.getProductId(), i.getQuantity(), null, null));
        }
        return lines;
    }

    // ---- internal: shipment mutation helpers ----

    private static void applyRate(Shipment shipment, ShippingRate rate) {
        shipment.setProviderShipmentId(rate.providerShipmentId());
        shipment.setRateId(rate.rateId());
        shipment.setCarrier(rate.carrier());
        shipment.setServiceLevel(rate.serviceLevelName());
        shipment.setServiceToken(rate.serviceToken());
        shipment.setAmount(rate.amount());
        shipment.setCurrency(rate.currency());
        shipment.setEstimatedDays(rate.estimatedDays());
    }

    private static void applyParcelSnapshot(Shipment shipment, ShippingParcel parcel) {
        shipment.setWeightGrams(parcel.weight());
        shipment.setLengthCm(parcel.length());
        shipment.setWidthCm(parcel.width());
        shipment.setHeightCm(parcel.height());
    }

    private static void applyTracking(Shipment shipment, ShipmentStatus status, Instant eta, String detail) {
        if (status != null && status != ShipmentStatus.UNKNOWN) {
            shipment.setStatus(status);
        }
        if (eta != null) {
            shipment.setEta(eta);
        }
        if (detail != null && !detail.isBlank()) {
            shipment.setTrackingStatusDetail(detail);
        }
    }

    private static String lastDetail(TrackingInfo info) {
        if (info.events() == null || info.events().isEmpty()) {
            return null;
        }
        return info.events().get(info.events().size() - 1).statusDetails();
    }

    /** Carrier token Shippo's tracking API expects, e.g. "usps" from a "usps_priority" service token. */
    private static String carrierToken(Shipment shipment) {
        String token = shipment.getServiceToken();
        if (token != null && token.contains("_")) {
            return token.substring(0, token.indexOf('_')).toLowerCase();
        }
        return shipment.getCarrier() == null ? "shippo" : shipment.getCarrier().toLowerCase();
    }

    // ---- internal: order synchronisation ----

    /** Advance the order's fulfillment status to mirror a shipment milestone, if the move is valid. */
    private void syncOrderToTracking(Shipment shipment, ShipmentStatus status) {
        FulfillmentStatus target = switch (status) {
            case IN_TRANSIT -> FulfillmentStatus.SHIPPED;
            case DELIVERED -> FulfillmentStatus.DELIVERED;
            default -> null;
        };
        if (target == null) {
            return;
        }
        orders.findById(shipment.getOrderId()).ifPresent(order ->
                advanceOrder(order, target, "Carrier tracking: " + status));
    }

    private void advanceOrder(Order order, FulfillmentStatus target, String note) {
        FulfillmentStatus from = order.getFulfillmentStatus();
        if (from == target || !FulfillmentFlows.isValidTransition(order.getFulfillmentType(), from, target)) {
            return;
        }
        order.setFulfillmentStatus(target);
        orders.save(order);
        audit.logChange(order.getId(), "FULFILLMENT", from.name(), target.name(), "SHIPPING", note);
        String body = "Order " + order.getPublicOrderId() + " is now " + target.name().toLowerCase().replace('_', ' ')
                + (order.getCustomerEmail() != null ? "." : ".");
        notifications.notifyOrder(order, "EMAIL", "SHIPPING_UPDATE", order.getCustomerEmail(), body);
        log.info("Order {} fulfillment advanced {} -> {} by shipping ({})",
                order.getPublicOrderId(), from, target, note);
    }

    private Order ownedOrder(UUID vendorId, UUID orderId) {
        Order order = orders.findById(orderId).orElseThrow(() -> ApiException.notFound("Order not found"));
        if (!order.getVendorId().equals(vendorId)) {
            throw ApiException.forbidden("This order belongs to another vendor");
        }
        return order;
    }

    // ---- mapping ----

    private static RateOption toOption(ShippingRate r) {
        return new RateOption(r.rateId(), r.carrier(), r.serviceLevelName(), r.serviceToken(),
                r.amount(), r.currency(), r.estimatedDays(), r.durationTerms(), r.providerImageUrl());
    }

    private static ShipmentResponse toResponse(Shipment s) {
        return new ShipmentResponse(s.getId(), s.getOrderId(), s.getProvider(), s.getStatus(),
                s.getCarrier(), s.getServiceLevel(), s.getServiceToken(), s.getAmount(), s.getCurrency(),
                s.getEstimatedDays(), s.getTrackingNumber(), s.getTrackingUrl(), s.getLabelUrl(),
                s.getTrackingStatusDetail(), s.getEta(), s.getCreatedAt());
    }

    private static Address toAddress(AddressDto d) {
        return new Address(d.houseNumber(), d.street(), d.city(), d.state(), d.postcode(), d.country());
    }

    private static String nullSafe(String s) {
        return s == null ? "" : s;
    }
}