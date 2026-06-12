package com.myorderlynk.app.notification;
import com.myorderlynk.app.common.Address;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

/**
 * Email via Twilio SendGrid's v3 Mail Send API. Active when {@code email.provider=twilio};
 * Resend ({@link ResendEmailProvider}) remains the default otherwise.
 *
 * <p>Degrades gracefully: with no API key the app still boots and emails are skipped + logged,
 * mirroring {@link ResendEmailProvider}. SendGrid returns {@code 202 Accepted} with an empty
 * body on success, so we don't read a response payload.
 */
@Slf4j
@Service
@ConditionalOnProperty(prefix = "email", name = "provider", havingValue = "twilio")
public class TwilioEmailProvider implements EmailProvider {

    private final TwilioEmailProperties properties;
    private final boolean configured;
    private final WebClient webClient;

    public TwilioEmailProvider(TwilioEmailProperties properties) {
        this.properties = properties;
        this.configured = properties.getApiKey() != null && !properties.getApiKey().isBlank();
        this.webClient = WebClient.builder().baseUrl(properties.getApiBaseUrl()).build();
        if (!configured) {
            log.warn("Twilio SendGrid is not configured (twilio.email.api-key unset) — emails will be skipped.");
        }
    }

    @Override
    public void sendEmail(String recipient, String subject, String html) {
        if (!configured) {
            log.warn("Skipping email '{}' to {} — Twilio SendGrid not configured", subject, recipient);
            return;
        }
        if (recipient == null || recipient.isBlank()) {
            log.warn("Skipping email '{}' — no recipient", subject);
            return;
        }

        SendGridEmailRequest.Address from =
                new SendGridEmailRequest.Address(properties.getFromEmail(), properties.getFromName());
        SendGridEmailRequest request = SendGridEmailRequest.html(from, recipient, subject, html);

        // Runs on the email executor (see EmailEventListener), so a blocking call is fine here.
        try {
            webClient.post()
                    .uri("/v3/mail/send")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .retrieve()
                    .toBodilessEntity()
                    .block();
        } catch (WebClientResponseException e) {
            // Surface SendGrid's own error message (e.g. unverified sender, invalid address)
            // instead of a bare status. Thrown without the reactor cause to keep the log readable.
            throw new IllegalStateException(
                    "Twilio SendGrid rejected email to " + recipient + " (HTTP " + e.getStatusCode().value() + "): "
                            + e.getResponseBodyAsString());
        }
    }
}