package com.myorderlynk.app.batch;

import com.myorderlynk.app.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * A catalog {@code Product} attached to a {@link Batch} with batch-specific price, quantity and
 * status (batch-cargo spec §11.2). Products are reused across batches — never recreated. Name/image
 * are snapshotted so the batch listing is stable even if the catalog product later changes.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "batch_products", indexes = {
        @Index(name = "idx_batchproduct_batch", columnList = "batchId"),
        @Index(name = "idx_batchproduct_vendor", columnList = "vendorId")
})
public class BatchProduct extends BaseEntity {

    @Column(nullable = false)
    private UUID batchId;

    @Column(nullable = false)
    private UUID vendorId;

    /** The master catalog product this is derived from. */
    @Column(nullable = false)
    private UUID productId;

    @Column(nullable = false)
    private String nameSnapshot;

    private String imageUrlSnapshot;

    @Column(length = 2000)
    private String description;

    /** Batch-specific price (overrides the catalog price). */
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal batchPrice;

    @Column(nullable = false)
    private String currency = "CAD";

    /** Max units sellable in this batch; 0 = unlimited. */
    @Column(nullable = false)
    private int quantityLimit = 0;

    @Column(nullable = false)
    private int soldQuantity = 0;

    @Column(nullable = false)
    private int minOrderQuantity = 1;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BatchProductStatus status = BatchProductStatus.AVAILABLE;

    @Column(length = 1000)
    private String batchNotes;

    /** Units still sellable, or null when unlimited. */
    public Integer remaining() {
        return quantityLimit <= 0 ? null : Math.max(0, quantityLimit - soldQuantity);
    }
}
