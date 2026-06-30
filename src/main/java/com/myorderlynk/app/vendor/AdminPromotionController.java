package com.myorderlynk.app.vendor;

import com.myorderlynk.app.common.PageRequests;
import com.myorderlynk.app.common.PageResponse;
import com.myorderlynk.app.security.access.IsAdmin;
import com.myorderlynk.app.vendor.FeaturedPlacementDtos.PlacementResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Admin oversight of featured-placement purchases (the promotions ledger). */
@RestController
@RequestMapping("/api/admin/promotions")
@IsAdmin
public class AdminPromotionController {

    private final FeaturedPlacementService featured;
    private final FeaturedPlacementRepository placements;

    public AdminPromotionController(FeaturedPlacementService featured, FeaturedPlacementRepository placements) {
        this.featured = featured;
        this.placements = placements;
    }

    @GetMapping("/featured")
    public PageResponse<PlacementResponse> list(@RequestParam(required = false) SubscriptionInvoiceStatus status,
                                                @RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "20") int size) {
        var pageable = PageRequests.of(page, size);
        var result = status == null
                ? placements.findAllByOrderByCreatedAtDesc(pageable)
                : placements.findByStatus(status, pageable);
        return PageResponse.of(result.map(FeaturedPlacementDtos::toResponse));
    }

    @GetMapping("/featured/vendor/{vendorId}")
    public List<PlacementResponse> vendorPlacements(@PathVariable UUID vendorId) {
        return featured.forVendor(vendorId).stream().map(FeaturedPlacementDtos::toResponse).toList();
    }

    @PostMapping("/featured/{id}/mark-paid")
    public PlacementResponse markPaid(@PathVariable UUID id, @RequestParam(required = false) String reference) {
        return FeaturedPlacementDtos.toResponse(featured.markPaid(id, reference));
    }

    @PostMapping("/featured/{id}/waive")
    public PlacementResponse waive(@PathVariable UUID id) {
        return FeaturedPlacementDtos.toResponse(featured.waive(id));
    }
}
