package com.myorderlynk.app.batch;

/** How a batch is shipped between origin and destination (batch-cargo spec §11.1). */
public enum ShippingMethod {
    AIR_CARGO,
    SEA_CARGO,
    DOMESTIC,
    OTHER
}
