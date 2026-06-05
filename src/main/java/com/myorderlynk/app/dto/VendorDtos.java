package com.myorderlynk.app.dto;

import com.myorderlynk.app.domain.enums.FulfillmentType;
import com.myorderlynk.app.domain.enums.VendorStatus;
import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class VendorDtos {

    private VendorDtos() {
    }

    /** Submitted by a signed-in user to apply as a vendor (PRD §14 Vendor Signup). */
    public record VendorApplicationRequest(
            @NotBlank String businessName,
            String description,
            String city,
            String country,
            String whatsappNumber,
            String instagramHandle,
            String logoUrl,
            Set<FulfillmentType> fulfillmentTypes) {
    }

    public record VendorUpdateRequest(
            String description,
            String city,
            String country,
            String whatsappNumber,
            String instagramHandle,
            String logoUrl,
            Set<FulfillmentType> fulfillmentTypes) {
    }

    public record VendorResponse(
            UUID id,
            String businessName,
            String description,
            String city,
            String country,
            String whatsappNumber,
            String instagramHandle,
            String logoUrl,
            String storeSlug,
            VendorStatus verificationStatus,
            Set<FulfillmentType> fulfillmentTypes,
            boolean active,
            BigDecimal rating,
            BigDecimal commissionRate) {
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
