package com.myorderlynk.app.common.enums;

/**
 * Vendor subscription tier. Each tier maps (via the admin-editable plan catalog) to a monthly fee
 * and a commission rate: lower commission costs more per month, so high-GMV vendors self-select up.
 * The materialized rate lives on {@code Vendor.commissionRate} (the effective rate used in fee math);
 * assigning a plan overwrites it from the catalog.
 */
public enum VendorPlan {
    STARTER,
    GROWTH,
    PRO
}
