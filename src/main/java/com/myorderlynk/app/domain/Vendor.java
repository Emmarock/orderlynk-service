package com.myorderlynk.app.domain;

import com.myorderlynk.app.domain.enums.FulfillmentType;
import com.myorderlynk.app.domain.enums.VendorStatus;
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

    private String instagramHandle;

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

    /** Platform commission rate applied to this vendor's product subtotal (e.g. 0.07 = 7%). */
    @Column(nullable = false)
    private BigDecimal commissionRate = new BigDecimal("0.07");

    // ---- Payout / settlement details (Settings → Payment/payout information) ----

    /** How the vendor is paid out, e.g. INTERAC, BANK_TRANSFER. */
    private String payoutMethod;

    private String payoutAccountName;

    private String payoutAccountNumber;

    private String payoutBankName;

    /** Email for Interac e-Transfer payouts. */
    private String payoutEmail;

    // ---- Notification preferences (Settings → Notification preferences) ----

    @Column(nullable = false)
    private boolean notifyByEmail = true;

    @Column(nullable = false)
    private boolean notifyByWhatsapp = false;

    @Column(nullable = false)
    private boolean lowStockAlerts = true;
}
