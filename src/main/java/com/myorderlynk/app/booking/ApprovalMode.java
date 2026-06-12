package com.myorderlynk.app.booking;

/** How incoming booking requests are handled (PRD §9 / §7 step 5). */
public enum ApprovalMode {
    /** Every request stays {@code REQUESTED} until the provider approves or rejects it. */
    MANUAL,
    /** Eligible requests are approved automatically when the slot is open. */
    AUTO
}
