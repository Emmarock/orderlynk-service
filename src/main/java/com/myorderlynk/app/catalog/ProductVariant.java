package com.myorderlynk.app.catalog;

import com.myorderlynk.app.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One purchasable option of a {@link Product} — a specific colour/size combination carrying its own
 * stock. A product either has no variants at all (simple product: stock lives on
 * {@link Product#getQuantityAvailable()}) or one row per combination the vendor actually sells, so
 * "Black / M" running out never blocks "White / L".
 *
 * <p>Both {@link #color} and {@link #size} are nullable: a product may vary by only one of them.
 * The pair is unique within a product.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "product_variants", indexes = @Index(name = "idx_product_variants_product", columnList = "product_id"))
public class ProductVariant extends BaseEntity {

    /** Owning product. The child owns the FK (mirrors Order/OrderItem), so inserts carry it directly. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id")
    private Product product;

    /** Colour for this option, or null when the product doesn't vary by colour. */
    @Column(length = 64)
    private String color;

    /** Size for this option, or null when the product doesn't vary by size. */
    @Column(length = 64)
    private String size;

    /** Stock for this specific combination. Zero means this option alone is sold out. */
    @Column(nullable = false)
    private int quantityAvailable = 0;

    /** Vendor-defined display order. */
    @Column(nullable = false)
    private int position = 0;

    /** Human label for messages and order snapshots, e.g. "Black / M". */
    public String label() {
        if (color != null && size != null) {
            return color + " / " + size;
        }
        return color != null ? color : (size != null ? size : "default");
    }

    /** Whether this option matches the shopper's (case-insensitive) selection. */
    public boolean matches(String selectedColor, String selectedSize) {
        return eq(color, selectedColor) && eq(size, selectedSize);
    }

    private static boolean eq(String a, String b) {
        String left = a == null ? "" : a.trim();
        String right = b == null ? "" : b.trim();
        return left.equalsIgnoreCase(right);
    }
}