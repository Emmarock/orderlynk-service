package com.myorderlynk.app.service.notification;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Map;
import java.util.TreeMap;

/**
 * Validates Twilio webhook requests via the {@code X-Twilio-Signature} header:
 * Base64(HMAC-SHA1(authToken, fullUrl + each sorted param name+value)). We sign against the
 * exact StatusCallback URL we configured (which is the URL Twilio calls), so it matches.
 */
@Slf4j
@Component
public class TwilioSignatureValidator {

    private final String authToken;
    private final String callbackUrl;

    public TwilioSignatureValidator(WhatsAppProperties properties) {
        this.authToken = trim(properties.getTwilio().getAuthToken());
        this.callbackUrl = trim(properties.getTwilio().getStatusCallbackUrl());
    }

    public boolean isValid(String signatureHeader, Map<String, String> params) {
        if (isBlank(authToken) || isBlank(callbackUrl)) {
            log.warn("Twilio webhook rejected — auth-token / status-callback-url not configured for validation");
            return false;
        }
        if (isBlank(signatureHeader)) {
            return false;
        }
        StringBuilder data = new StringBuilder(callbackUrl);
        for (Map.Entry<String, String> e : new TreeMap<>(params).entrySet()) {
            data.append(e.getKey()).append(e.getValue() == null ? "" : e.getValue());
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(authToken.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
            byte[] digest = mac.doFinal(data.toString().getBytes(StandardCharsets.UTF_8));
            String expected = Base64.getEncoder().encodeToString(digest);
            return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                    signatureHeader.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            log.error("Twilio signature validation error", ex);
            return false;
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String trim(String s) {
        return s == null ? null : s.trim();
    }
}