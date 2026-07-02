package com.myorderlynk.app.common.enums;

/**
 * Who collects VAT on behalf of the government for a vendor's sales.
 * <ul>
 *   <li>{@link #VENDOR} — the default: VAT is added to the vendor's payout and the vendor remits it.</li>
 *   <li>{@link #PLATFORM} — the platform holds the collected VAT as a liability and remits it.</li>
 * </ul>
 */
public enum VatCollector {
    VENDOR,
    PLATFORM
}
