package com.myorderlynk.app.vendor;
import com.myorderlynk.app.batch.BatchDtos;
import com.myorderlynk.app.booking.ServiceDtos;
import com.myorderlynk.app.catalog.ProductDtos;

import com.myorderlynk.app.common.enums.FulfillmentType;
import com.myorderlynk.app.common.enums.VendorStatus;
import com.myorderlynk.app.validation.FieldMatch;
import com.myorderlynk.app.validation.StrongPassword;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class VendorDtos {

    private VendorDtos() {
    }

    /**
     * One-step seller signup for a brand-new (unauthenticated) user: creates the user account
     * and the vendor together. Carries its own password + confirmation; the account fields mirror
     * {@link com.myorderlynk.app.identity.AuthDtos.RegisterRequest}.
     */
    @FieldMatch(field = "password", fieldMatch = "confirmPassword",
            message = "Password and confirmation do not match")
    public record SellerRegistrationRequest(
            @NotBlank String fullName,
            @Email @NotBlank String email,
            @StrongPassword String password,
            @NotBlank String confirmPassword,
            String phone,
            @NotBlank String businessName,
            String description,
            String houseNumber,
            String street,
            String city,
            String state,
            String postcode,
            String country,
            String whatsappNumber,
            String instagramHandle,
            Set<FulfillmentType> fulfillmentTypes) {
    }

    /** Submitted by a signed-in user to apply as a vendor (PRD §14 Vendor Signup). */
    public record VendorApplicationRequest(
            @NotBlank String businessName,
            String description,
            String houseNumber,
            String street,
            String city,
            String state,
            String postcode,
            String country,
            String whatsappNumber,
            String instagramHandle,
            @Pattern(regexp = "^@?[A-Za-z0-9._]+$", message = "Invalid TikTok handle") String tiktokHandle,
            @Pattern(regexp = "^(https://)?(www\\.)?facebook\\.com/.+$", message = "Invalid Facebook URL") String facebookPage,
            String logoUrl,
            Set<FulfillmentType> fulfillmentTypes) {
    }

    public record VendorUpdateRequest(
            String businessName,
            String description,
            String houseNumber,
            String street,
            String city,
            String state,
            String postcode,
            String country,
            String whatsappNumber,
            String instagramHandle,
            String tiktokHandle,
            String facebookPage,
            String logoUrl,
            String bannerUrl,
            Set<FulfillmentType> fulfillmentTypes,
            String payoutMethod,
            String payoutAccountName,
            String payoutAccountNumber,
            String payoutBankName,
            String payoutEmail,
            String payoutCurrency,
            String payoutSortCode,
            String payoutRoutingNumber,
            String payoutInstitutionNumber,
            String payoutTransitNumber,
            String payoutIban,
            String payoutBic,
            String payoutBankCode,
            Boolean notifyByEmail,
            Boolean notifyByWhatsapp,
            Boolean lowStockAlerts) {
    }

    public record VendorResponse(
            UUID id,
            String businessName,
            String description,
            String houseNumber,
            String street,
            String city,
            String state,
            String postcode,
            String country,
            String whatsappNumber,
            String instagramHandle,
            String tiktokHandle,
            String facebookPage,
            String logoUrl,
            String bannerUrl,
            String storeSlug,
            VendorStatus verificationStatus,
            Set<FulfillmentType> fulfillmentTypes,
            boolean active,
            BigDecimal rating,
            int ratingCount,
            BigDecimal commissionRate,
            String payoutMethod,
            String payoutAccountName,
            String payoutAccountNumber,
            String payoutBankName,
            String payoutEmail,
            String payoutCurrency,
            String payoutSortCode,
            String payoutRoutingNumber,
            String payoutInstitutionNumber,
            String payoutTransitNumber,
            String payoutIban,
            String payoutBic,
            String payoutBankCode,
            boolean notifyByEmail,
            boolean notifyByWhatsapp,
            boolean lowStockAlerts,
            boolean alternativePaymentsEnabled) {
    }

    /** Submitted by a customer to rate a vendor (1–5 stars). */
    public record RatingRequest(
            @Min(1) @Max(5) int stars,
            @Size(max = 1000) String comment) {
    }

    /** A vendor's rating summary, plus the requesting customer's own stars when known. */
    public record RatingSummary(
            BigDecimal rating,
            int ratingCount,
            Integer myStars) {
    }

    /**
     * Public storefront view: the vendor profile plus everything it offers — active products,
     * bookable services, and published batch/cargo. Services and batches are empty lists when the
     * vendor doesn't offer them, so a single vendor link renders all three sections uniformly.
     */
    public record StorefrontResponse(
            VendorResponse vendor,
            List<ProductDtos.ProductResponse> products,
            List<ServiceDtos.ServiceResponse> services,
            List<BatchDtos.BatchCard> batches) {
    }

    /** Returned after a user applies as a vendor: a refreshed token carrying the VENDOR role. */
    public record ApplyResponse(
            String token,
            VendorResponse vendor) {
    }

    public record ShareLinkResponse(
            String url,
            String whatsappShareUrl) {
    }
}
