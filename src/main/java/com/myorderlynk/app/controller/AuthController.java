package com.myorderlynk.app.controller;

import com.myorderlynk.app.dto.AuthDtos.AuthResponse;
import com.myorderlynk.app.dto.AuthDtos.ChangePasswordRequest;
import com.myorderlynk.app.dto.AuthDtos.LoginRequest;
import com.myorderlynk.app.dto.AuthDtos.RegisterRequest;
import com.myorderlynk.app.security.CurrentUser;
import com.myorderlynk.app.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
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
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordRequest req) {
        authService.changePassword(currentUser.require().userId(), req);
        return ResponseEntity.noContent().build();
    }
}
