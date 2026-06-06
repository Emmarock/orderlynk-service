package com.myorderlynk.app.service.notification;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * WhatsApp settings. {@code provider} selects the channel implementation
 * ({@code twilio} by default, or {@code meta}). Leave the chosen provider's
 * credentials blank to disable WhatsApp — messages are then skipped and logged.
 */
@Data
@ConfigurationProperties(prefix = "whatsapp")
public class WhatsAppProperties {

    /** Which provider to use: "twilio" (default) or "meta". */
    private String provider = "twilio";

    private final Twilio twilio = new Twilio();
    private final Meta meta = new Meta();

    /** Twilio WhatsApp (Messages API + Basic auth). */
    @Data
    public static class Twilio {
        private String accountSid;
        private String authToken;
        /** WhatsApp-enabled sender number, e.g. +14155238886 (Twilio sandbox). */
        private String from;
        private String apiBaseUrl = "https://api.twilio.com";
    }

    /** Meta WhatsApp Cloud API. */
    @Data
    public static class Meta {
        private String accessToken;
        private String phoneNumberId;
        private String apiBaseUrl = "https://graph.facebook.com/v21.0";
    }
}