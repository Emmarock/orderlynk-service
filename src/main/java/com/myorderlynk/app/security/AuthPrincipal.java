package com.myorderlynk.app.security;

import com.myorderlynk.app.common.enums.UserRole;

import java.util.UUID;

/**
 * Lightweight view of the authenticated user, resolved from JWT claims.
 */
public record AuthPrincipal(UUID userId, String email, UserRole role, UUID vendorId) {
}
