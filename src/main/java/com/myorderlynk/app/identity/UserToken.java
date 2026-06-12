package com.myorderlynk.app.identity;
import com.myorderlynk.app.common.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * A single-use, expiring token for email verification or password reset.
 * One table serves both flows, distinguished by {@link #type}.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "user_tokens", indexes = {
        @Index(name = "idx_user_token_value", columnList = "token", unique = true),
        @Index(name = "idx_user_token_user", columnList = "userId")
})
public class UserToken extends BaseEntity {

    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 64)
    private String token;

    /** EMAIL_VERIFY or PASSWORD_RESET. */
    @Column(nullable = false, length = 40)
    private String type;

    @Column(nullable = false)
    private Instant expiresAt;

    /** Set once the token is consumed; null while still usable. */
    private Instant usedAt;

    public boolean isUsable() {
        return usedAt == null && expiresAt.isAfter(Instant.now());
    }
}