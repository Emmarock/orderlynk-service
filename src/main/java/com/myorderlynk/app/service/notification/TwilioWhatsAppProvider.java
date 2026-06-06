package com.myorderlynk.app.service.notification;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

/**
 * WhatsApp via Twilio's Messages API (the default provider). Active when
 * {@code whatsapp.provider=twilio} or unset. Uses HTTP Basic auth (AccountSid:AuthToken)
 * and form-encoded {@code From}/{@code To}/{@code Body} with the {@code whatsapp:} prefix.
 * Degrades gracefully: with no credentials it logs and skips.
 */
@Slf4j
@Service
@ConditionalOnProperty(prefix = "whatsapp", name = "provider", havingValue = "twilio", matchIfMissing = true)
public class TwilioWhatsAppProvider implements WhatsAppProvider {

    private final WhatsAppProperties.Twilio twilio;
    private final boolean configured;
    private final WebClient webClient;

    public TwilioWhatsAppProvider(WhatsAppProperties properties) {
        this.twilio = properties.getTwilio();
        boolean hasSender = isSet(twilio.getMessagingServiceSid()) || isSet(twilio.getFrom());
        this.configured = isSet(twilio.getAccountSid()) && isSet(twilio.getAuthToken()) && hasSender;
        this.webClient = WebClient.builder().baseUrl(twilio.getApiBaseUrl()).build();
        if (!configured) {
            log.warn("WhatsApp (Twilio) is not configured (need account-sid, auth-token and a from/messaging-service-sid) — messages will be skipped.");
        }
    }

    @Override
    public void send(String toPhone, String body) {
        String to = e164(toPhone);
        if (!configured) {
            log.warn("Skipping WhatsApp message to {} — Twilio not configured", to);
            return;
        }
        if (to.equals("+")) {
            log.warn("Skipping WhatsApp message — no recipient number");
            return;
        }

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        // Prefer a Messaging Service (recommended for WhatsApp senders); fall back to a raw From number.
        if (isSet(twilio.getMessagingServiceSid())) {
            form.add("MessagingServiceSid", twilio.getMessagingServiceSid());
        } else {
            form.add("From", "whatsapp:" + e164(twilio.getFrom()));
        }
        form.add("To", "whatsapp:" + to);
        form.add("Body", body);
        try {
            webClient.post()
                    .uri("/2010-04-01/Accounts/{sid}/Messages.json", twilio.getAccountSid())
                    .headers(h -> h.setBasicAuth(twilio.getAccountSid(), twilio.getAuthToken()))
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .bodyValue(form)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
        } catch (WebClientResponseException e) {
            throw new IllegalStateException(
                    "WhatsApp (Twilio) send to " + to + " failed (HTTP " + e.getStatusCode().value() + "): "
                            + e.getResponseBodyAsString());
        }
    }

    /** Normalise to E.164 (digits with a leading +). */
    private static String e164(String phone) {
        return "+" + (phone == null ? "" : phone.replaceAll("\\D", ""));
    }

    private static boolean isSet(String s) {
        return s != null && !s.isBlank();
    }
}