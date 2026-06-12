package com.myorderlynk.app.booking;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/** Request/response records for the service catalog, availability and provider profile. */
public final class ServiceDtos {

    private ServiceDtos() {
    }

    // ---- Provider profile ----

    public record ProfileRequest(
            Boolean serviceEnabled,
            String bio,
            String serviceArea,
            ServiceLocationType locationType,
            ApprovalMode approvalMode,
            String cancellationPolicy,
            String depositPolicy,
            String businessHoursSummary,
            @PositiveOrZero Integer leadTimeHours,
            @PositiveOrZero Integer bufferMinutes,
            @Positive Integer maxAdvanceDays,
            @Positive Integer defaultCapacity,
            @PositiveOrZero Integer slotHoldMinutes,
            String timezone) {
    }

    public record ProfileResponse(
            UUID id,
            UUID vendorId,
            boolean serviceEnabled,
            String bio,
            String serviceArea,
            ServiceLocationType locationType,
            ApprovalMode approvalMode,
            String cancellationPolicy,
            String depositPolicy,
            String businessHoursSummary,
            int leadTimeHours,
            int bufferMinutes,
            int maxAdvanceDays,
            int defaultCapacity,
            int slotHoldMinutes,
            String timezone) {
    }

    // ---- Services ----

    public record ServiceRequest(
            @NotBlank String name,
            ServiceCategory category,
            String description,
            @NotNull @DecimalMin("0.0") BigDecimal basePrice,
            String currency,
            @Positive int durationMinutes,
            String imageUrl,
            DepositType depositType,
            @PositiveOrZero BigDecimal depositValue,
            @PositiveOrZero BigDecimal taxRate,
            Boolean active) {
    }

    public record ServiceResponse(
            UUID id,
            UUID vendorId,
            String name,
            ServiceCategory category,
            String description,
            BigDecimal basePrice,
            String currency,
            int durationMinutes,
            String imageUrl,
            DepositType depositType,
            BigDecimal depositValue,
            BigDecimal depositAmount,
            BigDecimal taxRate,
            boolean active,
            List<AddOnResponse> addOns) {
    }

    // ---- Add-ons ----

    public record AddOnRequest(
            @NotBlank String name,
            @NotNull @PositiveOrZero BigDecimal priceDelta,
            @PositiveOrZero int durationDelta,
            Boolean required,
            @Positive Integer maxSelection,
            Boolean active) {
    }

    /** Public URL of an uploaded service image. */
    public record ImageUploadResponse(String url) {
    }

    public record AddOnResponse(
            UUID id,
            UUID serviceId,
            String name,
            BigDecimal priceDelta,
            int durationDelta,
            boolean required,
            int maxSelection,
            boolean active) {
    }

    // ---- Availability rules ----

    public record AvailabilityRuleRequest(
            @NotNull DayOfWeek dayOfWeek,
            @NotNull LocalTime startTime,
            @NotNull LocalTime endTime,
            @Positive Integer capacity,
            @PositiveOrZero Integer bufferMinutes,
            @PositiveOrZero Integer leadTimeHours,
            Boolean active) {
    }

    public record AvailabilityRuleResponse(
            UUID id,
            UUID vendorId,
            DayOfWeek dayOfWeek,
            LocalTime startTime,
            LocalTime endTime,
            Integer capacity,
            Integer bufferMinutes,
            Integer leadTimeHours,
            boolean active) {
    }

    // ---- Blocked slots ----

    public record BlockedSlotRequest(
            @NotNull Instant startDatetime,
            @NotNull Instant endDatetime,
            String reason) {
    }

    public record BlockedSlotResponse(
            UUID id,
            UUID vendorId,
            Instant startDatetime,
            Instant endDatetime,
            String reason) {
    }

    // ---- Availability query ----

    /** A bookable slot for a service on a date. */
    public record SlotResponse(
            Instant start,
            Instant end,
            int remainingCapacity) {
    }

    public record DayAvailabilityResponse(
            UUID serviceId,
            String date,
            int durationMinutes,
            List<SlotResponse> slots) {
    }
}
