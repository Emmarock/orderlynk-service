package com.myorderlynk.app.payment;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for talking to the standalone payment-service, bound from
 * {@code payment.service.*}. Disabled by default so existing checkout behaviour
 * is unchanged until the payment-service is deployed and this is switched on.
 */
@ConfigurationProperties(prefix = "payment.service")
public class PaymentServiceProperties {

    /** When false, the backend does not call the payment-service (legacy inline flow). */
    private boolean enabled = false;

    /** Base URL of the payment-service, e.g. https://orderlynk-payment.onrender.com. */
    private String baseUrl = "http://localhost:8081";

    /** Shared HMAC secret used to verify inbound payment-service event signatures. */
    private String eventSigningSecret = "dev-payment-event-signing-secret-change-me";

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

    public String getEventSigningSecret() { return eventSigningSecret; }
    public void setEventSigningSecret(String eventSigningSecret) { this.eventSigningSecret = eventSigningSecret; }
}