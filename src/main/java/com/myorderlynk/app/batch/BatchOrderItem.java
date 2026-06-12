package com.myorderlynk.app.batch;

import com.myorderlynk.app.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

/** A line item on a {@link BatchOrder} — a batch product + quantity, snapshotted at order time. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "batch_order_items")
public class BatchOrderItem extends BaseEntity {

    @ManyToOne(optional = false)
    @JoinColumn(name = "batch_order_id", nullable = false)
    private BatchOrder order;

    @Column(nullable = false)
    private UUID batchProductId;

    @Column(nullable = false)
    private String productNameSnapshot;

    @Column(nullable = false)
    private int quantity;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal unitPrice;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal lineTotal;
}
