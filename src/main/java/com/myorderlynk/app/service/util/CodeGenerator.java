package com.myorderlynk.app.service.util;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Base64;

/**
 * Generates human-friendly identifiers per Appendix A of the PRD.
 */
public final class CodeGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final DateTimeFormatter YYMMDD = DateTimeFormatter.ofPattern("yyMMdd");
    private static final String SLUG_ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789";

    private CodeGenerator() {
    }

    /** Order id, format OB-YYMMDD-RANDOM, e.g. OB-260601-4821. */
    public static String orderId() {
        String date = LocalDate.now(ZoneOffset.UTC).format(YYMMDD);
        int rand = 1000 + RANDOM.nextInt(9000);
        return "OB-" + date + "-" + rand;
    }

    /** Short numeric pickup code, e.g. 5821. */
    public static String pickupCode() {
        return String.valueOf(1000 + RANDOM.nextInt(9000));
    }

    /** URL-safe store slug derived from a business name, with a uniqueness suffix when needed. */
    public static String slugify(String input) {
        String base = input == null ? "" : input.toLowerCase()
                .replaceAll("[^a-z0-9\\s-]", "")
                .trim()
                .replaceAll("[\\s-]+", "-");
        if (base.isBlank()) {
            base = "vendor";
        }
        return base;
    }

    /** Cryptographically-strong, URL-safe token (e.g. for email verification / password reset links). */
    public static String secureToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static String randomSuffix() {
        StringBuilder sb = new StringBuilder(4);
        for (int i = 0; i < 4; i++) {
            sb.append(SLUG_ALPHABET.charAt(RANDOM.nextInt(SLUG_ALPHABET.length())));
        }
        return sb.toString();
    }
}
