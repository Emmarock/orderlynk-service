package com.myorderlynk.app.config;

import com.myorderlynk.app.domain.Product;
import com.myorderlynk.app.domain.User;
import com.myorderlynk.app.domain.Vendor;
import com.myorderlynk.app.domain.enums.FulfillmentType;
import com.myorderlynk.app.domain.enums.PaymentMethod;
import com.myorderlynk.app.domain.enums.ProductCategory;
import com.myorderlynk.app.domain.enums.SourceChannel;
import com.myorderlynk.app.domain.enums.UserRole;
import com.myorderlynk.app.domain.enums.VendorStatus;
import com.myorderlynk.app.dto.OrderDtos.CartLine;
import com.myorderlynk.app.dto.OrderDtos.CheckoutRequest;
import com.myorderlynk.app.repo.ProductRepository;
import com.myorderlynk.app.repo.UserRepository;
import com.myorderlynk.app.repo.VendorRepository;
import com.myorderlynk.app.service.OrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
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

    public DataSeeder(UserRepository users, VendorRepository vendors, ProductRepository products,
                      OrderService orderService, PasswordEncoder encoder) {
        this.users = users;
        this.vendors = vendors;
        this.products = products;
        this.orderService = orderService;
        this.encoder = encoder;
    }

    @Override
    public void run(String... args) {
        // Seed demo vendors/products/orders only once. The admin user is created
        // by Liquibase changeset 002-seed-admin (so it also exists in prod), not here.
        if (vendors.count() > 0) {
            return;
        }
        log.info("Seeding demo data...");

        createUser("customer@orderlynk.app", "customer12345", "Ada Customer", UserRole.CUSTOMER, false, null);

        // ----- Vendor 1: Mama T Foods (Winnipeg), approved + active -----
        User mamaT = createUser("mama@orderlynk.app", "vendor12345", "Teni Adewale", UserRole.VENDOR, false, null);
        Vendor mamaTFoods = createVendor("Mama T Foods", "mama-t-foods", mamaT.getId(),
                "Authentic Nigerian groceries, spices and frozen foods. Pickup in Winnipeg or shipped Canada-wide.",
                "Winnipeg", "Canada", "+12045551234", "@mamatfoods",
                Set.of(FulfillmentType.LOCAL_PICKUP, FulfillmentType.LOCAL_DELIVERY, FulfillmentType.DOMESTIC_SHIPPING));
        mamaT.setVendorId(mamaTFoods.getId());
        users.save(mamaT);

        Product jollof = product(mamaTFoods.getId(), "Jollof Rice Spice Mix", ProductCategory.GROCERIES,
                "12.99", 40, FulfillmentType.LOCAL_PICKUP);
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
                Set.of(FulfillmentType.LOCAL_PICKUP, FulfillmentType.IMPORT_BATCH, FulfillmentType.DOMESTIC_SHIPPING));
        beautyOwner.setVendorId(beauty.getId());
        users.save(beautyOwner);

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
        lagos.setCity("Calgary");
        lagos.setCountry("Canada");
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
                "Ada Customer", "+12045550000", "customer@orderlynk.app", "Winnipeg",
                FulfillmentType.LOCAL_PICKUP, PaymentMethod.INTERAC_ETRANSFER,
                SourceChannel.WHATSAPP, "june-batch", "Please pack carefully.");
        orderService.checkout(demo, null);

        log.info("""
                Demo data ready. Logins:
                  Admin    -> admin@orderlynk.app / admin12345     (seeded by Liquibase migration)
                  Vendor   -> mama@orderlynk.app / vendor12345     (Mama T Foods, approved)
                  Vendor   -> beauty@orderlynk.app / vendor12345   (Naija Beauty Hub, approved)
                  Vendor   -> pending@orderlynk.app / vendor12345  (Lagos Pantry, awaiting approval)
                  Customer -> customer@orderlynk.app / customer12345""");
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
                                String country, String whatsapp, String instagram, Set<FulfillmentType> types) {
        Vendor v = new Vendor();
        v.setBusinessName(name);
        v.setStoreSlug(slug);
        v.setOwnerUserId(ownerId);
        v.setDescription(description);
        v.setCity(city);
        v.setCountry(country);
        v.setWhatsappNumber(whatsapp);
        v.setInstagramHandle(instagram);
        v.getFulfillmentTypes().addAll(types);
        v.setVerificationStatus(VendorStatus.APPROVED);
        v.setActive(true);
        v.setRating(new BigDecimal("4.8"));
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
