package com.myorderlynk.app.security;

import com.myorderlynk.app.identity.User;
import com.myorderlynk.app.exception.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Issues signed JWTs for authenticated users. Tokens are validated by the
 * Spring Security OAuth2 resource server configured in {@link SecurityConfig}.
 */
@Service
public class JwtService {

    private static final String TRACK_PURPOSE = "order_track";

    private final JwtEncoder encoder;
    private final JwtDecoder decoder;
    private final long ttlSeconds;
    private final long trackTtlDays;

    public JwtService(JwtEncoder encoder, JwtDecoder decoder,
                      @Value("${app.jwt.ttl-seconds:86400}") long ttlSeconds,
                      @Value("${app.track-link.ttl-days:180}") long trackTtlDays) {
        this.encoder = encoder;
        this.decoder = decoder;
        this.ttlSeconds = ttlSeconds;
        this.trackTtlDays = trackTtlDays;
    }

    public String issueToken(User user) {
        Instant now = Instant.now();
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer("myorderlynk")
                .issuedAt(now)
                .expiresAt(now.plus(ttlSeconds, ChronoUnit.SECONDS))
                .subject(user.getId().toString())
                .claim("email", user.getEmail())
                .claim("name", user.getFullName())
                .claim("roles", List.of(user.getRole().name()));
        if (user.getVendorId() != null) {
            claims.claim("vendorId", user.getVendorId().toString());
        }
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims.build())).getTokenValue();
    }

    /**
     * Issues a short-lived service token for machine-to-machine calls to internal
     * services (e.g. the payment-service). Carries {@code roles:["SERVICE"]} and the
     * same {@code myorderlynk} issuer, so the callee validates it with the shared secret.
     */
    public String issueServiceToken() {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("myorderlynk")
                .issuedAt(now)
                .expiresAt(now.plus(5, ChronoUnit.MINUTES))
                .subject("orderlynk-backend")
                .claim("roles", List.of("SERVICE"))
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    /**
     * Issues a signed, purpose-scoped token embedding an order's public id + the contact used,
     * so order-tracking links carry no PII in the URL and can't be tampered with.
     */
    public String issueOrderTrackToken(String publicOrderId, String contact) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("myorderlynk")
                .issuedAt(now)
                .expiresAt(now.plus(trackTtlDays, ChronoUnit.DAYS))
                .subject(publicOrderId)
                .claim("purpose", TRACK_PURPOSE)
                .claim("contact", contact == null ? "" : contact)
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    /** Verifies an order-track token (signature, expiry, purpose) and returns its claims. */
    public OrderTrackToken parseOrderTrackToken(String token) {
        Jwt jwt;
        try {
            jwt = decoder.decode(token);
        } catch (JwtException e) {
            throw ApiException.badRequest("This tracking link is invalid or has expired");
        }
        if (!TRACK_PURPOSE.equals(jwt.getClaimAsString("purpose"))) {
            throw ApiException.badRequest("This tracking link is invalid");
        }
        return new OrderTrackToken(jwt.getSubject(), jwt.getClaimAsString("contact"));
    }

    /** Claims carried by an order-track token. */
    public record OrderTrackToken(String publicOrderId, String contact) {
    }
}
