package com.myorderlynk.app.vendor;

import com.myorderlynk.app.vendor.AnalyticsDtos.BroadcastRequest;
import com.myorderlynk.app.vendor.AnalyticsDtos.BroadcastResult;
import com.myorderlynk.app.vendor.AnalyticsDtos.CustomerSummary;
import com.myorderlynk.app.vendor.AnalyticsDtos.VendorAnalytics;
import com.myorderlynk.app.finance.FinanceDtos.EarningsSummary;
import com.myorderlynk.app.support.SupportDtos.SupportRequest;
import com.myorderlynk.app.support.SupportDtos.SupportTicketResponse;
import com.myorderlynk.app.order.OrderDtos.CheckoutRequest;
import com.myorderlynk.app.order.OrderDtos.FulfillmentUpdateRequest;
import com.myorderlynk.app.order.OrderDtos.OrderResponse;
import com.myorderlynk.app.order.OrderDtos.PaymentUpdateRequest;
import com.myorderlynk.app.order.ChatOrderDtos.DraftOrder;
import com.myorderlynk.app.order.ChatOrderDtos.ParseChatRequest;
import com.myorderlynk.app.order.ChatOrderParser;
import com.myorderlynk.app.finance.PayoutDtos.PayoutResponse;
import com.myorderlynk.app.catalog.ProductDtos.DescriptionRequest;
import com.myorderlynk.app.catalog.ProductDtos.DescriptionResponse;
import com.myorderlynk.app.catalog.ProductDtos.ImageUploadResponse;
import com.myorderlynk.app.catalog.ProductDtos.ProductRequest;
import com.myorderlynk.app.catalog.ProductDtos.ProductResponse;
import com.myorderlynk.app.identity.AuthDtos.AuthResponse;
import com.myorderlynk.app.vendor.VendorDtos.ApplyResponse;
import com.myorderlynk.app.vendor.VendorDtos.SellerRegistrationRequest;
import com.myorderlynk.app.vendor.VendorDtos.ShareLinkResponse;
import com.myorderlynk.app.vendor.VendorDtos.VendorApplicationRequest;
import com.myorderlynk.app.vendor.VendorDtos.VendorResponse;
import com.myorderlynk.app.vendor.VendorDtos.VendorUpdateRequest;
import com.myorderlynk.app.payment.PaymentClient;
import com.myorderlynk.app.payment.PaymentDtos.ConnectAccountStatus;
import com.myorderlynk.app.payment.PaymentDtos.OnboardingResult;
import com.myorderlynk.app.security.AuthPrincipal;
import com.myorderlynk.app.security.CurrentUser;
import com.myorderlynk.app.finance.EarningsService;
import com.myorderlynk.app.order.OrderService;
import com.myorderlynk.app.finance.PayoutService;
import com.myorderlynk.app.catalog.ProductService;
import com.myorderlynk.app.support.SupportService;
import com.myorderlynk.app.vendor.VendorAnalyticsService;
import com.myorderlynk.app.vendor.VendorService;
import com.myorderlynk.app.exception.ApiException;
import com.myorderlynk.app.common.PageResponse;
import com.myorderlynk.app.common.PageRequests;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import com.myorderlynk.app.security.access.IsAuthenticated;
import com.myorderlynk.app.security.access.IsVendor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/vendor")
public class VendorController {

    private final VendorService vendorService;
    private final ProductService productService;
    private final OrderService orderService;
    private final com.myorderlynk.app.booking.BookingService bookingService;
    private final PayoutService payoutService;
    private final VendorAnalyticsService analyticsService;
    private final EarningsService earningsService;
    private final SupportService supportService;
    private final ChatOrderParser chatOrderParser;
    private final CurrentUser currentUser;
    private final PaymentClient paymentClient;
    private final FeaturedPlacementService featuredPlacementService;

    public VendorController(VendorService vendorService, ProductService productService, OrderService orderService,
                            com.myorderlynk.app.booking.BookingService bookingService,
                            PayoutService payoutService, VendorAnalyticsService analyticsService,
                            EarningsService earningsService, SupportService supportService,
                            ChatOrderParser chatOrderParser,
                            CurrentUser currentUser, PaymentClient paymentClient,
                            FeaturedPlacementService featuredPlacementService) {
        this.vendorService = vendorService;
        this.productService = productService;
        this.orderService = orderService;
        this.bookingService = bookingService;
        this.payoutService = payoutService;
        this.analyticsService = analyticsService;
        this.earningsService = earningsService;
        this.supportService = supportService;
        this.chatOrderParser = chatOrderParser;
        this.currentUser = currentUser;
        this.paymentClient = paymentClient;
        this.featuredPlacementService = featuredPlacementService;
    }

    // ---- Stripe Connect onboarding (proxies the payment-service) ----

    /** Start (or resume) Stripe onboarding: returns a hosted onboarding URL to redirect the vendor to. */
    @PostMapping("/connect/onboard")
    @IsVendor
    public OnboardingResult connectOnboard() {
        AuthPrincipal me = currentUser.require();
        return paymentClient.createConnectAccount(me.vendorId(), me.email(), null);
    }

    /** Cached Stripe onboarding/capability state for the vendor (canReceiveFunds gates card payments). */
    @GetMapping("/connect/status")
    @IsVendor
    public ConnectAccountStatus connectStatus() {
        return paymentClient.connectStatus(vendorId());
    }

    /** Force a live re-sync from Stripe (used after returning from onboarding / the Refresh button). */
    @PostMapping("/connect/refresh")
    @IsVendor
    public ConnectAccountStatus connectRefresh() {
        return paymentClient.refreshConnectStatus(vendorId());
    }

    /** Resolve an inclusive [from 00:00, to+1d 00:00) UTC window from optional ISO dates. */
    private static Instant[] range(LocalDate from, LocalDate to) {
        Instant start = from == null ? null : from.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant end = to == null ? null : to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        return new Instant[]{start, end};
    }

    /** Public one-step seller signup: creates the user account and the vendor together, returning a signed-in session. */
    @PostMapping("/register")
    public AuthResponse register(@Valid @RequestBody SellerRegistrationRequest req) {
        return vendorService.registerSeller(req);
    }

    /** Any authenticated user can apply to become a vendor (PRD §9). */
    @PostMapping("/apply")
    @IsAuthenticated
    public ApplyResponse apply(@Valid @RequestBody VendorApplicationRequest req) {
        return vendorService.apply(currentUser.require().userId(), req);
    }

    @GetMapping("/me")
    @IsVendor
    public VendorResponse myVendor() {
        return vendorService.myVendor(vendorId());
    }

    @PutMapping("/me")
    @IsVendor
    public VendorResponse updateStorefront(@Valid @RequestBody VendorUpdateRequest req) {
        return vendorService.updateStorefront(vendorId(), req);
    }

    /** Upload a branding image (kind=logo|banner) from the vendor's device; returns the public URL to save. */
    @PostMapping(value = "/branding/image", consumes = "multipart/form-data")
    @IsVendor
    public ImageUploadResponse uploadBrandingImage(@RequestParam("kind") String kind,
                                                   @RequestPart("file") MultipartFile file) {
        return new ImageUploadResponse(vendorService.uploadBrandingImage(vendorId(), kind, file));
    }

    @GetMapping("/share-link")
    @IsVendor
    public ShareLinkResponse shareLink(@RequestParam(required = false) String source,
                                       @RequestParam(required = false) String campaign) {
        return vendorService.shareLink(vendorId(), source, campaign);
    }

    // ---- Products ----

    @GetMapping("/products")
    @IsVendor
    public PageResponse<ProductResponse> products(@RequestParam(defaultValue = "0") int page,
                                                  @RequestParam(defaultValue = "20") int size) {
        return productService.listForVendor(vendorId(), PageRequests.of(page, size));
    }

    /** Products at or below their low-stock threshold (bounded list for dashboard alerts). */
    @GetMapping("/products/low-stock")
    @IsVendor
    public List<ProductResponse> lowStockProducts() {
        return productService.lowStockForVendor(vendorId());
    }

    @PostMapping("/products")
    @IsVendor
    public ProductResponse createProduct(@Valid @RequestBody ProductRequest req) {
        return productService.create(vendorId(), req);
    }

    /** Upload a product image from the vendor's device; returns the public URL to store as productImageUrl. */
    @PostMapping(value = "/products/image", consumes = "multipart/form-data")
    @IsVendor
    public ImageUploadResponse uploadProductImage(@RequestPart("file") MultipartFile file) {
        return new ImageUploadResponse(productService.uploadProductImage(vendorId(), file));
    }

    /** Upload a product video from the vendor's device; returns the public URL to store as videoUrl. */
    @PostMapping(value = "/products/video", consumes = "multipart/form-data")
    @IsVendor
    public ImageUploadResponse uploadProductVideo(@RequestPart("file") MultipartFile file) {
        return new ImageUploadResponse(productService.uploadProductVideo(vendorId(), file));
    }

    /** Generate a captivating, under-100-word product description from the name via AI. */
    @PostMapping("/products/description")
    @IsVendor
    public DescriptionResponse generateDescription(@Valid @RequestBody DescriptionRequest req) {
        return new DescriptionResponse(productService.generateDescription(req.name(), req.category()));
    }

    @PutMapping("/products/{id}")
    @IsVendor
    public ProductResponse updateProduct(@PathVariable UUID id, @Valid @RequestBody ProductRequest req) {
        return productService.update(vendorId(), id, req);
    }

    @PatchMapping("/products/{id}/active")
    @IsVendor
    public ProductResponse toggleProduct(@PathVariable UUID id, @RequestParam boolean active) {
        return productService.toggleActive(vendorId(), id, active);
    }

    @DeleteMapping("/products/{id}")
    @IsVendor
    public void deleteProduct(@PathVariable UUID id) {
        productService.delete(vendorId(), id);
    }

    // ---- Orders ----

    @GetMapping("/orders")
    @IsVendor
    public PageResponse<OrderResponse> orders(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "20") int size) {
        Instant[] r = range(from, to);
        return orderService.vendorOrders(vendorId(), r[0], r[1], PageRequests.of(page, size));
    }

    /**
     * Parse a pasted/forwarded chat thread (WhatsApp, Instagram, …) into a structured draft order
     * the vendor reviews before creating. Advisory only — nothing is persisted; line items are
     * matched against this vendor's catalogue and anything ambiguous is returned for manual fixup.
     */
    @PostMapping("/orders/parse-chat")
    @IsVendor
    public DraftOrder parseChatOrder(@Valid @RequestBody ParseChatRequest req) {
        UUID vid = vendorId();
        requireChatOrders(vid);
        return chatOrderParser.parse(vid, req.text());
    }

    /**
     * Vendor records an order on a customer's behalf — e.g. confirming a draft parsed from a chat.
     * The order is forced onto the authenticated vendor (the body's vendorId is ignored) and created
     * as a guest order attributed to the customer, reusing the same checkout pipeline (stock, fees,
     * customer invite) as the public storefront. Gated on the same admin-granted capability as parsing.
     */
    @PostMapping("/orders")
    @IsVendor
    public OrderResponse createOrder(@Valid @RequestBody CheckoutRequest req) {
        UUID vid = vendorId();
        requireChatOrders(vid);
        return orderService.checkout(req.withVendorId(vid), null);
    }

    /** Chat-order import is an opt-in capability an admin grants per vendor; 403 when it's off. */
    private void requireChatOrders(UUID vid) {
        if (!vendorService.myVendor(vid).chatOrderEnabled()) {
            throw ApiException.forbidden("Chat order import isn't enabled for your account");
        }
    }

    // ---- Customers & analytics ----

    /** Distinct customers who have ordered from this vendor (for outreach/broadcasts). */
    @GetMapping("/customers")
    @IsVendor
    public PageResponse<CustomerSummary> customers(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                   @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                                   @RequestParam(defaultValue = "0") int page,
                                                   @RequestParam(defaultValue = "20") int size) {
        Instant[] r = range(from, to);
        return analyticsService.customersPaged(vendorId(), r[0], r[1], page, size);
    }

    /** All of one customer's orders with this vendor, matched by normalized phone. Newest-first, bounded. */
    @GetMapping("/customers/{phone}/orders")
    @IsVendor
    public List<OrderResponse> customerOrders(@PathVariable String phone) {
        return orderService.vendorCustomerOrders(vendorId(), phone);
    }

    /** All of one customer's service bookings with this vendor, matched by normalized phone. */
    @GetMapping("/customers/{phone}/bookings")
    @IsVendor
    public List<com.myorderlynk.app.booking.BookingDtos.BookingResponse> customerBookings(@PathVariable String phone) {
        return bookingService.vendorCustomerBookings(vendorId(), phone);
    }

    /** Sales analytics: headline metrics plus top-5 customers and products. */
    @GetMapping("/analytics")
    @IsVendor
    public VendorAnalytics analytics(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        Instant[] r = range(from, to);
        return analyticsService.analytics(vendorId(), r[0], r[1]);
    }

    /** Broadcast a message to the vendor's customers (optionally scoped to the same date range). */
    @PostMapping("/customers/broadcast")
    @IsVendor
    public BroadcastResult broadcast(@Valid @RequestBody BroadcastRequest req,
                                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        Instant[] r = range(from, to);
        return analyticsService.broadcast(vendorId(), req.subject(), req.message(), r[0], r[1]);
    }

    // ---- Finance / earnings ----

    /** Earnings rollup (gross sales, commission, tax, net payout) + order-level breakdown for a date range. */
    @GetMapping("/earnings")
    @IsVendor
    public EarningsSummary earnings(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        Instant[] r = range(from, to);
        return earningsService.earnings(vendorId(), r[0], r[1]);
    }

    // ---- Support ("Message Us") ----

    @PostMapping("/support")
    @IsVendor
    public SupportTicketResponse createSupportTicket(@Valid @RequestBody SupportRequest req) {
        return supportService.create(vendorId(), currentUser.require().userId(), req);
    }

    @GetMapping("/support")
    @IsVendor
    public PageResponse<SupportTicketResponse> supportTickets(@RequestParam(defaultValue = "0") int page,
                                                              @RequestParam(defaultValue = "20") int size) {
        return supportService.forVendor(vendorId(), PageRequests.of(page, size));
    }

    @GetMapping("/orders/{id}")
    @IsVendor
    public OrderResponse order(@PathVariable UUID id) {
        return orderService.getForVendor(vendorId(), id);
    }

    @PatchMapping("/orders/{id}/fulfillment")
    @IsVendor
    public OrderResponse updateFulfillment(@PathVariable UUID id, @Valid @RequestBody FulfillmentUpdateRequest req) {
        AuthPrincipal me = currentUser.require();
        return orderService.updateFulfillment(me.vendorId(), id, req, "user:" + me.userId());
    }

    @PatchMapping("/orders/{id}/payment")
    @IsVendor
    public OrderResponse updatePayment(@PathVariable UUID id, @Valid @RequestBody PaymentUpdateRequest req) {
        AuthPrincipal me = currentUser.require();
        return orderService.updatePayment(id, req, "user:" + me.userId(), me.vendorId());
    }

    // ---- Payouts ----

    @GetMapping("/payouts")
    @IsVendor
    public PageResponse<PayoutResponse> payouts(@RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "20") int size) {
        return payoutService.forVendorPaged(vendorId(), PageRequests.of(page, size));
    }

    /**
     * Instantly pay out {@code amount} of the vendor's Stripe balance to their bank, for a platform fee
     * charged to their card on file. Returns the recorded instant payout.
     */
    @PostMapping("/payouts/instant")
    @IsVendor
    public PayoutResponse instantPayout(@RequestParam java.math.BigDecimal amount,
                                        @RequestParam(required = false) String currency) {
        return payoutService.requestInstantPayout(vendorId(), amount, currency);
    }

    // ---- Card-on-file billing (collects subscription / featured fees) ----

    /** Start saving a card: returns a SetupIntent client secret for Stripe Elements. */
    @PostMapping("/billing/card")
    @IsVendor
    public com.myorderlynk.app.payment.PaymentDtos.CardSetupResult startCardSetup() {
        AuthPrincipal me = currentUser.require();
        return paymentClient.startCardSetup(me.vendorId(), me.email());
    }

    /** Confirm the saved card once the SetupIntent has succeeded client-side. */
    @PostMapping("/billing/card/confirm")
    @IsVendor
    public com.myorderlynk.app.payment.PaymentDtos.BillingStatus confirmCard(
            @RequestParam String setupIntentId) {
        return paymentClient.confirmCard(vendorId(), setupIntentId);
    }

    /** Whether the vendor has a usable card on file for platform-fee collection. */
    @GetMapping("/billing")
    @IsVendor
    public com.myorderlynk.app.payment.PaymentDtos.BillingStatus billingStatus() {
        return paymentClient.billingStatus(vendorId());
    }

    // ---- Featured placement (paid marketplace promotion) ----

    /** Purchase a featured-placement slot (price/duration from fee settings); extends the boost window. */
    @PostMapping("/featured/purchase")
    @IsVendor
    public FeaturedPlacementDtos.PlacementResponse purchaseFeatured() {
        return FeaturedPlacementDtos.toResponse(featuredPlacementService.purchase(vendorId()));
    }

    @GetMapping("/featured")
    @IsVendor
    public List<FeaturedPlacementDtos.PlacementResponse> myFeatured() {
        return featuredPlacementService.forVendor(vendorId()).stream()
                .map(FeaturedPlacementDtos::toResponse).toList();
    }

    private UUID vendorId() {
        UUID vendorId = currentUser.require().vendorId();
        if (vendorId == null) {
            throw ApiException.forbidden("No vendor is linked to your account");
        }
        return vendorId;
    }
}
