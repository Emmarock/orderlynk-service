package com.myorderlynk.app.batch;

/** Availability of a product within a batch (batch-cargo spec §11.2). */
public enum BatchProductStatus {
    AVAILABLE,
    SOLD_OUT,
    HIDDEN
}
