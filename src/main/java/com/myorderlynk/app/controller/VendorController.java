package com.myorderlynk.app.controller;

import com.myorderlynk.app.dto.OrderDtos.FulfillmentUpdateRequest;
import com.myorderlynk.app.dto.OrderDtos.OrderResponse;
import com.myorderlynk.app.dto.OrderDtos.PaymentUpdateRequest;
import com.myorderlynk.app.dto.PayoutDtos.PayoutResponse;
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
import com.myorderlynk.app.service.OrderService;
import com.myorderlynk.app.service.PayoutService;
import com.myorderlynk.app.service.ProductService;
import com.myorderlynk.app.service.VendorService;
import com.myorderlynk.app.exception.ApiException;
import jakarta.validation.Valid;
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

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/vendor")
public class VendorController {

    private final VendorService vendorService;
    private final ProductService productService;
    private final OrderService orderService;
    private final PayoutService payoutService;
    private final CurrentUser currentUser;

    public VendorController(VendorService vendorService, ProductService productService, OrderService orderService,
                            PayoutService payoutService, CurrentUser currentUser) {
        this.vendorService = vendorService;
        this.productService = productService;
        this.orderService = orderService;
        this.payoutService = payoutService;
        this.currentUser = currentUser;
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
    public List<OrderResponse> orders() {
        return orderService.vendorOrders(vendorId());
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
