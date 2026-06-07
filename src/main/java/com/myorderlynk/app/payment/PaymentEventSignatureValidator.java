package com.myorderlynk.app.payment;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * Verifies the {@code X-Payment-Signature} header on inbound payment-service
 * events: lowercase-hex HMAC-SHA256 of the raw request body keyed by the shared
 * signing secret. Mirrors the existing Twilio webhook validation approach.
 */
@Slf4j
@Component
public class PaymentEventSignatureValidator {

    private static final String ALGORITHM = "HmacSHA256";

    private final String secret;

    public PaymentEventSignatureValidator(PaymentServiceProperties properties) {
        this.secret = properties.getEventSigningSecret();
    }

    public boolean isValid(String signatureHeader, String rawBody) {
        if (signatureHeader == null || signatureHeader.isBlank() || secret == null || secret.isBlank()) {
            return false;
        }
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM));
            byte[] digest = mac.doFinal(rawBody.getBytes(StandardCharsets.UTF_8));
            String expected = HexFormat.of().formatHex(digest);
            return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                    signatureHeader.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.error("Payment event signature validation error", e);
            return false;
        }
    }
}