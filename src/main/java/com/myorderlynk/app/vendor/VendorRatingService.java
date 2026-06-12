package com.myorderlynk.app.vendor;

import com.myorderlynk.app.vendor.Vendor;
import com.myorderlynk.app.vendor.VendorRating;
import com.myorderlynk.app.common.enums.VendorStatus;
import com.myorderlynk.app.vendor.VendorDtos.RatingSummary;
import com.myorderlynk.app.exception.ApiException;
import com.myorderlynk.app.order.OrderRepository;
import com.myorderlynk.app.vendor.VendorRatingRepository;
import com.myorderlynk.app.vendor.VendorRatingRepository.RatingAggregate;
import com.myorderlynk.app.vendor.VendorRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

/**
 * Customer ratings of vendors. Each (vendor, customer) pair has at most one
 * rating; submitting again updates it. The vendor's denormalized average rating
 * and count are recomputed on every change so the marketplace can sort by them
 * without aggregating per request.
 */
@Service
@Slf4j
public class VendorRatingService {

    private final VendorRepository vendors;
    private final VendorRatingRepository ratings;
    private final OrderRepository orders;
    private final boolean requirePurchase;

    public VendorRatingService(VendorRepository vendors, VendorRatingRepository ratings, OrderRepository orders,
                               @Value("${app.ratings.require-purchase:true}") boolean requirePurchase) {
        this.vendors = vendors;
        this.ratings = ratings;
        this.orders = orders;
        this.requirePurchase = requirePurchase;
    }

    @Transactional
    public RatingSummary rate(UUID customerUserId, String slug, int stars, String comment) {
        Vendor vendor = visibleVendor(slug);
        if (requirePurchase && !orders.existsByCustomerUserIdAndVendorId(customerUserId, vendor.getId())) {
            throw ApiException.forbidden("You can only rate vendors you have ordered from");
        }
        VendorRating rating = ratings.findByVendorIdAndCustomerUserId(vendor.getId(), customerUserId)
                .orElseGet(VendorRating::new);
        rating.setVendorId(vendor.getId());
        rating.setCustomerUserId(customerUserId);
        rating.setStars(stars);
        rating.setComment(comment == null || comment.isBlank() ? null : comment.trim());
        ratings.save(rating);

        recompute(vendor);
        log.info("Rating saved: vendor={} customer={} stars={} -> avg={} count={}",
                vendor.getId(), customerUserId, stars, vendor.getRating(), vendor.getRatingCount());
        return new RatingSummary(vendor.getRating(), vendor.getRatingCount(), stars);
    }

    @Transactional(readOnly = true)
    public RatingSummary myRating(UUID customerUserId, String slug) {
        Vendor vendor = visibleVendor(slug);
        Integer myStars = ratings.findByVendorIdAndCustomerUserId(vendor.getId(), customerUserId)
                .map(VendorRating::getStars)
                .orElse(null);
        return new RatingSummary(vendor.getRating(), vendor.getRatingCount(), myStars);
    }

    private void recompute(Vendor vendor) {
        RatingAggregate agg = ratings.aggregateForVendor(vendor.getId());
        long count = agg.getCount();
        BigDecimal average = count == 0 || agg.getAverage() == null
                ? null
                : BigDecimal.valueOf(agg.getAverage()).setScale(2, RoundingMode.HALF_UP);
        vendor.setRating(average);
        vendor.setRatingCount((int) count);
        vendors.save(vendor);
    }

    private Vendor visibleVendor(String slug) {
        Vendor vendor = vendors.findByStoreSlug(slug)
                .orElseThrow(() -> ApiException.notFound("Store not found"));
        if (!vendor.isActive() || vendor.getVerificationStatus() != VendorStatus.APPROVED) {
            throw ApiException.notFound("Store not found");
        }
        return vendor;
    }
}