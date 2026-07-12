package com.myorderlynk.app.identity;

import com.myorderlynk.app.identity.UserToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface UserTokenRepository extends JpaRepository<UserToken, UUID> {
    Optional<UserToken> findByTokenAndType(String token, String type);

    /**
     * Bulk-deletes expired tokens; returns the number removed. Used-but-unexpired tokens are kept on
     * purpose so email verification stays idempotent for the token's full lifetime (see
     * AuthService.verifyEmail) — a spent token is already unusable via {@link UserToken#isUsable()}.
     */
    @Modifying
    @Query("delete from UserToken t where t.expiresAt < :now")
    int deleteExpired(Instant now);
}