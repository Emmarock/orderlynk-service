package com.myorderlynk.app.service;

import com.myorderlynk.app.domain.Address;
import com.myorderlynk.app.domain.User;
import com.myorderlynk.app.domain.Vendor;
import com.myorderlynk.app.domain.enums.ProductCategory;
import com.myorderlynk.app.domain.enums.UserRole;
import com.myorderlynk.app.domain.enums.VendorStatus;
import com.myorderlynk.app.dto.AuthDtos.AuthResponse;
import com.myorderlynk.app.dto.Mapper;
import com.myorderlynk.app.dto.ProductDtos.ProductResponse;
import com.myorderlynk.app.dto.VendorDtos.ApplyResponse;
import com.myorderlynk.app.dto.VendorDtos.SellerRegistrationRequest;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriUtils;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@Slf4j
public class VendorService {

    private final VendorRepository vendors;
    private final UserRepository users;
    private final ProductRepository products;
    private final Mapper mapper;
    private final JwtService jwtService;
    private final AuthService authService;
    private final S3StorageService storage;
    private final String publicBaseUrl;

    public VendorService(VendorRepository vendors, UserRepository users, ProductRepository products,
                         Mapper mapper, JwtService jwtService, AuthService authService, S3StorageService storage,
                         @Value("${app.public-base-url:https://orderlynk.app}") String publicBaseUrl) {
        this.vendors = vendors;
        this.users = users;
        this.products = products;
        this.mapper = mapper;
        this.jwtService = jwtService;
        this.authService = authService;
        this.storage = storage;
        this.publicBaseUrl = publicBaseUrl;
    }

    /**
     * One-step seller signup for a new (unauthenticated) user: creates the user account as a VENDOR
     * and a linked Vendor in SUBMITTED state, then returns a fresh authenticated session. Password
     * strength and confirmation are enforced by validation on {@link SellerRegistrationRequest}.
     */
    @Transactional
    public AuthResponse registerSeller(SellerRegistrationRequest req) {
        User user = authService.createUser(req.fullName(), req.email(), req.password(),
                req.phone(), req.city(), req.country(), UserRole.VENDOR);

        Vendor vendor = new Vendor();
        vendor.setBusinessName(req.businessName());
        vendor.setDescription(req.description());
        vendor.setAddress(new Address(null, null, req.city(), null, null, req.country()));
        vendor.setWhatsappNumber(req.whatsappNumber());
        vendor.setInstagramHandle(req.instagramHandle());
        if (req.fulfillmentTypes() != null) {
            vendor.getFulfillmentTypes().addAll(req.fulfillmentTypes());
        }
        vendor.setOwnerUserId(user.getId());
        vendor.setVerificationStatus(VendorStatus.SUBMITTED);
        vendor.setActive(false);
        vendor.setStoreSlug(uniqueSlug(req.businessName()));
        vendors.save(vendor);

        user.setVendorId(vendor.getId());
        users.save(user);
        log.info("Seller signup: vendor={} slug={} user={}", vendor.getId(), vendor.getStoreSlug(), user.getId());

        // Issue a token now carrying the VENDOR role + vendorId so the client is signed in immediately.
        return new AuthResponse(jwtService.issueToken(user), user.getId(), user.getFullName(), user.getEmail(),
                user.getRole(), user.getVendorId(), user.isEmailVerified());
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
        vendor.setAddress(new Address(req.houseNumber(), req.street(), req.city(), req.state(), req.postcode(), req.country()));
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
        log.info("Vendor application: vendor={} slug={} by user={}", vendor.getId(), vendor.getStoreSlug(), userId);

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
        if (req.businessName() != null && !req.businessName().isBlank()) vendor.setBusinessName(req.businessName());
        if (req.description() != null) vendor.setDescription(req.description());
        Address addr = vendor.getAddress();
        if (req.houseNumber() != null) addr.setHouseNumber(req.houseNumber());
        if (req.street() != null) addr.setStreet(req.street());
        if (req.city() != null) addr.setCity(req.city());
        if (req.state() != null) addr.setState(req.state());
        if (req.postcode() != null) addr.setPostcode(req.postcode());
        if (req.country() != null) addr.setCountry(req.country());
        if (req.whatsappNumber() != null) vendor.setWhatsappNumber(req.whatsappNumber());
        if (req.instagramHandle() != null) vendor.setInstagramHandle(req.instagramHandle());
        if (req.logoUrl() != null) vendor.setLogoUrl(req.logoUrl());
        if (req.bannerUrl() != null) vendor.setBannerUrl(req.bannerUrl());
        if (req.fulfillmentTypes() != null) {
            vendor.getFulfillmentTypes().clear();
            vendor.getFulfillmentTypes().addAll(req.fulfillmentTypes());
        }
        // Payout details
        if (req.payoutMethod() != null) vendor.setPayoutMethod(req.payoutMethod());
        if (req.payoutAccountName() != null) vendor.setPayoutAccountName(req.payoutAccountName());
        if (req.payoutAccountNumber() != null) vendor.setPayoutAccountNumber(req.payoutAccountNumber());
        if (req.payoutBankName() != null) vendor.setPayoutBankName(req.payoutBankName());
        if (req.payoutEmail() != null) vendor.setPayoutEmail(req.payoutEmail());
        // Notification preferences
        if (req.notifyByEmail() != null) vendor.setNotifyByEmail(req.notifyByEmail());
        if (req.notifyByWhatsapp() != null) vendor.setNotifyByWhatsapp(req.notifyByWhatsapp());
        if (req.lowStockAlerts() != null) vendor.setLowStockAlerts(req.lowStockAlerts());
        log.info("Storefront settings updated for vendor {}", vendorId);
        return mapper.vendor(vendors.save(vendor));
    }

    /**
     * Upload a vendor branding image (logo or banner) from the vendor's device to S3 and
     * return its public URL. The vendor then saves the URL via {@link #updateStorefront}.
     */
    public String uploadBrandingImage(UUID vendorId, String kind, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("No image file was provided");
        }
        String folder = switch (kind == null ? "" : kind.toLowerCase()) {
            case "logo" -> "logo";
            case "banner" -> "banner";
            default -> throw ApiException.badRequest("kind must be 'logo' or 'banner'");
        };
        String ext = ImageUploads.extensionOrThrow(file.getContentType());
        String key = "vendors/" + vendorId + "/" + folder + "/" + UUID.randomUUID() + "." + ext;
        log.info("Uploading vendor {} image ({}) type={} size={}B key={}",
                vendorId, folder, file.getContentType(), file.getSize(), key);
        try {
            String url = storage.uploadPublic(file.getBytes(), file.getContentType(), key);
            log.info("Vendor {} {} uploaded -> {}", vendorId, folder, url);
            return url;
        } catch (java.io.IOException e) {
            log.error("Failed to read uploaded {} image for vendor {}", folder, vendorId, e);
            throw ApiException.badRequest("Could not read the uploaded image");
        }
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
        return new StorefrontResponse(mapper.publicVendor(vendor), activeProducts);
    }

    /**
     * Marketplace listing of approved, active vendors, optionally filtered by city
     * and/or product category. Always sorted so the highest-rated vendors lead —
     * which, combined with the category filter, surfaces top-rated vendors per category.
     */
    @Transactional(readOnly = true)
    public List<VendorResponse> marketplace(String city, ProductCategory category) {
        List<Vendor> list = (city == null || city.isBlank())
                ? vendors.findByActiveTrueAndVerificationStatus(VendorStatus.APPROVED)
                : vendors.findByActiveTrueAndVerificationStatusAndAddressCityIgnoreCase(VendorStatus.APPROVED, city);

        if (category != null) {
            Set<UUID> vendorIdsInCategory = Set.copyOf(products.findVendorIdsByActiveCategory(category));
            list = list.stream().filter(v -> vendorIdsInCategory.contains(v.getId())).toList();
        }

        return list.stream().sorted(BY_RATING_DESC).map(mapper::publicVendor).toList();
    }

    /** Highest average rating first (unrated last), tie-broken by number of ratings, then name. */
    private static final Comparator<Vendor> BY_RATING_DESC = Comparator
            .comparing((Vendor v) -> v.getRating() == null ? BigDecimal.valueOf(-1) : v.getRating(),
                    Comparator.reverseOrder())
            .thenComparing(Vendor::getRatingCount, Comparator.reverseOrder())
            .thenComparing(Vendor::getBusinessName, String.CASE_INSENSITIVE_ORDER);

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
