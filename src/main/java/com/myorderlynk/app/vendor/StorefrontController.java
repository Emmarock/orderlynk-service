package com.myorderlynk.app.vendor;

import com.myorderlynk.app.common.enums.ProductCategory;
import com.myorderlynk.app.vendor.VendorDtos.RatingRequest;
import com.myorderlynk.app.vendor.VendorDtos.RatingSummary;
import com.myorderlynk.app.vendor.VendorDtos.StorefrontResponse;
import com.myorderlynk.app.vendor.VendorDtos.VendorResponse;
import com.myorderlynk.app.security.CurrentUser;
import com.myorderlynk.app.vendor.VendorRatingService;
import com.myorderlynk.app.vendor.VendorService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Public, unauthenticated storefront + marketplace discovery (PRD §13 pages). */
@RestController
@RequestMapping("/api/storefronts")
public class StorefrontController {

    private final VendorService vendorService;
    private final VendorRatingService ratingService;
    private final CurrentUser currentUser;

    public StorefrontController(VendorService vendorService, VendorRatingService ratingService,
                                CurrentUser currentUser) {
        this.vendorService = vendorService;
        this.ratingService = ratingService;
        this.currentUser = currentUser;
    }

    /** Marketplace: approved, active vendors, optionally filtered by city and/or product category. */
    @GetMapping
    public List<VendorResponse> marketplace(@RequestParam(required = false) String city,
                                            @RequestParam(required = false) ProductCategory category) {
        return vendorService.marketplace(city, category);
    }

    @GetMapping("/{slug}")
    public StorefrontResponse storefront(@PathVariable String slug) {
        return vendorService.storefront(slug);
    }

    /** Submit (or update) the signed-in customer's rating for a vendor. */
    @PostMapping("/{slug}/ratings")
    public RatingSummary rate(@PathVariable String slug, @Valid @RequestBody RatingRequest req) {
        return ratingService.rate(currentUser.require().userId(), slug, req.stars(), req.comment());
    }

    /** The signed-in customer's existing rating for a vendor (myStars is null if they haven't rated). */
    @GetMapping("/{slug}/ratings/mine")
    public RatingSummary myRating(@PathVariable String slug) {
        return ratingService.myRating(currentUser.require().userId(), slug);
    }
}