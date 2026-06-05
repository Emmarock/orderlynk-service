package com.myorderlynk.app.security;

import com.myorderlynk.app.domain.enums.UserRole;
import com.myorderlynk.app.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Resolves the {@link AuthPrincipal} for the current request from the JWT in
 * the security context. Inject and call {@link #require()} in controllers.
 */
@Component
public class CurrentUser {

    public AuthPrincipal require() {
        AuthPrincipal principal = resolve();
        if (principal == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        return principal;
    }

    public AuthPrincipal resolve() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof Jwt jwt)) {
            return null;
        }
        UUID userId = UUID.fromString(jwt.getSubject());
        UserRole role = UserRole.valueOf(jwt.getClaimAsStringList("roles").get(0));
        String vendorClaim = jwt.getClaimAsString("vendorId");
        UUID vendorId = vendorClaim == null ? null : UUID.fromString(vendorClaim);
        return new AuthPrincipal(userId, jwt.getClaimAsString("email"), role, vendorId);
    }
}
