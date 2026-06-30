package com.myorderlynk.app.order;

import com.myorderlynk.app.common.enums.FulfillmentType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;

/** DTOs for the admin-configurable platform fee policy ({@code /api/admin/fee-settings}). */
public final class FeeSettingsDtos {

    private FeeSettingsDtos() {
    }

    /** Current fee policy, including when it was last changed. */
    public record FeeSettingsResponse(
            BigDecimal serviceFeeRate,
            BigDecimal processingRate,
            BigDecimal processingFixed,
            BigDecimal processingBufferRate,
            boolean grossUpProcessing,
            BigDecimal logisticsMarginRate,
            BigDecimal logisticsMarkupFlat,
            BigDecimal taxRate,
            BigDecimal instantPayoutFeeRate,
            BigDecimal cargoHandlingFeeRate,
            BigDecimal featuredPlacementFee,
            int featuredPlacementDays,
            String featuredPlacementCurrency,
            Map<FulfillmentType, BigDecimal> logistics,
            Instant updatedAt) {
    }

    /**
     * Full replacement of the fee policy. Rate fields are fractions in [0, 1]; money fields are
     * non-negative amounts. {@code logistics} maps each fulfillment type to its flat base fee.
     */
    public record UpdateFeeSettingsRequest(
            @NotNull @DecimalMin("0.0") @DecimalMax("1.0") BigDecimal serviceFeeRate,
            @NotNull @DecimalMin("0.0") @DecimalMax("1.0") BigDecimal processingRate,
            @NotNull @PositiveOrZero BigDecimal processingFixed,
            @NotNull @DecimalMin("0.0") @DecimalMax("1.0") BigDecimal processingBufferRate,
            boolean grossUpProcessing,
            @NotNull @DecimalMin("0.0") @DecimalMax("1.0") BigDecimal logisticsMarginRate,
            @NotNull @PositiveOrZero BigDecimal logisticsMarkupFlat,
            @NotNull @DecimalMin("0.0") @DecimalMax("1.0") BigDecimal taxRate,
            @NotNull @DecimalMin("0.0") @DecimalMax("1.0") BigDecimal instantPayoutFeeRate,
            @NotNull @DecimalMin("0.0") @DecimalMax("1.0") BigDecimal cargoHandlingFeeRate,
            @NotNull @PositiveOrZero BigDecimal featuredPlacementFee,
            @Positive int featuredPlacementDays,
            @NotBlank String featuredPlacementCurrency,
            Map<FulfillmentType, @NotNull @PositiveOrZero BigDecimal> logistics) {
    }

    public static FeeSettingsResponse toResponse(FeeSettings s) {
        return new FeeSettingsResponse(
                s.getServiceFeeRate(),
                s.getProcessingRate(),
                s.getProcessingFixed(),
                s.getProcessingBufferRate(),
                s.isGrossUpProcessing(),
                s.getLogisticsMarginRate(),
                s.getLogisticsMarkupFlat(),
                s.getTaxRate(),
                s.getInstantPayoutFeeRate(),
                s.getCargoHandlingFeeRate(),
                s.getFeaturedPlacementFee(),
                s.getFeaturedPlacementDays(),
                s.getFeaturedPlacementCurrency(),
                new EnumMap<>(s.getLogistics()),
                s.getUpdatedAt());
    }
}