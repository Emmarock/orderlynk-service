package com.myorderlynk.app.support;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

/** DTOs for vendor support ("Message Us") tickets. */
public final class SupportDtos {

    private SupportDtos() {
    }

    public record SupportRequest(
            @NotBlank @Size(max = 40) String category,
            @NotBlank @Size(max = 200) String subject,
            @NotBlank @Size(max = 4000) String message) {
    }

    public record SupportTicketResponse(
            UUID id,
            String category,
            String subject,
            String message,
            String status,
            Instant createdAt) {
    }
}