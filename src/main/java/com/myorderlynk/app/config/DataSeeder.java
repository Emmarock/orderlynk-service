package com.myorderlynk.app.config;

import com.myorderlynk.app.booking.ApprovalMode;
import com.myorderlynk.app.booking.AvailabilityRule;
import com.myorderlynk.app.booking.AvailabilityRuleRepository;
import com.myorderlynk.app.booking.DepositType;
import com.myorderlynk.app.booking.ServiceCategory;
import com.myorderlynk.app.booking.ServiceLocationType;
import com.myorderlynk.app.booking.ServiceOffering;
import com.myorderlynk.app.booking.ServiceOfferingRepository;
import com.myorderlynk.app.booking.ServiceProviderProfile;
import com.myorderlynk.app.booking.ServiceProviderProfileRepository;
import com.myorderlynk.app.booking.ServiceVariant;
import com.myorderlynk.app.booking.ServiceVariantRepository;
import com.myorderlynk.app.catalog.Product;
import com.myorderlynk.app.identity.User;
import com.myorderlynk.app.vendor.Vendor;
import com.myorderlynk.app.common.enums.FulfillmentType;
import com.myorderlynk.app.common.enums.PaymentMethod;
import com.myorderlynk.app.common.enums.ProductCategory;
import com.myorderlynk.app.common.enums.SourceChannel;
import com.myorderlynk.app.common.enums.UserRole;
import com.myorderlynk.app.common.enums.VendorStatus;
import com.myorderlynk.app.order.OrderDtos.CartLine;
import com.myorderlynk.app.order.OrderDtos.CheckoutRequest;
import com.myorderlynk.app.catalog.ProductRepository;
import com.myorderlynk.app.identity.UserRepository;
import com.myorderlynk.app.vendor.VendorRepository;
import com.myorderlynk.app.order.OrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Seeds demo data on first start (skipped if any users already exist). Disabled
 * under the {@code prod} profile.
 */
@Component
@Profile("!prod")
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final UserRepository users;
    private final VendorRepository vendors;
    private final ProductRepository products;
    private final OrderService orderService;
    private final PasswordEncoder encoder;
    private final ServiceProviderProfileRepository serviceProfiles;
    private final ServiceOfferingRepository serviceOfferings;
    private final ServiceVariantRepository serviceVariants;
    private final AvailabilityRuleRepository availabilityRules;

    public DataSeeder(UserRepository users, VendorRepository vendors, ProductRepository products,
                      OrderService orderService, PasswordEncoder encoder,
                      ServiceProviderProfileRepository serviceProfiles, ServiceOfferingRepository serviceOfferings,
                      ServiceVariantRepository serviceVariants, AvailabilityRuleRepository availabilityRules) {
        this.users = users;
        this.vendors = vendors;
        this.products = products;
        this.orderService = orderService;
        this.encoder = encoder;
        this.serviceProfiles = serviceProfiles;
        this.serviceOfferings = serviceOfferings;
        this.serviceVariants = serviceVariants;
        this.availabilityRules = availabilityRules;
    }

    @Override
    public void run(String... args) {
        // Seed demo vendors/products/orders only once. The admin user is created
        // by Liquibase changeset 002-seed-admin (so it also exists in prod), not here.
        if (vendors.count() > 0) {
            return;
        }
        log.info("Seeding demo data...");

        User customer = createUser("customer@orderlynk.app", "customer12345", "Ada Customer", UserRole.CUSTOMER, false, null);

        // ----- Vendor 1: Mama T Foods (Winnipeg), approved + active -----
        User mamaT = createUser("mama@orderlynk.app", "vendor12345", "Teni Adewale", UserRole.VENDOR, false, null);
        Vendor mamaTFoods = createVendor("Mama T Foods", "mama-t-foods", mamaT.getId(),
                "Authentic Nigerian groceries, spices and frozen foods. Pickup in Winnipeg or shipped Canada-wide.",
                "Winnipeg", "Canada", "+12045551234", "@mamatfoods",
                Set.of(FulfillmentType.LOCAL_PICKUP, FulfillmentType.LOCAL_DELIVERY, FulfillmentType.DOMESTIC_SHIPPING),
                "4.8", 24);
        // Admin has enabled non-card (transfer) payments for this vendor — used by the demo order below.
        mamaTFoods.setAlternativePaymentsEnabled(true);
        vendors.save(mamaTFoods);
        mamaT.setVendorId(mamaTFoods.getId());
        users.save(mamaT);

        Product jollof = product(mamaTFoods.getId(), "Jollof Rice Spice Mix", ProductCategory.GROCERIES,
                "12.99", 40, FulfillmentType.LOCAL_PICKUP);
        jollof.setDiscountPercent(15);
        products.save(jollof);
        product(mamaTFoods.getId(), "Egusi (Ground Melon Seed) 1kg", ProductCategory.GROCERIES,
                "18.50", 25, FulfillmentType.LOCAL_PICKUP);
        product(mamaTFoods.getId(), "Palm Oil 2L", ProductCategory.GROCERIES,
                "22.00", 30, FulfillmentType.DOMESTIC_SHIPPING);
        product(mamaTFoods.getId(), "Frozen Suya Pack", ProductCategory.GROCERIES,
                "29.99", 15, FulfillmentType.LOCAL_DELIVERY);

        // ----- Vendor 2: Naija Beauty Hub (Toronto), approved + active -----
        User beautyOwner = createUser("beauty@orderlynk.app", "vendor12345", "Chioma Okafor", UserRole.VENDOR, false, null);
        Vendor beauty = createVendor("Naija Beauty Hub", "naija-beauty-hub", beautyOwner.getId(),
                "Shea butter, black soap and skincare sourced from Nigeria. Import batches into Canada.",
                "Toronto", "Canada", "+14165559876", "@naijabeautyhub",
                Set.of(FulfillmentType.LOCAL_PICKUP, FulfillmentType.IMPORT_BATCH, FulfillmentType.DOMESTIC_SHIPPING),
                "4.9", 37);
        beautyOwner.setVendorId(beauty.getId());
        beautyOwner.setEmailVerified(true); // demo: let this vendor sign in to manage services
        users.save(beautyOwner);

        // Bookable services (braiding) with priced sub-services + open availability, so the booking flow is demoable.
        seedBraidingServices(beauty.getId());

        product(beauty.getId(), "Raw Shea Butter 500g", ProductCategory.BEAUTY,
                "15.00", 50, FulfillmentType.DOMESTIC_SHIPPING);
        product(beauty.getId(), "African Black Soap", ProductCategory.BEAUTY,
                "9.99", 60, FulfillmentType.LOCAL_PICKUP);
        product(beauty.getId(), "Hibiscus Hair Oil", ProductCategory.BEAUTY,
                "24.50", 20, FulfillmentType.IMPORT_BATCH);

        // ----- Vendor 3: Lagos Pantry (Calgary), pending approval -----
        User pending = createUser("pending@orderlynk.app", "vendor12345", "Bola Hassan", UserRole.VENDOR, false, null);
        Vendor lagos = new Vendor();
        lagos.setBusinessName("Lagos Pantry");
        lagos.setStoreSlug("lagos-pantry");
        lagos.setOwnerUserId(pending.getId());
        lagos.setDescription("Snacks and pantry staples. Awaiting approval.");
        lagos.getAddress().setCity("Calgary");
        lagos.getAddress().setCountry("Canada");
        lagos.getFulfillmentTypes().add(FulfillmentType.LOCAL_PICKUP);
        lagos.setVerificationStatus(VendorStatus.SUBMITTED);
        lagos.setActive(false);
        vendors.save(lagos);
        pending.setVendorId(lagos.getId());
        users.save(pending);

        // ----- A demo order against Mama T Foods -----
        CheckoutRequest demo = new CheckoutRequest(
                mamaTFoods.getId(),
                List.of(new CartLine(jollof.getId(), 2)),
                "Ada Customer", "+12045550000", "customer@orderlynk.app",
                "12", "Portage Avenue", "Winnipeg", "MB", "R3C 0B1", "Canada",
                FulfillmentType.LOCAL_PICKUP, PaymentMethod.INTERAC_ETRANSFER,
                SourceChannel.WHATSAPP, "june-batch", "Please pack carefully.", null);
        // Link the order to the customer account so they're a verified buyer (can rate Mama T Foods).
        orderService.checkout(demo, customer.getId());

        log.info("""
                Demo data ready. Logins:
                  Admin    -> admin@orderlynk.app / admin12345     (seeded by Liquibase migration)
                  Vendor   -> mama@orderlynk.app / vendor12345     (Mama T Foods, approved)
                  Vendor   -> beauty@orderlynk.app / vendor12345   (Naija Beauty Hub, approved)
                  Vendor   -> pending@orderlynk.app / vendor12345  (Lagos Pantry, awaiting approval)
                  Customer -> customer@orderlynk.app / customer12345""");
    }

    /** Auto-approving braiding service with sub-services and 7-day availability (demo booking flow). */
    private void seedBraidingServices(UUID vendorId) {
        ServiceProviderProfile profile = new ServiceProviderProfile();
        profile.setVendorId(vendorId);
        profile.setServiceEnabled(true);
        profile.setApprovalMode(ApprovalMode.AUTO);
        profile.setLocationType(ServiceLocationType.AT_PROVIDER);
        profile.setLeadTimeHours(0);   // demo: show slots starting today
        profile.setBusinessHoursSummary("Mon–Sun, 9am–6pm");
        profile.setBio("Protective styling specialists — braids, weaves and cornrows.");
        serviceProfiles.save(profile);

        ServiceOffering braids = new ServiceOffering();
        braids.setVendorId(vendorId);
        braids.setName("Braiding");
        braids.setCategory(ServiceCategory.HAIR);
        braids.setDescription("Protective braiding styles. Pick your style below — price varies by style.");
        braids.setBasePrice(new BigDecimal("90.00"));
        braids.setDurationMinutes(120);
        braids.setDepositType(DepositType.NONE);
        braids.setLocationType(ServiceLocationType.AT_PROVIDER);
        braids.setActive(true);
        ServiceOffering savedBraids = serviceOfferings.save(braids);

        variant(savedBraids, "Braids with Gel", "80.00", 120);
        variant(savedBraids, "1 Million Braids", "120.00", 180);
        variant(savedBraids, "Knotless Braids", "150.00", 210);
        variant(savedBraids, "Weaving (Cornrows)", "90.00", 90);

        for (DayOfWeek day : DayOfWeek.values()) {
            AvailabilityRule rule = new AvailabilityRule();
            rule.setVendorId(vendorId);
            rule.setDayOfWeek(day);
            rule.setStartTime(LocalTime.of(9, 0));
            rule.setEndTime(LocalTime.of(18, 0));
            rule.setActive(true);
            availabilityRules.save(rule);
        }
    }

    private void variant(ServiceOffering service, String name, String price, int durationMinutes) {
        ServiceVariant v = new ServiceVariant();
        v.setServiceId(service.getId());
        v.setVendorId(service.getVendorId());
        v.setName(name);
        v.setPrice(new BigDecimal(price));
        v.setDurationMinutes(durationMinutes);
        v.setActive(true);
        serviceVariants.save(v);
    }

    private User createUser(String email, String password, String name, UserRole role, boolean admin, UUID vendorId) {
        User u = new User();
        u.setEmail(email);
        u.setPasswordHash(encoder.encode(password));
        u.setFullName(name);
        u.setRole(role);
        u.setAdmin(admin);
        u.setVendorId(vendorId);
        return users.save(u);
    }

    private Vendor createVendor(String name, String slug, UUID ownerId, String description, String city,
                                String country, String whatsapp, String instagram, Set<FulfillmentType> types,
                                String rating, int ratingCount) {
        Vendor v = new Vendor();
        v.setBusinessName(name);
        v.setStoreSlug(slug);
        v.setOwnerUserId(ownerId);
        v.setDescription(description);
        v.getAddress().setCity(city);
        v.getAddress().setCountry(country);
        v.setWhatsappNumber(whatsapp);
        v.setInstagramHandle(instagram);
        v.getFulfillmentTypes().addAll(types);
        v.setVerificationStatus(VendorStatus.APPROVED);
        v.setActive(true);
        v.setRating(new BigDecimal(rating));
        v.setRatingCount(ratingCount);
        return vendors.save(v);
    }

    private Product product(UUID vendorId, String name, ProductCategory category, String price, int qty,
                            FulfillmentType type) {
        Product p = new Product();
        p.setVendorId(vendorId);
        p.setName(name);
        p.setCategory(category);
        p.setPrice(new BigDecimal(price));
        p.setQuantityAvailable(qty);
        p.setFulfillmentType(type);
        p.setActive(true);
        p.setAvailableNow(true);
        return products.save(p);
    }
}
