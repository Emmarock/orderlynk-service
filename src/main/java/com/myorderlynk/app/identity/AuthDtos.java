package com.myorderlynk.app.identity;

import com.myorderlynk.app.common.enums.UserRole;
import com.myorderlynk.app.identity.AddressDtos.AddressDto;
import com.myorderlynk.app.validation.FieldMatch;
import com.myorderlynk.app.validation.StrongPassword;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

public final class AuthDtos {

    private AuthDtos() {
    }

    @FieldMatch(field = "password", fieldMatch = "confirmPassword",
            message = "Password and confirmation do not match")
    public record RegisterRequest(
            @NotBlank String fullName,
            @Email @NotBlank String email,
            @StrongPassword String password,
            @NotBlank String confirmPassword,
            String phone,
            String city,
            String country,
            @Valid AddressDto address) {
    }

    public record LoginRequest(
            @Email @NotBlank String email,
            @NotBlank String password) {
    }

    public record ChangePasswordRequest(
            @NotBlank String currentPassword,
            @StrongPassword String newPassword) {
    }

    public record UpdateProfileRequest(
            @NotBlank String fullName,
            String phone,
            String city,
            String country) {
    }

    public record ChangeEmailRequest(
            @Email @NotBlank String newEmail,
            @NotBlank String currentPassword) {
    }

    public record VerifyEmailRequest(
            @NotBlank String token) {
    }

    public record ForgotPasswordRequest(
            @Email @NotBlank String email) {
    }

    public record ResetPasswordRequest(
            @NotBlank String token,
            @StrongPassword String newPassword) {
    }

    /** Claim an invited account (created from a guest order): set the first password from the email link. */
    @FieldMatch(field = "password", fieldMatch = "confirmPassword",
            message = "Password and confirmation do not match")
    public record AcceptInviteRequest(
            @NotBlank String token,
            @StrongPassword String password,
            @NotBlank String confirmPassword) {
    }

    public record AuthResponse(
            String token,
            UUID userId,
            String fullName,
            String email,
            UserRole role,
            UUID vendorId,
            boolean emailVerified) {
    }
}
