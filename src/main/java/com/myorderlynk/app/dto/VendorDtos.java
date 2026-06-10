package com.myorderlynk.app.dto;

import com.myorderlynk.app.domain.enums.FulfillmentType;
import com.myorderlynk.app.domain.enums.VendorStatus;
import com.myorderlynk.app.validation.FieldMatch;
import com.myorderlynk.app.validation.StrongPassword;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

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
     * {@link com.myorderlynk.app.dto.AuthDtos.RegisterRequest}.
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
            String logoUrl,
            String bannerUrl,
            Set<FulfillmentType> fulfillmentTypes,
            String payoutMethod,
            String payoutAccountName,
            String payoutAccountNumber,
            String payoutBankName,
            String payoutEmail,
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
            boolean notifyByEmail,
            boolean notifyByWhatsapp,
            boolean lowStockAlerts) {
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

    /** Public storefront view: vendor profile + its active products. */
    public record StorefrontResponse(
            VendorResponse vendor,
            List<ProductDtos.ProductResponse> products) {
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
