package com.myorderlynk.app.identity;

import com.myorderlynk.app.exception.ApiException;
import com.myorderlynk.app.notification.EmailService;
import com.myorderlynk.app.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AuthService#verifyEmail} idempotency. Email security scanners / link
 * prefetchers (SafeLinks, Proofpoint, Gmail/Outlook preview) spend the single-use token seconds
 * after the mail is sent, so a real click that arrives afterwards must still report success rather
 * than a spurious "invalid or expired" error.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceVerifyEmailTest {

    private static final String TOKEN = "verify-token-abc123";

    @Mock UserRepository users;
    @Mock UserTokenRepository userTokens;
    @Mock PasswordEncoder passwordEncoder;
    @Mock JwtService jwtService;
    @Mock EmailService emailService;
    @Mock CustomerAddressService customerAddresses;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(users, userTokens, passwordEncoder, jwtService,
                emailService, customerAddresses);
    }

    private User unverifiedUser(UUID id) {
        User user = new User();
        user.setId(id);
        user.setEmail("scanned@example.com");
        user.setFullName("Sam Scanner");
        user.setEmailVerified(false);
        return user;
    }

    private UserToken verifyToken(UUID userId, Instant expiresAt) {
        UserToken token = new UserToken();
        token.setUserId(userId);
        token.setToken(TOKEN);
        token.setType("EMAIL_VERIFY");
        token.setExpiresAt(expiresAt);
        return token;
    }

    @Test
    void repeatClickOnAlreadyConsumedTokenStillSucceedsAndDoesNotResendWelcome() {
        UUID userId = UUID.randomUUID();
        User user = unverifiedUser(userId);
        // A still-valid token (48h TTL); the real entities are mutated in place so state carries
        // across both calls, exactly as it would in the DB.
        UserToken token = verifyToken(userId, Instant.now().plus(48, ChronoUnit.HOURS));
        when(userTokens.findByTokenAndType(TOKEN, "EMAIL_VERIFY")).thenReturn(Optional.of(token));
        when(users.findById(userId)).thenReturn(Optional.of(user));

        // First hit — the link scanner: consumes the token and verifies the account.
        authService.verifyEmail(TOKEN);
        assertThat(user.isEmailVerified()).isTrue();
        assertThat(token.getUsedAt()).isNotNull();
        Instant consumedAt = token.getUsedAt();

        // Second hit — the human, moments later on the same (now-spent) token: must not throw.
        assertThatCode(() -> authService.verifyEmail(TOKEN)).doesNotThrowAnyException();

        assertThat(user.isEmailVerified()).isTrue();
        assertThat(token.getUsedAt()).isEqualTo(consumedAt); // usedAt not overwritten on replay
        // Welcome email and the verified flag are applied exactly once, not once per click.
        verify(emailService, times(1)).sendWelcome(user.getEmail(), user.getFullName());
        verify(users, times(1)).save(user);
    }

    @Test
    void expiredTokenIsRejected() {
        UUID userId = UUID.randomUUID();
        UserToken token = verifyToken(userId, Instant.now().minus(1, ChronoUnit.MINUTES));
        when(userTokens.findByTokenAndType(TOKEN, "EMAIL_VERIFY")).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> authService.verifyEmail(TOKEN))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("invalid or has expired");

        verify(emailService, never()).sendWelcome(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void unknownTokenIsRejected() {
        when(userTokens.findByTokenAndType(TOKEN, "EMAIL_VERIFY")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.verifyEmail(TOKEN))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("invalid or has expired");
    }
}
