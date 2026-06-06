package com.myorderlynk.app.service.notification;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.List;

@Slf4j
@Service
public class ResendEmailProvider implements EmailProvider {

    private final ResendProperties properties;
    private final boolean configured;
    private final WebClient webClient = WebClient.builder()
            .baseUrl("https://api.resend.com")
            .build();

    public ResendEmailProvider(ResendProperties properties) {
        this.properties = properties;
        this.configured = properties.getApiKey() != null && !properties.getApiKey().isBlank();
        if (!configured) {
            log.warn("Resend is not configured (resend.api-key unset) — emails will be skipped.");
        }
    }

    @Override
    public void sendEmail(String recipient, String subject, String html) {
        if (!configured) {
            log.warn("Skipping email '{}' to {} — Resend not configured", subject, recipient);
            return;
        }
        if (recipient == null || recipient.isBlank()) {
            log.warn("Skipping email '{}' — no recipient", subject);
            return;
        }

        ResendEmailRequest request = new ResendEmailRequest();
        request.setFrom(properties.getFromName() + " <" + properties.getFromEmail() + ">");
        request.setTo(List.of(recipient));
        request.setSubject(subject);
        request.setHtml(html);

        // Runs on the email executor (see EmailEventListener), so a blocking call is fine here.
        try {
            webClient.post()
                    .uri("/emails")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
        } catch (WebClientResponseException e) {
            // Surface Resend's own error message (e.g. unverified domain, test-mode recipient limits)
            // instead of a bare status. Thrown without the reactor cause to keep the log readable.
            throw new IllegalStateException(
                    "Resend rejected email to " + recipient + " (HTTP " + e.getStatusCode().value() + "): "
                            + e.getResponseBodyAsString());
        }
    }
}