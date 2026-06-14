package com.myorderlynk.app.booking;

import com.myorderlynk.app.booking.BookingDtos.ProviderCard;
import com.myorderlynk.app.booking.BookingDtos.ReviewResponse;
import com.myorderlynk.app.booking.BookingDtos.ServiceStorefrontResponse;
import com.myorderlynk.app.booking.ServiceDtos.ServiceResponse;
import com.myorderlynk.app.vendor.Vendor;
import com.myorderlynk.app.common.PageResponse;
import com.myorderlynk.app.common.enums.VendorStatus;
import com.myorderlynk.app.exception.ApiException;
import com.myorderlynk.app.vendor.VendorRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Public, unauthenticated service discovery (PRD §12.1/§12.2): the Services marketplace
 * (provider cards with filters) and a provider's service storefront.
 */
@Service
public class ServiceDiscoveryService {

    private final VendorRepository vendors;
    private final ServiceOfferingRepository services;
    private final ServiceAddOnRepository addOns;
    private final ServiceProviderProfileRepository profiles;
    private final BookingReviewRepository reviews;
    private final BookingMapper mapper;

    public ServiceDiscoveryService(VendorRepository vendors, ServiceOfferingRepository services,
                                   ServiceAddOnRepository addOns, ServiceProviderProfileRepository profiles,
                                   BookingReviewRepository reviews, BookingMapper mapper) {
        this.vendors = vendors;
        this.services = services;
        this.addOns = addOns;
        this.profiles = profiles;
        this.reviews = reviews;
        this.mapper = mapper;
    }

    /** Service-provider cards for the marketplace, optionally filtered by category and/or city. */
    @Transactional(readOnly = true)
    public PageResponse<ProviderCard> marketplace(ServiceCategory category, String city, boolean acceptsDepositsOnly,
                                                  int page, int size) {
        List<UUID> vendorIds = category != null
                ? services.findVendorIdsByActiveCategory(category)
                : services.findVendorIdsWithActiveServices();

        List<ProviderCard> cards = vendorIds.stream()
                .map(vendors::findById)
                .filter(java.util.Optional::isPresent)
                .map(java.util.Optional::get)
                .filter(v -> v.isActive() && v.getVerificationStatus() == VendorStatus.APPROVED)
                .filter(v -> city == null || city.isBlank()
                        || (v.getAddress() != null && city.equalsIgnoreCase(v.getAddress().getCity())))
                .map(this::card)
                .filter(card -> !acceptsDepositsOnly || card.acceptsDeposits())
                .sorted(Comparator.comparing(
                        (ProviderCard c) -> c.rating() == null ? BigDecimal.ZERO : c.rating()).reversed())
                .toList();
        return PageResponse.of(cards, page, size);
    }

    /** A provider's full service storefront (profile + active services + reviews). */
    @Transactional(readOnly = true)
    public ServiceStorefrontResponse storefront(String slug) {
        Vendor v = vendors.findByStoreSlug(slug)
                .orElseThrow(() -> ApiException.notFound("Provider not found"));
        if (!v.isActive() || v.getVerificationStatus() != VendorStatus.APPROVED) {
            throw ApiException.notFound("Provider not found");
        }
        ServiceProviderProfile profile = profiles.findByVendorId(v.getId()).orElse(null);
        if (profile == null || !profile.isServiceEnabled()) {
            throw ApiException.notFound("This provider does not offer bookable services");
        }
        List<ServiceResponse> activeServices = services.findByVendorIdAndActiveTrue(v.getId()).stream()
                .map(s -> mapper.service(s, addOns.findByServiceIdAndActiveTrue(s.getId())))
                .toList();
        List<ReviewResponse> recentReviews = reviews.findByVendorIdAndVisibleTrueOrderByCreatedAtDesc(v.getId())
                .stream().limit(20).map(mapper::review).toList();

        return new ServiceStorefrontResponse(
                v.getId(), v.getBusinessName(), v.getStoreSlug(), v.getDescription(),
                v.getLogoUrl(), v.getBannerUrl(),
                v.getAddress() == null ? null : v.getAddress().getCity(),
                v.getWhatsappNumber(), v.getInstagramHandle(),
                mapper.profile(profile),
                rating(v.getId()), (int) reviews.countByVendorIdAndVisibleTrue(v.getId()),
                activeServices, recentReviews);
    }

    private ProviderCard card(Vendor v) {
        List<ServiceOffering> active = services.findByVendorIdAndActiveTrue(v.getId());
        BigDecimal startingPrice = active.stream()
                .map(ServiceOffering::getBasePrice)
                .min(Comparator.naturalOrder())
                .orElse(null);
        List<ServiceCategory> categories = active.stream()
                .map(ServiceOffering::getCategory)
                .distinct()
                .toList();
        boolean acceptsDeposits = active.stream().anyMatch(s -> s.getDepositType() != DepositType.NONE);
        String currency = active.stream().map(ServiceOffering::getCurrency).findFirst().orElse("CAD");
        ServiceProviderProfile profile = profiles.findByVendorId(v.getId()).orElse(null);
        return new ProviderCard(
                v.getId(), v.getBusinessName(), v.getStoreSlug(), v.getLogoUrl(), v.getBannerUrl(),
                v.getAddress() == null ? null : v.getAddress().getCity(),
                profile == null ? null : profile.getServiceArea(),
                profile == null ? null : profile.getLocationType(),
                rating(v.getId()), (int) reviews.countByVendorIdAndVisibleTrue(v.getId()),
                startingPrice, currency, categories, acceptsDeposits);
    }

    private BigDecimal rating(UUID vendorId) {
        double avg = reviews.averageRating(vendorId);
        return avg <= 0 ? null : BigDecimal.valueOf(avg).setScale(2, RoundingMode.HALF_UP);
    }
}
