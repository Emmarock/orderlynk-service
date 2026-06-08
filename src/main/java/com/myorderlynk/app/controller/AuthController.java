package com.myorderlynk.app.controller;

import com.myorderlynk.app.dto.AuthDtos.AuthResponse;
import com.myorderlynk.app.dto.AuthDtos.ChangeEmailRequest;
import com.myorderlynk.app.dto.AuthDtos.ChangePasswordRequest;
import com.myorderlynk.app.dto.AuthDtos.ForgotPasswordRequest;
import com.myorderlynk.app.dto.AuthDtos.LoginRequest;
import com.myorderlynk.app.dto.AuthDtos.RegisterRequest;
import com.myorderlynk.app.dto.AuthDtos.ResetPasswordRequest;
import com.myorderlynk.app.dto.AuthDtos.UpdateProfileRequest;
import com.myorderlynk.app.dto.AuthDtos.VerifyEmailRequest;
import com.myorderlynk.app.security.CurrentUser;
import com.myorderlynk.app.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import com.myorderlynk.app.security.access.IsAuthenticated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final CurrentUser currentUser;

    public AuthController(AuthService authService, CurrentUser currentUser) {
        this.authService = authService;
        this.currentUser = currentUser;
    }

    @PostMapping("/register")
    public AuthResponse register(@Valid @RequestBody RegisterRequest req) {
        return authService.register(req);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest req) {
        return authService.login(req);
    }

    @GetMapping("/me")
    public AuthResponse me() {
        return authService.me(currentUser.require().userId());
    }

    /** Authenticated users (admin, vendor, customer) rotate their own password. */
    @PostMapping("/change-password")
    @IsAuthenticated
    public ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordRequest req) {
        authService.changePassword(currentUser.require().userId(), req);
        return ResponseEntity.noContent().build();
    }

    /** Update the signed-in user's profile (name, phone, city, country). */
    @PutMapping("/profile")
    @IsAuthenticated
    public AuthResponse updateProfile(@Valid @RequestBody UpdateProfileRequest req) {
        return authService.updateProfile(currentUser.require().userId(), req);
    }

    /** Change the signed-in user's email; returns a refreshed token. */
    @PostMapping("/change-email")
    @IsAuthenticated
    public AuthResponse changeEmail(@Valid @RequestBody ChangeEmailRequest req) {
        return authService.changeEmail(currentUser.require().userId(), req);
    }

    /** Confirm an email address from a verification link (public). */
    @PostMapping("/verify-email")
    public ResponseEntity<Void> verifyEmail(@Valid @RequestBody VerifyEmailRequest req) {
        authService.verifyEmail(req.token());
        return ResponseEntity.noContent().build();
    }

    /** Re-send the verification email to the signed-in user. */
    @PostMapping("/resend-verification")
    @IsAuthenticated
    public ResponseEntity<Void> resendVerification() {
        authService.resendVerification(currentUser.require().userId());
        return ResponseEntity.noContent().build();
    }

    /** Begin a password reset (public). Always 204 — never reveals whether the email exists. */
    @PostMapping("/forgot-password")
    public ResponseEntity<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest req) {
        authService.requestPasswordReset(req.email());
        return ResponseEntity.noContent().build();
    }

    /** Complete a password reset from a reset link (public). */
    @PostMapping("/reset-password")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest req) {
        authService.resetPassword(req.token(), req.newPassword());
        return ResponseEntity.noContent().build();
    }
}
