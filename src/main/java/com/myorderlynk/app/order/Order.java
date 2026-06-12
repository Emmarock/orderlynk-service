package com.myorderlynk.app.order;
import com.myorderlynk.app.common.Address;
import com.myorderlynk.app.common.BaseEntity;

import com.myorderlynk.app.common.enums.FulfillmentStatus;
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

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "orders", indexes = {
        @Index(name = "idx_order_public_id", columnList = "publicOrderId", unique = true),
        @Index(name = "idx_order_vendor", columnList = "vendorId")
})
public class Order extends BaseEntity {

    /** Human-friendly id, format OB-YYMMDD-RANDOM (see Appendix A). */
    @Column(nullable = false, unique = true)
    private String publicOrderId;

    /** Optional logged-in customer user id; orders can be placed as a guest. */
    private UUID customerUserId;

    @Column(nullable = false)
    private String customerName;

    @Column(nullable = false)
    private String customerPhone;

    private String customerEmail;

    /** Delivery address snapshot for this order (reuses the existing customer_* columns). */
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
    private UUID vendorId;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<OrderItem> items = new ArrayList<>();

    // ---- Money breakdown (Appendix A fee logic) ----
    @Column(nullable = false)
    private BigDecimal productSubtotal = BigDecimal.ZERO;

    @Column(nullable = false)
    private BigDecimal logisticsFee = BigDecimal.ZERO;

    @Column(nullable = false)
    private BigDecimal platformFee = BigDecimal.ZERO;

    @Column(nullable = false)
    private BigDecimal processingFee = BigDecimal.ZERO;

    @Column(nullable = false)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(nullable = false)
    private BigDecimal vendorPayable = BigDecimal.ZERO;

    @Column(nullable = false)
    private BigDecimal logisticsPayable = BigDecimal.ZERO;

    @Column(nullable = false)
    private BigDecimal platformRevenue = BigDecimal.ZERO;

    @Column(nullable = false)
    private BigDecimal refundedAmount = BigDecimal.ZERO;

    private String currency = "CAD";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus paymentStatus = PaymentStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FulfillmentType fulfillmentType = FulfillmentType.LOCAL_PICKUP;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FulfillmentStatus fulfillmentStatus = FulfillmentStatus.ORDER_RECEIVED;

    private UUID batchId;

    private String pickupCode;

    @Enumerated(EnumType.STRING)
    private SourceChannel sourceChannel = SourceChannel.VENDOR_LINK;

    private String campaign;

    @Column(length = 2000)
    private String notes;

    public void addItem(OrderItem item) {
        item.setOrder(this);
        items.add(item);
    }
}
