package com.myorderlynk.app.service.notification;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;

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

    // Trimmed once — secret managers / .env files often leave a trailing newline, which would
    // corrupt the URL path (→ Twilio 20404) or Basic auth. Defensive normalisation avoids that.
    private final String accountSid;
    private final String authToken;
    private final String from;
    private final String messagingServiceSid;
    private final boolean configured;
    private final WebClient webClient;

    public TwilioWhatsAppProvider(WhatsAppProperties properties) {
        WhatsAppProperties.Twilio twilio = properties.getTwilio();
        this.accountSid = trim(twilio.getAccountSid());
        this.authToken = trim(twilio.getAuthToken());
        this.from = trim(twilio.getFrom());
        this.messagingServiceSid = trim(twilio.getMessagingServiceSid());
        boolean hasSender = isSet(messagingServiceSid) || isSet(from);
        this.configured = isSet(accountSid) && isSet(authToken) && hasSender;
        this.webClient = WebClient.builder().baseUrl(trim(twilio.getApiBaseUrl())).build();
        if (!configured) {
            log.warn("WhatsApp (Twilio) is not configured (need account-sid, auth-token and a from/messaging-service-sid) — messages will be skipped.");
        }
    }

    /**
     * One-time credential check at startup: fetch the account so misconfigured Twilio
     * credentials (wrong SID, bad token) surface in the logs immediately rather than on
     * the first order. Never blocks/breaks startup — it's time-bounded and non-fatal.
     */
    @PostConstruct
    void verifyCredentials() {
        if (!configured) {
            return;
        }
        try {
            webClient.get()
                    .uri("/2010-04-01/Accounts/{sid}.json", accountSid)
                    .headers(h -> h.setBasicAuth(accountSid, authToken))
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(Duration.ofSeconds(10));
            log.info("Twilio credentials verified for account {} — WhatsApp ready.", accountSid);
        } catch (WebClientResponseException e) {
            log.error("Twilio credential check FAILED (HTTP {}): {} — check whatsapp.twilio.account-sid/auth-token. "
                    + "WhatsApp sends will fail until resolved.", e.getStatusCode().value(), e.getResponseBodyAsString());
        } catch (Exception e) {
            log.warn("Could not verify Twilio credentials at startup ({}). Sends will still be attempted.", e.getMessage());
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
        if (isSet(messagingServiceSid)) {
            form.add("MessagingServiceSid", messagingServiceSid);
        } else {
            form.add("From", "whatsapp:" + e164(from));
        }
        form.add("To", "whatsapp:" + to);
        form.add("Body", body);
        try {
            webClient.post()
                    .uri("/2010-04-01/Accounts/{sid}/Messages.json", accountSid)
                    .headers(h -> h.setBasicAuth(accountSid, authToken))
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

    private static String trim(String s) {
        return s == null ? null : s.trim();
    }

    /** Normalise to E.164 (digits with a leading +). */
    private static String e164(String phone) {
        return "+" + (phone == null ? "" : phone.replaceAll("\\D", ""));
    }

    private static boolean isSet(String s) {
        return s != null && !s.isBlank();
    }
}