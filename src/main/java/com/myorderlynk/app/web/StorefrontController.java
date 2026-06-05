package com.myorderlynk.app.web;

import com.myorderlynk.app.dto.VendorDtos.StorefrontResponse;
import com.myorderlynk.app.dto.VendorDtos.VendorResponse;
import com.myorderlynk.app.service.VendorService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Public, unauthenticated storefront + marketplace discovery (PRD §13 pages). */
@RestController
@RequestMapping("/api/storefronts")
public class StorefrontController {

    private final VendorService vendorService;

    public StorefrontController(VendorService vendorService) {
        this.vendorService = vendorService;
    }

    /** Marketplace: approved, active vendors, optionally filtered by city. */
    @GetMapping
    public List<VendorResponse> marketplace(@RequestParam(required = false) String city) {
        return vendorService.marketplace(city);
    }

    @GetMapping("/{slug}")
    public StorefrontResponse storefront(@PathVariable String slug) {
        return vendorService.storefront(slug);
    }
}
