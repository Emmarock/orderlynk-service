package com.myorderlynk.app.order;
import com.myorderlynk.app.common.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "order_items")
public class OrderItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id")
    private Order order;

    @Column(nullable = false)
    private UUID productId;

    @Column(nullable = false)
    private UUID vendorId;

    /** Snapshot of product name at order time, immune to later product edits. */
    @Column(nullable = false)
    private String productNameSnapshot;

    /** Snapshot of the colour the customer selected, or null if the product has no colour option. */
    @Column(length = 64)
    private String selectedColor;

    /** Snapshot of the size the customer selected, or null if the product has no size option. */
    @Column(length = 64)
    private String selectedSize;

    @Column(nullable = false)
    private int quantity;

    @Column(nullable = false)
    private BigDecimal unitPrice;

    @Column(nullable = false)
    private BigDecimal lineTotal;
}
