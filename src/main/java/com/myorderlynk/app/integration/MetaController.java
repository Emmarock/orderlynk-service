package com.myorderlynk.app.integration;
import com.myorderlynk.app.common.Address;

import com.myorderlynk.app.common.enums.FulfillmentStatus;
import com.myorderlynk.app.common.enums.FulfillmentType;
import com.myorderlynk.app.common.enums.PaymentMethod;
import com.myorderlynk.app.common.enums.PaymentStatus;
import com.myorderlynk.app.common.enums.ProductCategory;
import com.myorderlynk.app.common.enums.SourceChannel;
import com.myorderlynk.app.identity.AddressDtos.AddressSuggestion;
import com.myorderlynk.app.exception.ApiException;
import com.myorderlynk.app.order.FulfillmentFlows;
import com.myorderlynk.app.integration.GeoapifyService;
import com.myorderlynk.app.common.RateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Exposes option sets and fulfillment flows so the frontend never hard-codes enums. */
@RestController
@RequestMapping("/api/meta")
public class MetaController {

    /** Autocomplete is public + costs Geoapify quota, so cap lookups per client IP. */
    private static final int AUTOCOMPLETE_MAX_PER_WINDOW = 60;
    private static final Duration AUTOCOMPLETE_WINDOW = Duration.ofMinutes(1);

    private final GeoapifyService geoapify;
    private final RateLimiter rateLimiter;

    public MetaController(GeoapifyService geoapify, RateLimiter rateLimiter) {
        this.geoapify = geoapify;
        this.rateLimiter = rateLimiter;
    }

    /** Address autocomplete (proxied to Geoapify so the key stays server-side); empty list when unconfigured. */
    @GetMapping("/address/autocomplete")
    public List<AddressSuggestion> addressAutocomplete(@RequestParam("text") String text,
                                                       @RequestParam(value = "country", required = false) String country,
                                                       HttpServletRequest request) {
        String key = "geoapify-autocomplete:" + clientIp(request);
        if (!rateLimiter.tryAcquire(key, AUTOCOMPLETE_MAX_PER_WINDOW, AUTOCOMPLETE_WINDOW)) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "Too many address lookups — please slow down");
        }
        return geoapify.autocomplete(text, country);
    }

    /** Real client IP, preferring the first X-Forwarded-For hop when behind a proxy/load balancer. */
    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    @GetMapping("/option-sets")
    public Map<String, Object> optionSets() {
        Map<String, Object> sets = new LinkedHashMap<>();
        sets.put("fulfillmentTypes", names(FulfillmentType.values()));
        sets.put("fulfillmentStatuses", names(FulfillmentStatus.values()));
        sets.put("paymentStatuses", names(PaymentStatus.values()));
        sets.put("paymentMethods", names(PaymentMethod.values()));
        sets.put("productCategories", names(ProductCategory.values()));
        sets.put("sourceChannels", names(SourceChannel.values()));
        return sets;
    }

    @GetMapping("/fulfillment-flows")
    public Map<FulfillmentType, Object> fulfillmentFlows() {
        return Arrays.stream(FulfillmentType.values())
                .collect(Collectors.toMap(t -> t,
                        t -> FulfillmentFlows.flowFor(t).stream().map(Enum::name).toList(),
                        (a, b) -> a, () -> new LinkedHashMap<>()));
    }

    private static String[] names(Enum<?>[] values) {
        return Arrays.stream(values).map(Enum::name).toArray(String[]::new);
    }
}
