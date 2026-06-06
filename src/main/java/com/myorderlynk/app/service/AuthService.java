package com.myorderlynk.app.service;

import com.myorderlynk.app.domain.User;
import com.myorderlynk.app.domain.UserToken;
import com.myorderlynk.app.domain.enums.UserRole;
import com.myorderlynk.app.dto.AuthDtos.AuthResponse;
import com.myorderlynk.app.dto.AuthDtos.ChangeEmailRequest;
import com.myorderlynk.app.dto.AuthDtos.ChangePasswordRequest;
import com.myorderlynk.app.dto.AuthDtos.LoginRequest;
import com.myorderlynk.app.dto.AuthDtos.RegisterRequest;
import com.myorderlynk.app.dto.AuthDtos.UpdateProfileRequest;
import com.myorderlynk.app.repository.UserRepository;
import com.myorderlynk.app.repository.UserTokenRepository;
import com.myorderlynk.app.security.JwtService;
import com.myorderlynk.app.service.notification.EmailService;
import com.myorderlynk.app.service.util.CodeGenerator;
import com.myorderlynk.app.exception.ApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
@Slf4j
public class AuthService {

    private static final String TYPE_VERIFY = "EMAIL_VERIFY";
    private static final String TYPE_RESET = "PASSWORD_RESET";
    private static final Duration VERIFY_TTL = Duration.ofHours(48);
    private static final Duration RESET_TTL = Duration.ofHours(1);

    private final UserRepository users;
    private final UserTokenRepository userTokens;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final EmailService emailService;

    public AuthService(UserRepository users, UserTokenRepository userTokens, PasswordEncoder passwordEncoder,
                       JwtService jwtService, EmailService emailService) {
        this.users = users;
        this.userTokens = userTokens;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.emailService = emailService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest req) {
        if (users.existsByEmailIgnoreCase(req.email())) {
            throw new ApiException(HttpStatus.CONFLICT, "An account with this email already exists");
        }
        User user = new User();
        user.setEmail(req.email().toLowerCase());
        user.setPasswordHash(passwordEncoder.encode(req.password()));
        user.setFullName(req.fullName());
        user.setPhone(req.phone());
        user.setCity(req.city());
        user.setCountry(req.country());
        user.setRole(UserRole.CUSTOMER);
        users.save(user);
        log.info("Registered new user {} ({})", user.getId(), user.getEmail());
        emailService.sendEmailVerification(user.getEmail(), user.getFullName(), createToken(user, TYPE_VERIFY, VERIFY_TTL));
        return toResponse(user);
    }

    /** Verify an email address from a token link; sends the welcome email on first success. */
    @Transactional
    public void verifyEmail(String token) {
        UserToken record = userTokens.findByTokenAndType(token, TYPE_VERIFY)
                .filter(UserToken::isUsable)
                .orElseThrow(() -> ApiException.badRequest("This verification link is invalid or has expired"));
        User user = users.findById(record.getUserId())
                .orElseThrow(() -> ApiException.notFound("User not found"));
        record.setUsedAt(Instant.now());
        userTokens.save(record);
        if (!user.isEmailVerified()) {
            user.setEmailVerified(true);
            users.save(user);
            log.info("Email verified for user {}", user.getId());
            emailService.sendWelcome(user.getEmail(), user.getFullName());
        }
    }

    /** Re-send the verification email for the signed-in user (no-op if already verified). */
    @Transactional
    public void resendVerification(UUID userId) {
        User user = users.findById(userId)
                .orElseThrow(() -> ApiException.notFound("User not found"));
        if (user.isEmailVerified()) {
            return;
        }
        emailService.sendEmailVerification(user.getEmail(), user.getFullName(), createToken(user, TYPE_VERIFY, VERIFY_TTL));
        log.info("Resent verification email for user {}", userId);
    }

    /**
     * Begin a password reset. Always succeeds silently (no account enumeration); an email is only
     * sent when an account exists for the address.
     */
    @Transactional
    public void requestPasswordReset(String email) {
        users.findByEmailIgnoreCase(email).ifPresentOrElse(user -> {
            emailService.sendPasswordReset(user.getEmail(), user.getFullName(), createToken(user, TYPE_RESET, RESET_TTL));
            log.info("Password reset requested for user {}", user.getId());
        }, () -> log.info("Password reset requested for unknown email — ignoring"));
    }

    /** Complete a password reset using a token link. */
    @Transactional
    public void resetPassword(String token, String newPassword) {
        UserToken record = userTokens.findByTokenAndType(token, TYPE_RESET)
                .filter(UserToken::isUsable)
                .orElseThrow(() -> ApiException.badRequest("This reset link is invalid or has expired"));
        User user = users.findById(record.getUserId())
                .orElseThrow(() -> ApiException.notFound("User not found"));
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        users.save(user);
        record.setUsedAt(Instant.now());
        userTokens.save(record);
        log.info("Password reset completed for user {}", user.getId());
    }

    private String createToken(User user, String type, Duration ttl) {
        UserToken token = new UserToken();
        token.setUserId(user.getId());
        token.setToken(CodeGenerator.secureToken());
        token.setType(type);
        token.setExpiresAt(Instant.now().plus(ttl));
        userTokens.save(token);
        return token.getToken();
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest req) {
        User user = users.findByEmailIgnoreCase(req.email())
                .orElseThrow(() -> {
                    log.warn("Failed login (no such user) for {}", req.email());
                    return new ApiException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
                });
        if (!passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            log.warn("Failed login (bad password) for {}", req.email());
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
        }
        log.info("User {} logged in", user.getId());
        return toResponse(user);
    }

    /** Self-service password rotation: verifies the current password before updating. */
    @Transactional
    public void changePassword(UUID userId, ChangePasswordRequest req) {
        User user = users.findById(userId)
                .orElseThrow(() -> ApiException.notFound("User not found"));
        if (!passwordEncoder.matches(req.currentPassword(), user.getPasswordHash())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Current password is incorrect");
        }
        if (passwordEncoder.matches(req.newPassword(), user.getPasswordHash())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "New password must be different from the current one");
        }
        user.setPasswordHash(passwordEncoder.encode(req.newPassword()));
        users.save(user);
        log.info("Password changed for user {}", userId);
    }

    /** Update the user's own profile details. Does not affect the auth token. */
    @Transactional
    public AuthResponse updateProfile(UUID userId, UpdateProfileRequest req) {
        User user = users.findById(userId)
                .orElseThrow(() -> ApiException.notFound("User not found"));
        user.setFullName(req.fullName());
        user.setPhone(req.phone());
        user.setCity(req.city());
        user.setCountry(req.country());
        users.save(user);
        return new AuthResponse(null, user.getId(), user.getFullName(), user.getEmail(),
                user.getRole(), user.getVendorId(), user.isEmailVerified());
    }

    /** Change the user's email after verifying their password; re-issues a token with fresh claims. */
    @Transactional
    public AuthResponse changeEmail(UUID userId, ChangeEmailRequest req) {
        User user = users.findById(userId)
                .orElseThrow(() -> ApiException.notFound("User not found"));
        if (!passwordEncoder.matches(req.currentPassword(), user.getPasswordHash())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Password is incorrect");
        }
        String newEmail = req.newEmail().trim().toLowerCase();
        if (!newEmail.equalsIgnoreCase(user.getEmail()) && users.existsByEmailIgnoreCase(newEmail)) {
            throw new ApiException(HttpStatus.CONFLICT, "An account with this email already exists");
        }
        String oldEmail = user.getEmail();
        user.setEmail(newEmail);
        user.setEmailVerified(false);
        users.save(user);
        log.info("Email changed for user {}: {} -> {}", userId, oldEmail, newEmail);
        emailService.sendEmailVerification(newEmail, user.getFullName(), createToken(user, TYPE_VERIFY, VERIFY_TTL));
        return toResponse(user);
    }

    @Transactional(readOnly = true)
    public AuthResponse me(UUID userId) {
        User user = users.findById(userId)
                .orElseThrow(() -> ApiException.notFound("User not found"));
        return new AuthResponse(null, user.getId(), user.getFullName(), user.getEmail(),
                user.getRole(), user.getVendorId(), user.isEmailVerified());
    }

    private AuthResponse toResponse(User user) {
        String token = jwtService.issueToken(user);
        return new AuthResponse(token, user.getId(), user.getFullName(), user.getEmail(),
                user.getRole(), user.getVendorId(), user.isEmailVerified());
    }
}
