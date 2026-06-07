package com.myorderlynk.app.shipping;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Resolves the shipping provider to use. All {@link ShippingProvider} beans are discovered at
 * startup and indexed by {@link ShippingProvider#key()}; {@code shipping.provider} selects the
 * default. Designed for multiple partners — additional providers just need a bean and a key.
 */
@Slf4j
@Component
public class ShippingProviderRegistry {

    private final Map<String, ShippingProvider> byKey;
    private final String defaultKey;

    public ShippingProviderRegistry(List<ShippingProvider> providers, ShippingProperties properties) {
        this.byKey = providers.stream().collect(Collectors.toMap(
                p -> p.key().toLowerCase(), Function.identity(), (a, b) -> a));
        this.defaultKey = properties.getProvider() == null ? "" : properties.getProvider().toLowerCase();
        log.info("Shipping providers registered: {} (default={}, configured={})",
                byKey.keySet(), defaultKey, active().map(ShippingProvider::isConfigured).orElse(false));
    }

    /** The configured default provider, if it exists. */
    public Optional<ShippingProvider> active() {
        return Optional.ofNullable(byKey.get(defaultKey));
    }

    /** Look up a provider by key (for routing a specific shipment to a specific partner). */
    public Optional<ShippingProvider> byKey(String key) {
        return key == null ? Optional.empty() : Optional.ofNullable(byKey.get(key.toLowerCase()));
    }

    /** The active provider, or throw if none is configured/usable. */
    public ShippingProvider require() {
        ShippingProvider provider = active().orElseThrow(() ->
                new ShippingException("No shipping provider '" + defaultKey + "' is registered"));
        if (!provider.isConfigured()) {
            throw new ShippingException("Shipping provider '" + defaultKey + "' is not configured");
        }
        return provider;
    }

    /** True when the default provider exists and is configured (used to decide live-rating vs flat fee). */
    public boolean isConfigured() {
        return active().map(ShippingProvider::isConfigured).orElse(false);
    }
}