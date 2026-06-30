package com.myorderlynk.app.order;

import com.myorderlynk.app.order.FeeSettingsDtos.FeeSettingsResponse;
import com.myorderlynk.app.order.FeeSettingsDtos.UpdateFeeSettingsRequest;
import com.myorderlynk.app.security.access.IsAdmin;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin console for the platform-wide fee policy (service fee, processing, logistics markup, tax,
 * per-fulfillment logistics fees). Changes take effect immediately for new quotes and checkouts.
 */
@RestController
@RequestMapping("/api/admin/fee-settings")
@IsAdmin
public class AdminFeeSettingsController {

    private final FeeSettingsService service;

    public AdminFeeSettingsController(FeeSettingsService service) {
        this.service = service;
    }

    @GetMapping
    public FeeSettingsResponse get() {
        return FeeSettingsDtos.toResponse(service.current());
    }

    @PutMapping
    public FeeSettingsResponse update(@Valid @RequestBody UpdateFeeSettingsRequest req) {
        return FeeSettingsDtos.toResponse(service.update(req));
    }
}