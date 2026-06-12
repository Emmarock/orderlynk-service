package com.myorderlynk.app.notification;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.Map;

/**
 * WhatsApp via the Meta WhatsApp Cloud API. Active only when {@code whatsapp.provider=meta}.
 * Degrades gracefully: with no token/phone-number-id it logs and skips.
 */
@Slf4j
@Service
@ConditionalOnProperty(prefix = "whatsapp", name = "provider", havingValue = "meta")
public class MetaWhatsAppProvider implements WhatsAppProvider {

    private final WhatsAppProperties.Meta meta;
    private final boolean configured;
    private final WebClient webClient;

    public MetaWhatsAppProvider(WhatsAppProperties properties) {
        this.meta = properties.getMeta();
        this.configured = isSet(meta.getAccessToken()) && isSet(meta.getPhoneNumberId());
        this.webClient = WebClient.builder().baseUrl(meta.getApiBaseUrl()).build();
        if (!configured) {
            log.warn("WhatsApp (Meta) is not configured (whatsapp.meta.* unset) — messages will be skipped.");
        }
    }

    @Override
    public String send(WhatsAppRequestedEvent message) {
        String to = message.to() == null ? "" : message.to().replaceAll("\\D", "");
        if (!configured) {
            log.warn("Skipping WhatsApp message to {} — Meta not configured", to);
            return null;
        }
        if (to.isBlank()) {
            log.warn("Skipping WhatsApp message — no recipient number");
            return null;
        }

        Map<String, Object> payload = Map.of(
                "messaging_product", "whatsapp",
                "to", to,
                "type", "text",
                "text", Map.of("preview_url", true, "body", message.body()));
        try {
            webClient.post()
                    .uri("/{phoneNumberId}/messages", meta.getPhoneNumberId())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + meta.getAccessToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(payload)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            log.info("Meta accepted WhatsApp to {}", to);
            return null; // Meta returns a wamid; delivery tracking via Meta webhooks is out of scope here.
        } catch (WebClientResponseException e) {
            throw new IllegalStateException(
                    "WhatsApp (Meta) send to " + to + " failed (HTTP " + e.getStatusCode().value() + "): "
                            + e.getResponseBodyAsString());
        }
    }

    private static boolean isSet(String s) {
        return s != null && !s.isBlank();
    }
}