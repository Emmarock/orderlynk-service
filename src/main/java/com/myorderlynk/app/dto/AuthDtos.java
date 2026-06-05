package com.myorderlynk.app.dto;

import com.myorderlynk.app.domain.enums.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record RegisterRequest(
            @NotBlank String fullName,
            @Email @NotBlank String email,
            @NotBlank @Size(min = 6, max = 100) String password,
            String phone,
            String city,
            String country) {
    }

    public record LoginRequest(
            @Email @NotBlank String email,
            @NotBlank String password) {
    }

    public record ChangePasswordRequest(
            @NotBlank String currentPassword,
            @NotBlank @Size(min = 6, max = 100) String newPassword) {
    }

    public record AuthResponse(
            String token,
            UUID userId,
            String fullName,
            String email,
            UserRole role,
            UUID vendorId) {
    }
}
