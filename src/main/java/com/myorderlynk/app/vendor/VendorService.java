package com.myorderlynk.app.vendor;
import com.myorderlynk.app.integration.ImageUploads;
import com.myorderlynk.app.integration.S3StorageService;
import com.myorderlynk.app.identity.AuthService;

import com.myorderlynk.app.common.Address;
import com.myorderlynk.app.identity.User;
import com.myorderlynk.app.common.enums.ProductCategory;
import com.myorderlynk.app.common.enums.UserRole;
import com.myorderlynk.app.common.enums.VendorStatus;
import com.myorderlynk.app.identity.AuthDtos.AuthResponse;
import com.myorderlynk.app.catalog.ProductMapper;
import com.myorderlynk.app.catalog.ProductDtos.ProductResponse;
import com.myorderlynk.app.vendor.VendorDtos.ApplyResponse;
import com.myorderlynk.app.vendor.VendorDtos.SellerRegistrationRequest;
import com.myorderlynk.app.vendor.VendorDtos.ShareLinkResponse;
import com.myorderlynk.app.vendor.VendorDtos.StorefrontResponse;
import com.myorderlynk.app.vendor.VendorDtos.VendorApplicationRequest;
import com.myorderlynk.app.vendor.VendorDtos.VendorResponse;
import com.myorderlynk.app.vendor.VendorDtos.VendorUpdateRequest;
import com.myorderlynk.app.catalog.ProductRepository;
import com.myorderlynk.app.identity.UserRepository;
import com.myorderlynk.app.security.JwtService;
import com.myorderlynk.app.common.CodeGenerator;
import com.myorderlynk.app.common.PageResponse;
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
    private final VendorMapper vendorMapper;
    private final ProductMapper productMapper;
    private final JwtService jwtService;
    private final AuthService authService;
    private final S3StorageService storage;
    private final com.myorderlynk.app.booking.ServiceDiscoveryService serviceDiscovery;
    private final com.myorderlynk.app.batch.BatchDiscoveryService batchDiscovery;
    private final String publicBaseUrl;

    public VendorService(VendorRepository vendors, UserRepository users, ProductRepository products,
                         VendorMapper vendorMapper, ProductMapper productMapper, JwtService jwtService, AuthService authService, S3StorageService storage,
                         com.myorderlynk.app.booking.ServiceDiscoveryService serviceDiscovery,
                         com.myorderlynk.app.batch.BatchDiscoveryService batchDiscovery,
                         @Value("${app.public-base-url:https://orderlynk.app}") String publicBaseUrl) {
        this.vendors = vendors;
        this.users = users;
        this.products = products;
        this.vendorMapper = vendorMapper;
        this.productMapper = productMapper;
        this.jwtService = jwtService;
        this.authService = authService;
        this.storage = storage;
        this.serviceDiscovery = serviceDiscovery;
        this.batchDiscovery = batchDiscovery;
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
        vendor.setAddress(new Address(req.houseNumber(), req.street(), req.city(), req.state(), req.postcode(), req.country()));
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
        vendor.setTiktokHandle(req.tiktokHandle());
        vendor.setFacebookPage(req.facebookPage());
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
        return new ApplyResponse(jwtService.issueToken(user), vendorMapper.vendor(vendor));
    }

    @Transactional(readOnly = true)
    public VendorResponse myVendor(UUID vendorId) {
        return vendorMapper.vendor(require(vendorId));
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
        if (req.tiktokHandle() != null) vendor.setTiktokHandle(req.tiktokHandle());
        if (req.facebookPage() != null) vendor.setFacebookPage(req.facebookPage());
        if (req.logoUrl() != null) vendor.setLogoUrl(req.logoUrl());
        if (req.bannerUrl() != null) vendor.setBannerUrl(req.bannerUrl());
        if (req.fulfillmentTypes() != null) {
            vendor.getFulfillmentTypes().clear();
            vendor.getFulfillmentTypes().addAll(req.fulfillmentTypes());
        }
        // Manual bank-transfer / payout details. Only validated when the payout section is the one
        // being submitted (i.e. it carries a currency), so saving other sections never trips it.
        if (req.payoutCurrency() != null && !req.payoutCurrency().isBlank()) {
            validatePayoutDetails(req);
        }
        if (req.payoutMethod() != null) vendor.setPayoutMethod(req.payoutMethod());
        if (req.payoutAccountName() != null) vendor.setPayoutAccountName(req.payoutAccountName());
        if (req.payoutAccountNumber() != null) vendor.setPayoutAccountNumber(req.payoutAccountNumber());
        if (req.payoutBankName() != null) vendor.setPayoutBankName(req.payoutBankName());
        if (req.payoutEmail() != null) vendor.setPayoutEmail(req.payoutEmail());
        if (req.payoutCurrency() != null) vendor.setPayoutCurrency(emptyToNull(req.payoutCurrency()));
        if (req.payoutSortCode() != null) vendor.setPayoutSortCode(emptyToNull(req.payoutSortCode()));
        if (req.payoutRoutingNumber() != null) vendor.setPayoutRoutingNumber(emptyToNull(req.payoutRoutingNumber()));
        if (req.payoutInstitutionNumber() != null) vendor.setPayoutInstitutionNumber(emptyToNull(req.payoutInstitutionNumber()));
        if (req.payoutTransitNumber() != null) vendor.setPayoutTransitNumber(emptyToNull(req.payoutTransitNumber()));
        if (req.payoutIban() != null) vendor.setPayoutIban(normalize(req.payoutIban()));
        if (req.payoutBic() != null) vendor.setPayoutBic(normalize(req.payoutBic()));
        if (req.payoutBankCode() != null) vendor.setPayoutBankCode(emptyToNull(req.payoutBankCode()));
        // Notification preferences
        if (req.notifyByEmail() != null) vendor.setNotifyByEmail(req.notifyByEmail());
        if (req.notifyByWhatsapp() != null) vendor.setNotifyByWhatsapp(req.notifyByWhatsapp());
        if (req.lowStockAlerts() != null) vendor.setLowStockAlerts(req.lowStockAlerts());
        log.info("Storefront settings updated for vendor {}", vendorId);
        return vendorMapper.vendor(vendors.save(vendor));
    }

    /** Supported manual-payout currencies (Stripe Connect handles card payouts for everything else). */
    private static final java.util.Set<String> PAYOUT_CURRENCIES = java.util.Set.of("NGN", "USD", "CAD", "GBP", "EUR");

    /**
     * Validate the manual bank-transfer details for the chosen currency. Mirrors the frontend's
     * inline checks so a vendor can't save details that would fail at payment time. Interac
     * e-transfer (CAD) only needs an email; every other arrangement needs account holder + bank
     * plus the currency-specific routing fields.
     */
    private static void validatePayoutDetails(VendorUpdateRequest req) {
        String currency = req.payoutCurrency().trim().toUpperCase();
        if (!PAYOUT_CURRENCIES.contains(currency)) {
            throw ApiException.badRequest("Unsupported payout currency: " + req.payoutCurrency());
        }
        // Interac e-Transfer (CAD): email is the only detail required.
        if ("INTERAC".equalsIgnoreCase(req.payoutMethod())) {
            if (blank(req.payoutEmail())) {
                throw ApiException.badRequest("Enter the Interac e-Transfer email");
            }
            return;
        }
        require("account holder name", req.payoutAccountName());
        require("bank name", req.payoutBankName());
        switch (currency) {
            case "NGN" -> requireDigits("account number (NUBAN)", req.payoutAccountNumber(), 10);
            case "USD" -> {
                require("account number", req.payoutAccountNumber());
                requireDigits("routing number", req.payoutRoutingNumber(), 9);
            }
            case "CAD" -> {
                require("account number", req.payoutAccountNumber());
                requireDigits("institution number", req.payoutInstitutionNumber(), 3);
                requireDigits("transit number", req.payoutTransitNumber(), 5);
            }
            case "GBP" -> {
                requireDigits("account number", req.payoutAccountNumber(), 8);
                requireDigits("sort code", req.payoutSortCode(), 6);
            }
            case "EUR" -> {
                requireIban(req.payoutIban());
                requireBic(req.payoutBic());
            }
            default -> throw ApiException.badRequest("Unsupported payout currency: " + currency);
        }
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    private static String emptyToNull(String s) {
        return blank(s) ? null : s.trim();
    }

    /** Uppercase + strip spaces (for IBAN/BIC), returning null when blank. */
    private static String normalize(String s) {
        return blank(s) ? null : s.replaceAll("\\s+", "").toUpperCase();
    }

    private static void require(String label, String value) {
        if (blank(value)) {
            throw ApiException.badRequest("Enter the " + label);
        }
    }

    private static void requireDigits(String label, String value, int length) {
        String digits = value == null ? "" : value.replaceAll("[\\s-]", "");
        if (!digits.matches("\\d{" + length + "}")) {
            throw ApiException.badRequest("Enter a valid " + label + " (" + length + " digits)");
        }
    }

    private static void requireBic(String value) {
        String bic = value == null ? "" : value.replaceAll("\\s", "").toUpperCase();
        if (!bic.matches("[A-Z0-9]{8}([A-Z0-9]{3})?")) {
            throw ApiException.badRequest("Enter a valid BIC / SWIFT code (8 or 11 characters)");
        }
    }

    /** Structural + ISO 7064 mod-97 IBAN check. */
    private static void requireIban(String value) {
        String iban = value == null ? "" : value.replaceAll("\\s", "").toUpperCase();
        if (!iban.matches("[A-Z]{2}\\d{2}[A-Z0-9]{10,30}")) {
            throw ApiException.badRequest("Enter a valid IBAN");
        }
        String rearranged = iban.substring(4) + iban.substring(0, 4);
        StringBuilder numeric = new StringBuilder();
        for (char c : rearranged.toCharArray()) {
            numeric.append(Character.isLetter(c) ? Integer.toString(c - 'A' + 10) : c);
        }
        if (new java.math.BigInteger(numeric.toString()).mod(java.math.BigInteger.valueOf(97)).intValue() != 1) {
            throw ApiException.badRequest("Enter a valid IBAN (checksum failed)");
        }
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
                .stream().map(productMapper::product).toList();
        return new StorefrontResponse(
                vendorMapper.publicVendor(vendor),
                activeProducts,
                serviceDiscovery.publicServices(vendor.getId()),
                batchDiscovery.publicBatches(vendor.getId()));
    }

    /**
     * Marketplace listing of approved, active vendors, optionally filtered by city
     * and/or product category. Always sorted so the highest-rated vendors lead —
     * which, combined with the category filter, surfaces top-rated vendors per category.
     */
    @Transactional(readOnly = true)
    public PageResponse<VendorResponse> marketplace(String city, ProductCategory category, int page, int size) {
        List<Vendor> list = (city == null || city.isBlank())
                ? vendors.findByActiveTrueAndVerificationStatus(VendorStatus.APPROVED)
                : vendors.findByActiveTrueAndVerificationStatusAndAddressCityIgnoreCase(VendorStatus.APPROVED, city);

        if (category != null) {
            Set<UUID> vendorIdsInCategory = Set.copyOf(products.findVendorIdsByActiveCategory(category));
            list = list.stream().filter(v -> vendorIdsInCategory.contains(v.getId())).toList();
        }

        List<VendorResponse> all = list.stream().sorted(BY_RATING_DESC).map(vendorMapper::publicVendor).toList();
        return PageResponse.of(all, page, size);
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
        String waText = "Order from " + vendor.getBusinessName() + " on OrderLynk: " + link;
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
