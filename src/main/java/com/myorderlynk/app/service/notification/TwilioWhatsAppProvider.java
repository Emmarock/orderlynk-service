package com.myorderlynk.app.service.notification;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.List;
import java.util.Map;

/**
 * WhatsApp via Twilio's Messages API (the default provider). Active when
 * {@code whatsapp.provider=twilio} or unset.
 *
 * <p>Sends an approved Content template (ContentSid + ContentVariables) when one is configured
 * for the message's template key — this is what lets business-initiated messages deliver outside
 * WhatsApp's 24h window — and falls back to a free-form Body otherwise. When a status-callback URL
 * is set, Twilio is told to POST delivery updates there. Degrades gracefully: no credentials → skip.
 */
@Slf4j
@Service
@ConditionalOnProperty(prefix = "whatsapp", name = "provider", havingValue = "twilio", matchIfMissing = true)
public class TwilioWhatsAppProvider implements WhatsAppProvider {

    private final String accountSid;
    private final String authToken;
    private final String from;
    private final String messagingServiceSid;
    private final String statusCallbackUrl;
    private final Map<String, String> templates;
    private final boolean configured;
    private final WebClient webClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public TwilioWhatsAppProvider(WhatsAppProperties properties) {
        WhatsAppProperties.Twilio twilio = properties.getTwilio();
        this.accountSid = trim(twilio.getAccountSid());
        this.authToken = trim(twilio.getAuthToken());
        this.from = trim(twilio.getFrom());
        this.messagingServiceSid = trim(twilio.getMessagingServiceSid());
        this.statusCallbackUrl = trim(twilio.getStatusCallbackUrl());
        this.templates = twilio.getTemplates();
        boolean hasSender = isSet(messagingServiceSid) || isSet(from);
        this.configured = isSet(accountSid) && isSet(authToken) && hasSender;
        this.webClient = WebClient.builder().baseUrl(trim(twilio.getApiBaseUrl())).build();
        if (!configured) {
            log.warn("WhatsApp (Twilio) is not configured (need account-sid, auth-token and a from/messaging-service-sid) — messages will be skipped.");
        }
    }

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
    public String send(WhatsAppRequestedEvent message) {
        String to = e164(message.to());
        if (!configured) {
            log.warn("Skipping WhatsApp message to {} — Twilio not configured", to);
            return null;
        }
        if (to.equals("+")) {
            log.warn("Skipping WhatsApp message — no recipient number");
            return null;
        }

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        // Sender: a Messaging Service (recommended for WhatsApp) or a raw From number.
        if (isSet(messagingServiceSid)) {
            form.add("MessagingServiceSid", messagingServiceSid);
        } else {
            form.add("From", "whatsapp:" + e164(from));
        }
        form.add("To", "whatsapp:" + to);

        // Prefer an approved Content template for this event (delivers outside the 24h window);
        // otherwise send a free-form body (works in-window / in the sandbox).
        String contentSid = templates == null ? null : trim(templates.get(message.template()));
        if (isSet(contentSid)) {
            form.add("ContentSid", contentSid);
            String vars = contentVariables(message.variables());
            if (vars != null) {
                form.add("ContentVariables", vars);
            }
        } else {
            form.add("Body", message.body());
        }
        if (isSet(statusCallbackUrl)) {
            form.add("StatusCallback", statusCallbackUrl);
        }

        try {
            TwilioMessage msg = webClient.post()
                    .uri("/2010-04-01/Accounts/{sid}/Messages.json", accountSid)
                    .headers(h -> h.setBasicAuth(accountSid, authToken))
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .bodyValue(form)
                    .retrieve()
                    .bodyToMono(TwilioMessage.class)
                    .block();
            String sid = msg == null ? null : msg.sid();
            log.info("Twilio accepted WhatsApp to {}: sid={} status={} (queued, not yet delivered)",
                    to, sid, msg == null ? "?" : msg.status());
            return sid;
        } catch (WebClientResponseException e) {
            throw new IllegalStateException(
                    "WhatsApp (Twilio) send to " + to + " failed (HTTP " + e.getStatusCode().value() + "): "
                            + e.getResponseBodyAsString());
        }
    }

    /** Twilio ContentVariables: a JSON object of 1-indexed string keys, e.g. {"1":"Ada","2":"OB-1"}. */
    private String contentVariables(List<String> variables) {
        if (variables == null || variables.isEmpty()) {
            return null;
        }
        var map = new java.util.LinkedHashMap<String, String>();
        for (int i = 0; i < variables.size(); i++) {
            map.put(String.valueOf(i + 1), variables.get(i) == null ? "" : variables.get(i));
        }
        try {
            return objectMapper.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private static String e164(String phone) {
        return "+" + (phone == null ? "" : phone.replaceAll("\\D", ""));
    }

    private static boolean isSet(String s) {
        return s != null && !s.isBlank();
    }

    private static String trim(String s) {
        return s == null ? null : s.trim();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record TwilioMessage(String sid, String status) {
    }
}