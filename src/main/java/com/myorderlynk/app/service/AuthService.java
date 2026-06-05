package com.myorderlynk.app.service;

import com.myorderlynk.app.domain.User;
import com.myorderlynk.app.domain.enums.UserRole;
import com.myorderlynk.app.dto.AuthDtos.AuthResponse;
import com.myorderlynk.app.dto.AuthDtos.ChangePasswordRequest;
import com.myorderlynk.app.dto.AuthDtos.LoginRequest;
import com.myorderlynk.app.dto.AuthDtos.RegisterRequest;
import com.myorderlynk.app.repo.UserRepository;
import com.myorderlynk.app.security.JwtService;
import com.myorderlynk.app.web.error.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class AuthService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository users, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
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
        return toResponse(user);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest req) {
        User user = users.findByEmailIgnoreCase(req.email())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Invalid email or password"));
        if (!passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
        }
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
    }

    @Transactional(readOnly = true)
    public AuthResponse me(UUID userId) {
        User user = users.findById(userId)
                .orElseThrow(() -> ApiException.notFound("User not found"));
        return new AuthResponse(null, user.getId(), user.getFullName(), user.getEmail(),
                user.getRole(), user.getVendorId());
    }

    private AuthResponse toResponse(User user) {
        String token = jwtService.issueToken(user);
        return new AuthResponse(token, user.getId(), user.getFullName(), user.getEmail(),
                user.getRole(), user.getVendorId());
    }
}
