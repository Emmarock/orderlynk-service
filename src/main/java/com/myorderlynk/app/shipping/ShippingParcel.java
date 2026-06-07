package com.myorderlynk.app.shipping;

import java.math.BigDecimal;

/**
 * Provider-neutral parcel: physical weight and dimensions. A shipment may contain several.
 * Units are carried explicitly so a provider can translate them to its own enum set.
 */
public record ShippingParcel(
        BigDecimal length,
        BigDecimal width,
        BigDecimal height,
        DimensionUnit distanceUnit,
        BigDecimal weight,
        WeightUnit massUnit) {
}