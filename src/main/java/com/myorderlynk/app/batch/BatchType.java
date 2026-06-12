package com.myorderlynk.app.batch;

/** What a batch carries (batch-cargo spec §11.1). */
public enum BatchType {
    /** Vendor-led products sold into a shipment cycle. */
    PRODUCT_BATCH,
    /** Customer-owned items shipped per-kg (Send My Items). */
    CARGO_BATCH,
    /** Both batch products and customer shipment requests. */
    HYBRID_BATCH
}
