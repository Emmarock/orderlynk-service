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

    /** Bulk-deletes consumed or expired tokens; returns the number removed. */
    @Modifying
    @Query("delete from UserToken t where t.usedAt is not null or t.expiresAt < :now")
    int deleteUsedOrExpired(Instant now);
}