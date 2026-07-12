package com.myorderlynk.app.vendor;
import com.myorderlynk.app.common.Address;
import com.myorderlynk.app.common.BaseEntity;

import com.myorderlynk.app.common.enums.FulfillmentType;
import com.myorderlynk.app.common.enums.VatCollector;
import com.myorderlynk.app.common.enums.VendorPlan;
import com.myorderlynk.app.common.enums.VendorStatus;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "vendors", indexes = @Index(name = "idx_vendor_slug", columnList = "storeSlug", unique = true))
public class Vendor extends BaseEntity {

    @Column(nullable = false)
    private String businessName;

    /** User id of the vendor owner. */
    private UUID ownerUserId;

    @Column(length = 2000)
    private String description;

    /** Single business/pickup address. City &amp; country reuse the original vendor columns. */
    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "houseNumber", column = @Column(name = "address_house_number")),
            @AttributeOverride(name = "street", column = @Column(name = "address_street")),
            @AttributeOverride(name = "city", column = @Column(name = "city")),
            @AttributeOverride(name = "state", column = @Column(name = "address_state")),
            @AttributeOverride(name = "postcode", column = @Column(name = "address_postcode")),
            @AttributeOverride(name = "country", column = @Column(name = "country"))
    })
    private Address address = new Address();

    private String whatsappNumber;

    /**
     * Whether the vendor has proven ownership of {@link #whatsappNumber} by entering the one-time code
     * we message them. Required (alongside the owner's email verification) before an admin can approve.
     * Reset to false whenever the WhatsApp number changes.
     */
    @Column(nullable = false)
    private boolean whatsappVerified = false;

    /** The current one-time WhatsApp verification code (null once verified/expired). */
    @Column(length = 12)
    private String whatsappVerifyCode;

    /** When the current {@link #whatsappVerifyCode} stops being valid. */
    private java.time.Instant whatsappVerifyExpiresAt;

    /** Failed attempts against the current code; a small cap prevents brute-forcing it. */
    @Column(nullable = false)
    private int whatsappVerifyAttempts = 0;

    private String instagramHandle;
    private String tiktokHandle;
    private String facebookPage;

    private String logoUrl;

    /** Wide cover/banner image shown at the top of the storefront and on marketplace cards. */
    private String bannerUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VendorStatus verificationStatus = VendorStatus.SUBMITTED;

    @Column(nullable = false, unique = true)
    private String storeSlug;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "vendor_fulfillment_types", joinColumns = @JoinColumn(name = "vendor_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "fulfillment_type")
    private Set<FulfillmentType> fulfillmentTypes = new HashSet<>();

    @Column(nullable = false)
    private boolean active = false;

    /** Denormalized average of all {@link VendorRating} stars, recomputed on each new rating. */
    private BigDecimal rating;

    /** Number of customer ratings backing {@link #rating}. */
    @Column(nullable = false)
    private int ratingCount = 0;

    /**
     * Subscription tier. Default {@code STARTER}; existing vendors are grandfathered onto STARTER at
     * their legacy {@link #commissionRate} until an admin assigns them a plan (which then materializes
     * the catalog's rate). Drives the monthly subscription fee.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private VendorPlan plan = VendorPlan.STARTER;

    /**
     * Paid featured-placement boost: while this is in the future, the vendor is promoted to the top of
     * marketplace discovery. Null/past = not featured. Extended by a featured-placement purchase.
     */
    private java.time.Instant featuredUntil;

    /** Whether the vendor's featured-placement boost is currently active. */
    public boolean isFeatured() {
        return featuredUntil != null && featuredUntil.isAfter(java.time.Instant.now());
    }

    /**
     * Platform commission rate applied to this vendor's product subtotal (e.g. 0.07 = 7%). This is
     * the <em>effective</em> rate used in all fee math; assigning a {@link #plan} overwrites it from
     * the plan catalog, but an admin may also set a custom rate for a bespoke deal.
     */
    @Column(nullable = false)
    private BigDecimal commissionRate = new BigDecimal("0.07");

    /**
     * Who collects VAT on behalf of the government for this vendor's sales. Chosen at registration;
     * defaults to {@link VatCollector#VENDOR} (the vendor receives the VAT in its payout and remits
     * it). {@link VatCollector#PLATFORM} means the platform holds and remits it. Editable in settings.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private VatCollector vatCollector = VatCollector.VENDOR;

    // ---- Payout / settlement details (Settings → Payment/payout information) ----

    /** How the vendor is paid out, e.g. INTERAC, BANK_TRANSFER. */
    private String payoutMethod;

    private String payoutAccountName;

    private String payoutAccountNumber;

    private String payoutBankName;

    /** Email for Interac e-Transfer payouts. */
    private String payoutEmail;

    /**
     * Settlement currency for manual bank-transfer payments (NGN, USD, CAD, GBP, EUR). Drives which
     * of the country-specific fields below are populated/shown. Card payouts go through Stripe Connect,
     * which collects its own bank details; these fields are for the manual/alternative-payment path.
     */
    private String payoutCurrency;

    /** GBP — 6-digit sort code. */
    private String payoutSortCode;

    /** USD — 9-digit ACH routing number. */
    private String payoutRoutingNumber;

    /** CAD — 3-digit institution number. */
    private String payoutInstitutionNumber;

    /** CAD — 5-digit branch transit number. */
    private String payoutTransitNumber;

    /** EUR — IBAN. */
    private String payoutIban;

    /** EUR / international — BIC / SWIFT code (8 or 11 chars). */
    private String payoutBic;

    /** NGN — optional CBN bank code, alongside the bank name. */
    private String payoutBankCode;

    // ---- Notification preferences (Settings → Notification preferences) ----

    @Column(nullable = false)
    private boolean notifyByEmail = true;

    @Column(nullable = false)
    private boolean notifyByWhatsapp = false;

    @Column(nullable = false)
    private boolean lowStockAlerts = true;

    /**
     * Whether this vendor may accept non-card payment methods (bank transfer, e-transfer, cash) in
     * addition to card. Off by default; only an admin can enable it. When false the vendor is
     * card-only across orders, service bookings and batch/cargo.
     */
    @Column(nullable = false)
    private boolean alternativePaymentsEnabled = false;

    /**
     * Whether this vendor may use chat-order import (paste a WhatsApp/Instagram thread → structured
     * draft order). Off by default; only an admin can enable it per vendor.
     */
    @Column(nullable = false)
    private boolean chatOrderEnabled = false;
}
