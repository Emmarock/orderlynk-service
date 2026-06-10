package com.myorderlynk.app.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.myorderlynk.app.dto.AddressDtos.AddressSuggestion;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Server-side proxy for Geoapify address autocomplete. Keeps the API key off the client and
 * degrades gracefully: when unconfigured or on any upstream failure it returns an empty list so the
 * forms simply fall back to manual entry rather than breaking. See <a
 * href="https://apidocs.geoapify.com/docs/geocoding/address-autocomplete/">Geoapify autocomplete</a>.
 */
@Service
@Slf4j
public class GeoapifyService {

    /** Geoapify charges per request, so cap how many suggestions we ever ask for. */
    private static final int MAX_RESULTS = 6;
    /** Below this length autocomplete is noise; skip the call to save quota. */
    private static final int MIN_QUERY_LENGTH = 3;

    /** English country name (lower-cased) → ISO 3166-1 alpha-2 code, so forms can pass "Canada". */
    private static final Map<String, String> COUNTRY_NAME_TO_CODE = buildCountryIndex();

    private final RestClient client;
    private final String apiKey;
    private final boolean configured;

    public GeoapifyService(
            @Value("${app.geoapify.api-key:}") String apiKey,
            @Value("${app.geoapify.base-url:https://api.geoapify.com}") String baseUrl) {
        this.apiKey = apiKey;
        this.configured = apiKey != null && !apiKey.isBlank();
        this.client = RestClient.builder().baseUrl(baseUrl).build();
        if (!configured) {
            log.warn("Geoapify is not configured (app.geoapify.api-key unset) — address autocomplete is disabled.");
        }
    }

    public boolean isConfigured() {
        return configured;
    }

    /**
     * Suggest addresses for a partial query, optionally restricted to a country (accepts either an
     * ISO alpha-2 code or an English country name such as "Canada").
     */
    public List<AddressSuggestion> autocomplete(String text, String country) {
        if (!configured || text == null || text.trim().length() < MIN_QUERY_LENGTH) {
            return List.of();
        }
        String countryCode = toCountryCode(country);
        try {
            GeoapifyResponse resp = client.get()
                    .uri(b -> b.path("/v1/geocode/autocomplete")
                            .queryParam("text", text.trim())
                            .queryParam("format", "json")
                            .queryParam("limit", MAX_RESULTS)
                            .queryParam("apiKey", apiKey)
                            .queryParamIfPresent("filter",
                                    Optional.ofNullable(countryCode).map(c -> "countrycode:" + c))
                            .build())
                    .retrieve()
                    .body(GeoapifyResponse.class);

            if (resp == null || resp.results() == null) {
                return List.of();
            }
            return resp.results().stream().map(GeoapifyService::toSuggestion).toList();
        } catch (Exception e) {
            // Never let a geocoder hiccup break a checkout/settings form — fall back to manual entry.
            log.warn("Geoapify autocomplete failed for query '{}': {}", text, e.getMessage());
            return List.of();
        }
    }

    private static AddressSuggestion toSuggestion(GeoapifyResult r) {
        return new AddressSuggestion(
                r.housenumber(), r.street(), r.city(), r.state(), r.postcode(), r.country(),
                r.formatted(), r.rank() == null ? null : r.rank().confidence());
    }

    /** Null/blank → no filter; 2-letter input passed through; otherwise resolve an English country name. */
    private static String toCountryCode(String country) {
        if (country == null || country.isBlank()) {
            return null;
        }
        String trimmed = country.trim();
        if (trimmed.length() == 2) {
            return trimmed.toLowerCase(Locale.ROOT);
        }
        return COUNTRY_NAME_TO_CODE.get(trimmed.toLowerCase(Locale.ROOT));
    }

    private static Map<String, String> buildCountryIndex() {
        Map<String, String> index = new HashMap<>();
        for (String code : Locale.getISOCountries()) {
            String name = Locale.of("", code).getDisplayCountry(Locale.ENGLISH);
            if (!name.isBlank()) {
                index.put(name.toLowerCase(Locale.ROOT), code.toLowerCase(Locale.ROOT));
            }
        }
        return index;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record GeoapifyResponse(List<GeoapifyResult> results) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record GeoapifyResult(String housenumber, String street, String city, String state,
                                  String postcode, String country, String formatted, Rank rank) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Rank(Double confidence) {
    }
}