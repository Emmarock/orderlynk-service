package com.myorderlynk.app.shipping;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Unit of mass for product/parcel weight, provider-agnostic. Each constant knows how many
 * grams one of its units is, so weights expressed in different units can be aggregated into a
 * single parcel weight before being handed to a {@link ShippingProvider}.
 */
public enum WeightUnit {
    G(new BigDecimal("1")),
    KG(new BigDecimal("1000")),
    OZ(new BigDecimal("28.349523125")),
    LB(new BigDecimal("453.59237"));

    private final BigDecimal gramsPerUnit;

    WeightUnit(BigDecimal gramsPerUnit) {
        this.gramsPerUnit = gramsPerUnit;
    }

    /** Convert {@code value} of this unit to grams. */
    public BigDecimal toGrams(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value.multiply(gramsPerUnit);
    }

    /** Convert a gram amount into this unit, rounded to 4dp. */
    public BigDecimal fromGrams(BigDecimal grams) {
        if (grams == null) {
            return BigDecimal.ZERO;
        }
        return grams.divide(gramsPerUnit, 4, RoundingMode.HALF_UP);
    }
}