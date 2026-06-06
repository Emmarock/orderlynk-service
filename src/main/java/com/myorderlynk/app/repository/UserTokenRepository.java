package com.myorderlynk.app.repository;

import com.myorderlynk.app.domain.UserToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserTokenRepository extends JpaRepository<UserToken, UUID> {
    Optional<UserToken> findByTokenAndType(String token, String type);
}