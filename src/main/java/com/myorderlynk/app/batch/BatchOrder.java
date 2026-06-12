package com.myorderlynk.app.batch;

import com.myorderlynk.app.common.Address;
import com.myorderlynk.app.common.BaseEntity;
import com.myorderlynk.app.common.enums.FulfillmentType;
import com.myorderlynk.app.common.enums.PaymentStatus;
import com.myorderlynk.app.common.enums.SourceChannel;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A customer's purchase of batch products into a {@link Batch} (batch-cargo spec §11 "Batch Product
 * Order"). Its own aggregate with the {@link BatchOrderStatus} cycle — kept separate from regular
 * product orders so batch fulfillment isn't mixed with local fulfillment.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "batch_orders", indexes = {
        @Index(name = "idx_batchorder_public_id", columnList = "publicOrderId", unique = true),
        @Index(name = "idx_batchorder_batch", columnList = "batchId"),
        @Index(name = "idx_batchorder_vendor", columnList = "vendorId"),
        @Index(name = "idx_batchorder_customer", columnList = "customerUserId")
})
public class BatchOrder extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String publicOrderId;

    @Column(nullable = false)
    private UUID batchId;

    @Column(nullable = false)
    private UUID vendorId;

    private UUID customerUserId;

    @Column(nullable = false)
    private String customerName;

    @Column(nullable = false)
    private String customerPhone;

    private String customerEmail;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<BatchOrderItem> items = new ArrayList<>();

    /** Pickup at destination vs local delivery (batch-cargo §7.1). */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FulfillmentType fulfillmentType = FulfillmentType.LOCAL_PICKUP;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "houseNumber", column = @Column(name = "customer_house_number")),
            @AttributeOverride(name = "street", column = @Column(name = "customer_street")),
            @AttributeOverride(name = "city", column = @Column(name = "customer_city")),
            @AttributeOverride(name = "state", column = @Column(name = "customer_state")),
            @AttributeOverride(name = "postcode", column = @Column(name = "customer_postcode")),
            @AttributeOverride(name = "country", column = @Column(name = "customer_country"))
    })
    private Address deliveryAddress = new Address();

    @Column(nullable = false)
    private BigDecimal productSubtotal = BigDecimal.ZERO;

    @Column(nullable = false)
    private BigDecimal deliveryFee = BigDecimal.ZERO;

    @Column(nullable = false)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(nullable = false)
    private BigDecimal amountPaid = BigDecimal.ZERO;

    @Column(nullable = false)
    private BigDecimal refundedAmount = BigDecimal.ZERO;

    @Column(nullable = false)
    private String currency = "CAD";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus paymentStatus = PaymentStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BatchOrderStatus status = BatchOrderStatus.ORDER_RECEIVED;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SourceChannel sourceChannel = SourceChannel.VENDOR_LINK;

    private String pickupCode;

    @Column(length = 2000)
    private String notes;

    public void addItem(BatchOrderItem item) {
        item.setOrder(this);
        items.add(item);
    }

    public BigDecimal balanceDue() {
        BigDecimal due = totalAmount.subtract(amountPaid);
        return due.signum() < 0 ? BigDecimal.ZERO : due;
    }
}
