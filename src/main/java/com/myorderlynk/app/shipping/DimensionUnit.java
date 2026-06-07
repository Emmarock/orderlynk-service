package com.myorderlynk.app.shipping;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Unit of length for parcel dimensions, provider-agnostic. Each constant knows how many
 * centimetres one of its units is, so dimensions in mixed units can be normalised before
 * being handed to a {@link ShippingProvider}.
 */
public enum DimensionUnit {
    MM(new BigDecimal("0.1")),
    CM(new BigDecimal("1")),
    M(new BigDecimal("100")),
    IN(new BigDecimal("2.54")),
    FT(new BigDecimal("30.48")),
    YD(new BigDecimal("91.44"));

    private final BigDecimal cmPerUnit;

    DimensionUnit(BigDecimal cmPerUnit) {
        this.cmPerUnit = cmPerUnit;
    }

    /** Convert {@code value} of this unit to centimetres. */
    public BigDecimal toCentimeters(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value.multiply(cmPerUnit);
    }

    /** Convert a centimetre amount into this unit, rounded to 4dp. */
    public BigDecimal fromCentimeters(BigDecimal cm) {
        if (cm == null) {
            return BigDecimal.ZERO;
        }
        return cm.divide(cmPerUnit, 4, RoundingMode.HALF_UP);
    }
}