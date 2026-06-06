package com.myorderlynk.app.controller;

import com.myorderlynk.app.dto.AnalyticsDtos.BroadcastRequest;
import com.myorderlynk.app.dto.AnalyticsDtos.BroadcastResult;
import com.myorderlynk.app.dto.AnalyticsDtos.CustomerSummary;
import com.myorderlynk.app.dto.AnalyticsDtos.VendorAnalytics;
import com.myorderlynk.app.dto.FinanceDtos.EarningsSummary;
import com.myorderlynk.app.dto.SupportDtos.SupportRequest;
import com.myorderlynk.app.dto.SupportDtos.SupportTicketResponse;
import com.myorderlynk.app.dto.OrderDtos.FulfillmentUpdateRequest;
import com.myorderlynk.app.dto.OrderDtos.OrderResponse;
import com.myorderlynk.app.dto.OrderDtos.PaymentUpdateRequest;
import com.myorderlynk.app.dto.PayoutDtos.PayoutResponse;
import com.myorderlynk.app.dto.ProductDtos.DescriptionRequest;
import com.myorderlynk.app.dto.ProductDtos.DescriptionResponse;
import com.myorderlynk.app.dto.ProductDtos.ImageUploadResponse;
import com.myorderlynk.app.dto.ProductDtos.ProductRequest;
import com.myorderlynk.app.dto.ProductDtos.ProductResponse;
import com.myorderlynk.app.dto.VendorDtos.ApplyResponse;
import com.myorderlynk.app.dto.VendorDtos.ShareLinkResponse;
import com.myorderlynk.app.dto.VendorDtos.VendorApplicationRequest;
import com.myorderlynk.app.dto.VendorDtos.VendorResponse;
import com.myorderlynk.app.dto.VendorDtos.VendorUpdateRequest;
import com.myorderlynk.app.security.AuthPrincipal;
import com.myorderlynk.app.security.CurrentUser;
import com.myorderlynk.app.service.EarningsService;
import com.myorderlynk.app.service.OrderService;
import com.myorderlynk.app.service.PayoutService;
import com.myorderlynk.app.service.ProductService;
import com.myorderlynk.app.service.SupportService;
import com.myorderlynk.app.service.VendorAnalyticsService;
import com.myorderlynk.app.service.VendorService;
import com.myorderlynk.app.exception.ApiException;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
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
    private final PayoutService payoutService;
    private final VendorAnalyticsService analyticsService;
    private final EarningsService earningsService;
    private final SupportService supportService;
    private final CurrentUser currentUser;

    public VendorController(VendorService vendorService, ProductService productService, OrderService orderService,
                            PayoutService payoutService, VendorAnalyticsService analyticsService,
                            EarningsService earningsService, SupportService supportService,
                            CurrentUser currentUser) {
        this.vendorService = vendorService;
        this.productService = productService;
        this.orderService = orderService;
        this.payoutService = payoutService;
        this.analyticsService = analyticsService;
        this.earningsService = earningsService;
        this.supportService = supportService;
        this.currentUser = currentUser;
    }

    /** Resolve an inclusive [from 00:00, to+1d 00:00) UTC window from optional ISO dates. */
    private static Instant[] range(LocalDate from, LocalDate to) {
        Instant start = from == null ? null : from.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant end = to == null ? null : to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        return new Instant[]{start, end};
    }

    /** Any authenticated user can apply to become a vendor (PRD §9). */
    @PostMapping("/apply")
    @PreAuthorize("isAuthenticated()")
    public ApplyResponse apply(@Valid @RequestBody VendorApplicationRequest req) {
        return vendorService.apply(currentUser.require().userId(), req);
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('VENDOR')")
    public VendorResponse myVendor() {
        return vendorService.myVendor(vendorId());
    }

    @PutMapping("/me")
    @PreAuthorize("hasRole('VENDOR')")
    public VendorResponse updateStorefront(@Valid @RequestBody VendorUpdateRequest req) {
        return vendorService.updateStorefront(vendorId(), req);
    }

    /** Upload a branding image (kind=logo|banner) from the vendor's device; returns the public URL to save. */
    @PostMapping(value = "/branding/image", consumes = "multipart/form-data")
    @PreAuthorize("hasRole('VENDOR')")
    public ImageUploadResponse uploadBrandingImage(@RequestParam("kind") String kind,
                                                   @RequestPart("file") MultipartFile file) {
        return new ImageUploadResponse(vendorService.uploadBrandingImage(vendorId(), kind, file));
    }

    @GetMapping("/share-link")
    @PreAuthorize("hasRole('VENDOR')")
    public ShareLinkResponse shareLink(@RequestParam(required = false) String source,
                                       @RequestParam(required = false) String campaign) {
        return vendorService.shareLink(vendorId(), source, campaign);
    }

    // ---- Products ----

    @GetMapping("/products")
    @PreAuthorize("hasRole('VENDOR')")
    public List<ProductResponse> products() {
        return productService.listForVendor(vendorId());
    }

    @PostMapping("/products")
    @PreAuthorize("hasRole('VENDOR')")
    public ProductResponse createProduct(@Valid @RequestBody ProductRequest req) {
        return productService.create(vendorId(), req);
    }

    /** Upload a product image from the vendor's device; returns the public URL to store as productImageUrl. */
    @PostMapping(value = "/products/image", consumes = "multipart/form-data")
    @PreAuthorize("hasRole('VENDOR')")
    public ImageUploadResponse uploadProductImage(@RequestPart("file") MultipartFile file) {
        return new ImageUploadResponse(productService.uploadProductImage(vendorId(), file));
    }

    /** Generate a captivating, under-100-word product description from the name via AI. */
    @PostMapping("/products/description")
    @PreAuthorize("hasRole('VENDOR')")
    public DescriptionResponse generateDescription(@Valid @RequestBody DescriptionRequest req) {
        return new DescriptionResponse(productService.generateDescription(req.name(), req.category()));
    }

    @PutMapping("/products/{id}")
    @PreAuthorize("hasRole('VENDOR')")
    public ProductResponse updateProduct(@PathVariable UUID id, @Valid @RequestBody ProductRequest req) {
        return productService.update(vendorId(), id, req);
    }

    @PatchMapping("/products/{id}/active")
    @PreAuthorize("hasRole('VENDOR')")
    public ProductResponse toggleProduct(@PathVariable UUID id, @RequestParam boolean active) {
        return productService.toggleActive(vendorId(), id, active);
    }

    @DeleteMapping("/products/{id}")
    @PreAuthorize("hasRole('VENDOR')")
    public void deleteProduct(@PathVariable UUID id) {
        productService.delete(vendorId(), id);
    }

    // ---- Orders ----

    @GetMapping("/orders")
    @PreAuthorize("hasRole('VENDOR')")
    public List<OrderResponse> orders(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        Instant[] r = range(from, to);
        return orderService.vendorOrders(vendorId(), r[0], r[1]);
    }

    // ---- Customers & analytics ----

    /** Distinct customers who have ordered from this vendor (for outreach/broadcasts). */
    @GetMapping("/customers")
    @PreAuthorize("hasRole('VENDOR')")
    public List<CustomerSummary> customers(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        Instant[] r = range(from, to);
        return analyticsService.customers(vendorId(), r[0], r[1]);
    }

    /** Sales analytics: headline metrics plus top-5 customers and products. */
    @GetMapping("/analytics")
    @PreAuthorize("hasRole('VENDOR')")
    public VendorAnalytics analytics(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        Instant[] r = range(from, to);
        return analyticsService.analytics(vendorId(), r[0], r[1]);
    }

    /** Broadcast a message to the vendor's customers (optionally scoped to the same date range). */
    @PostMapping("/customers/broadcast")
    @PreAuthorize("hasRole('VENDOR')")
    public BroadcastResult broadcast(@Valid @RequestBody BroadcastRequest req,
                                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        Instant[] r = range(from, to);
        return analyticsService.broadcast(vendorId(), req.subject(), req.message(), r[0], r[1]);
    }

    // ---- Finance / earnings ----

    /** Earnings rollup (gross sales, commission, tax, net payout) + order-level breakdown for a date range. */
    @GetMapping("/earnings")
    @PreAuthorize("hasRole('VENDOR')")
    public EarningsSummary earnings(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        Instant[] r = range(from, to);
        return earningsService.earnings(vendorId(), r[0], r[1]);
    }

    // ---- Support ("Message Us") ----

    @PostMapping("/support")
    @PreAuthorize("hasRole('VENDOR')")
    public SupportTicketResponse createSupportTicket(@Valid @RequestBody SupportRequest req) {
        return supportService.create(vendorId(), currentUser.require().userId(), req);
    }

    @GetMapping("/support")
    @PreAuthorize("hasRole('VENDOR')")
    public List<SupportTicketResponse> supportTickets() {
        return supportService.forVendor(vendorId());
    }

    @GetMapping("/orders/{id}")
    @PreAuthorize("hasRole('VENDOR')")
    public OrderResponse order(@PathVariable UUID id) {
        return orderService.getForVendor(vendorId(), id);
    }

    @PatchMapping("/orders/{id}/fulfillment")
    @PreAuthorize("hasRole('VENDOR')")
    public OrderResponse updateFulfillment(@PathVariable UUID id, @Valid @RequestBody FulfillmentUpdateRequest req) {
        AuthPrincipal me = currentUser.require();
        return orderService.updateFulfillment(me.vendorId(), id, req, "user:" + me.userId());
    }

    @PatchMapping("/orders/{id}/payment")
    @PreAuthorize("hasRole('VENDOR')")
    public OrderResponse updatePayment(@PathVariable UUID id, @Valid @RequestBody PaymentUpdateRequest req) {
        AuthPrincipal me = currentUser.require();
        return orderService.updatePayment(id, req, "user:" + me.userId(), me.vendorId());
    }

    // ---- Payouts ----

    @GetMapping("/payouts")
    @PreAuthorize("hasRole('VENDOR')")
    public List<PayoutResponse> payouts() {
        return payoutService.forVendor(vendorId());
    }

    private UUID vendorId() {
        UUID vendorId = currentUser.require().vendorId();
        if (vendorId == null) {
            throw ApiException.forbidden("No vendor is linked to your account");
        }
        return vendorId;
    }
}
