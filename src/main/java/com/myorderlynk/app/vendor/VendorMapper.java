package com.myorderlynk.app.vendor;

import com.myorderlynk.app.common.Address;
import org.springframework.stereotype.Component;

/** Maps {@link Vendor} entities to API response records. */
@Component
public class VendorMapper {

    /** Hibernate maps an all-null @Embedded address to null on read; treat that as an empty address. */
    private static Address orEmpty(Address a) {
        return a == null ? new Address() : a;
    }

    /** Full vendor view, including private payout details — for the vendor's own console and admins. */
    public VendorDtos.VendorResponse vendor(Vendor v) {
        Address a = orEmpty(v.getAddress());
        return new VendorDtos.VendorResponse(
                v.getId(), v.getBusinessName(), v.getDescription(),
                a.getHouseNumber(), a.getStreet(), a.getCity(), a.getState(), a.getPostcode(), a.getCountry(),
                v.getWhatsappNumber(), v.getInstagramHandle(), v.getTiktokHandle(), v.getFacebookPage(), v.getLogoUrl(), v.getBannerUrl(), v.getStoreSlug(),
                v.getVerificationStatus(), v.getFulfillmentTypes(), v.isActive(), v.getRating(),
                v.getRatingCount(), v.getPlan(), v.getCommissionRate(), v.getVatCollector(),
                v.getPayoutMethod(), v.getPayoutAccountName(), v.getPayoutAccountNumber(),
                v.getPayoutBankName(), v.getPayoutEmail(),
                v.getPayoutCurrency(), v.getPayoutSortCode(), v.getPayoutRoutingNumber(),
                v.getPayoutInstitutionNumber(), v.getPayoutTransitNumber(), v.getPayoutIban(),
                v.getPayoutBic(), v.getPayoutBankCode(),
                v.isNotifyByEmail(), v.isNotifyByWhatsapp(), v.isLowStockAlerts(),
                v.isAlternativePaymentsEnabled(), v.isChatOrderEnabled(), v.isFeatured());
    }

    /**
     * Public vendor view for marketplace/storefront — omits private payout details
     * (account numbers, e-transfer email) so they never leak to anonymous browsers.
     */
    public VendorDtos.VendorResponse publicVendor(Vendor v) {
        Address a = orEmpty(v.getAddress());
        return new VendorDtos.VendorResponse(
                v.getId(), v.getBusinessName(), v.getDescription(),
                a.getHouseNumber(), a.getStreet(), a.getCity(), a.getState(), a.getPostcode(), a.getCountry(),
                v.getWhatsappNumber(), v.getInstagramHandle(), v.getTiktokHandle(), v.getFacebookPage(), v.getLogoUrl(), v.getBannerUrl(), v.getStoreSlug(),
                v.getVerificationStatus(), v.getFulfillmentTypes(), v.isActive(), v.getRating(),
                v.getRatingCount(), v.getPlan(), v.getCommissionRate(), v.getVatCollector(),
                null, null, null, null, null,
                null, null, null, null, null, null, null, null,
                v.isNotifyByEmail(), v.isNotifyByWhatsapp(), v.isLowStockAlerts(),
                v.isAlternativePaymentsEnabled(), v.isChatOrderEnabled(), v.isFeatured());
    }
}
