package com.myorderlynk.app.service.notification;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Twilio SendGrid email settings (Twilio's email product). Leave {@code api-key} blank
 * to disable email — sends are then skipped and logged. Active only when
 * {@code email.provider=twilio}; see {@link TwilioEmailProvider}.
 */
@Data
@ConfigurationProperties(prefix = "twilio.email")
public class TwilioEmailProperties {

    private String apiKey;
    private String fromEmail;
    private String fromName;
    private String apiBaseUrl = "https://api.sendgrid.com";
}