package com.myorderlynk.app.booking;

/** Deposit rule for a service (PRD §10). The deposit amount is derived from this and the service price. */
public enum DepositType {
    /** No upfront payment; booking confirms on approval. */
    NONE,
    /** Customer pays a fixed amount to lock the appointment (deposit value = amount). */
    FIXED,
    /** Customer pays a percentage of the service price (deposit value = percent, 0–100). */
    PERCENTAGE,
    /** Customer pays the full service price upfront. */
    FULL
}
