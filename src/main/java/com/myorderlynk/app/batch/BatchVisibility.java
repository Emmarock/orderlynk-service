package com.myorderlynk.app.batch;

/** Who can discover a batch (batch-cargo spec §11.1). */
public enum BatchVisibility {
    /** Not visible to customers yet. */
    DRAFT,
    /** Reachable only via its direct link. */
    PRIVATE_LINK,
    /** Listed on the public marketplace. */
    MARKETPLACE
}
