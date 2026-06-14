package com.myorderlynk.app.booking;

import com.myorderlynk.app.booking.BookingDtos.ProviderCard;
import com.myorderlynk.app.booking.BookingDtos.ServiceStorefrontResponse;
import com.myorderlynk.app.common.PageResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public Services marketplace + provider service storefront (PRD §12.1/§12.2). Unauthenticated,
 * mirroring the product {@code StorefrontController}.
 */
@RestController
@RequestMapping("/api/services")
public class ServiceMarketplaceController {

    private final ServiceDiscoveryService discovery;

    public ServiceMarketplaceController(ServiceDiscoveryService discovery) {
        this.discovery = discovery;
    }

    /** Service-provider cards, filterable by category, city and deposit acceptance. */
    @GetMapping
    public PageResponse<ProviderCard> marketplace(@RequestParam(required = false) ServiceCategory category,
                                                  @RequestParam(required = false) String city,
                                                  @RequestParam(required = false, defaultValue = "false") boolean acceptsDeposits,
                                                  @RequestParam(defaultValue = "0") int page,
                                                  @RequestParam(defaultValue = "20") int size) {
        return discovery.marketplace(category, city, acceptsDeposits, page, size);
    }

    /** A provider's service storefront by store slug. */
    @GetMapping("/{slug}")
    public ServiceStorefrontResponse storefront(@PathVariable String slug) {
        return discovery.storefront(slug);
    }
}
