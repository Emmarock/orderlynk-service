package com.myorderlynk.app.service;

import com.myorderlynk.app.domain.User;
import com.myorderlynk.app.domain.Vendor;
import com.myorderlynk.app.domain.enums.UserRole;
import com.myorderlynk.app.domain.enums.VendorStatus;
import com.myorderlynk.app.dto.Mapper;
import com.myorderlynk.app.dto.ProductDtos.ProductResponse;
import com.myorderlynk.app.dto.VendorDtos.ApplyResponse;
import com.myorderlynk.app.dto.VendorDtos.ShareLinkResponse;
import com.myorderlynk.app.dto.VendorDtos.StorefrontResponse;
import com.myorderlynk.app.dto.VendorDtos.VendorApplicationRequest;
import com.myorderlynk.app.dto.VendorDtos.VendorResponse;
import com.myorderlynk.app.dto.VendorDtos.VendorUpdateRequest;
import com.myorderlynk.app.repository.ProductRepository;
import com.myorderlynk.app.repository.UserRepository;
import com.myorderlynk.app.repository.VendorRepository;
import com.myorderlynk.app.security.JwtService;
import com.myorderlynk.app.service.util.CodeGenerator;
import com.myorderlynk.app.exception.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@Service
public class VendorService {

    private final VendorRepository vendors;
    private final UserRepository users;
    private final ProductRepository products;
    private final Mapper mapper;
    private final JwtService jwtService;
    private final String publicBaseUrl;

    public VendorService(VendorRepository vendors, UserRepository users, ProductRepository products,
                         Mapper mapper, JwtService jwtService,
                         @Value("${app.public-base-url:https://orderlynk.app}") String publicBaseUrl) {
        this.vendors = vendors;
        this.users = users;
        this.products = products;
        this.mapper = mapper;
        this.jwtService = jwtService;
        this.publicBaseUrl = publicBaseUrl;
    }

    /** Vendor signup: creates a Vendor in SUBMITTED state and promotes the user to VENDOR (PRD §14). */
    @Transactional
    public ApplyResponse apply(UUID userId, VendorApplicationRequest req) {
        User user = users.findById(userId).orElseThrow(() -> ApiException.notFound("User not found"));
        if (user.getVendorId() != null) {
            throw new ApiException(HttpStatus.CONFLICT, "You have already registered a vendor");
        }
        Vendor vendor = new Vendor();
        vendor.setBusinessName(req.businessName());
        vendor.setDescription(req.description());
        vendor.setCity(req.city());
        vendor.setCountry(req.country());
        vendor.setWhatsappNumber(req.whatsappNumber());
        vendor.setInstagramHandle(req.instagramHandle());
        vendor.setLogoUrl(req.logoUrl());
        if (req.fulfillmentTypes() != null) {
            vendor.getFulfillmentTypes().addAll(req.fulfillmentTypes());
        }
        vendor.setOwnerUserId(userId);
        vendor.setVerificationStatus(VendorStatus.SUBMITTED);
        vendor.setActive(false);
        vendor.setStoreSlug(uniqueSlug(req.businessName()));
        vendors.save(vendor);

        user.setRole(UserRole.VENDOR);
        user.setVendorId(vendor.getId());
        users.save(user);

        // Re-issue token so the client immediately has VENDOR authority + vendorId claim.
        return new ApplyResponse(jwtService.issueToken(user), mapper.vendor(vendor));
    }

    @Transactional(readOnly = true)
    public VendorResponse myVendor(UUID vendorId) {
        return mapper.vendor(require(vendorId));
    }

    @Transactional
    public VendorResponse updateStorefront(UUID vendorId, VendorUpdateRequest req) {
        Vendor vendor = require(vendorId);
        if (req.description() != null) vendor.setDescription(req.description());
        if (req.city() != null) vendor.setCity(req.city());
        if (req.country() != null) vendor.setCountry(req.country());
        if (req.whatsappNumber() != null) vendor.setWhatsappNumber(req.whatsappNumber());
        if (req.instagramHandle() != null) vendor.setInstagramHandle(req.instagramHandle());
        if (req.logoUrl() != null) vendor.setLogoUrl(req.logoUrl());
        if (req.fulfillmentTypes() != null) {
            vendor.getFulfillmentTypes().clear();
            vendor.getFulfillmentTypes().addAll(req.fulfillmentTypes());
        }
        return mapper.vendor(vendors.save(vendor));
    }

    /** Public storefront by slug — only visible once approved + active (PRD §17). */
    @Transactional(readOnly = true)
    public StorefrontResponse storefront(String slug) {
        Vendor vendor = vendors.findByStoreSlug(slug)
                .orElseThrow(() -> ApiException.notFound("Store not found"));
        if (!vendor.isActive() || vendor.getVerificationStatus() != VendorStatus.APPROVED) {
            throw ApiException.notFound("Store not found");
        }
        List<ProductResponse> activeProducts = products.findByVendorIdAndActiveTrue(vendor.getId())
                .stream().map(mapper::product).toList();
        return new StorefrontResponse(mapper.vendor(vendor), activeProducts);
    }

    /** Marketplace listing of approved, active vendors, optionally filtered by city. */
    @Transactional(readOnly = true)
    public List<VendorResponse> marketplace(String city) {
        List<Vendor> list = (city == null || city.isBlank())
                ? vendors.findByActiveTrueAndVerificationStatus(VendorStatus.APPROVED)
                : vendors.findByActiveTrueAndVerificationStatusAndCityIgnoreCase(VendorStatus.APPROVED, city);
        return list.stream().map(mapper::vendor).toList();
    }

    /** Build a trackable share link (PRD §15): base + slug + ?source=&campaign=. */
    @Transactional(readOnly = true)
    public ShareLinkResponse shareLink(UUID vendorId, String source, String campaign) {
        Vendor vendor = require(vendorId);
        StringBuilder url = new StringBuilder(publicBaseUrl).append("/vendor/").append(vendor.getStoreSlug());
        if (source != null && !source.isBlank()) {
            url.append("?source=").append(enc(source));
            if (campaign != null && !campaign.isBlank()) {
                url.append("&campaign=").append(enc(campaign));
            }
        }
        String link = url.toString();
        String waText = "Order from " + vendor.getBusinessName() + " on Orderlynk: " + link;
        String whatsapp = "https://wa.me/?text=" + enc(waText);
        return new ShareLinkResponse(link, whatsapp);
    }

    public Vendor require(UUID vendorId) {
        return vendors.findById(vendorId).orElseThrow(() -> ApiException.notFound("Vendor not found"));
    }

    private String uniqueSlug(String businessName) {
        String base = CodeGenerator.slugify(businessName);
        String slug = base;
        while (vendors.existsByStoreSlug(slug)) {
            slug = base + "-" + CodeGenerator.randomSuffix();
        }
        return slug;
    }

    private static String enc(String value) {
        return UriUtils.encode(value, StandardCharsets.UTF_8);
    }
}
