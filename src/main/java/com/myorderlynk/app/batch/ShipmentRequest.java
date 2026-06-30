package com.myorderlynk.app.batch;

import com.myorderlynk.app.common.BaseEntity;
import com.myorderlynk.app.common.enums.PaymentStatus;
import com.myorderlynk.app.common.enums.SourceChannel;
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
import java.math.RoundingMode;
import java.util.UUID;

/**
 * A customer-supplied "Send My Items" cargo request into a {@link Batch} (batch-cargo spec §11.3).
 * Priced per-kg on the actual weight recorded at the collection point, then invoiced and paid.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "shipment_requests", indexes = {
        @Index(name = "idx_shipreq_public_id", columnList = "publicRequestId", unique = true),
        @Index(name = "idx_shipreq_batch", columnList = "batchId"),
        @Index(name = "idx_shipreq_vendor", columnList = "vendorId"),
        @Index(name = "idx_shipreq_customer", columnList = "customerUserId")
})
public class ShipmentRequest extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String publicRequestId;

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

    @Column(nullable = false, length = 2000)
    private String itemDescription;

    @Column(nullable = false)
    private int packageCount = 1;

    @Column(precision = 12, scale = 3)
    private BigDecimal estimatedWeight;

    /** Recorded by cargo/vendor staff at the collection point; drives the final charge. */
    @Column(precision = 12, scale = 3)
    private BigDecimal actualWeight;

    /** Default copied from the batch, editable by the vendor. */
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal ratePerKg = BigDecimal.ZERO;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal handlingFee = BigDecimal.ZERO;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal deliveryFee = BigDecimal.ZERO;

    /** Platform cargo handling/sourcing fee, added on top of the base charge; set from fee settings. */
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal platformCargoFee = BigDecimal.ZERO;

    /** baseCharge + platform cargo fee; 0 until weighed. */
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal totalCharge = BigDecimal.ZERO;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amountPaid = BigDecimal.ZERO;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal refundedAmount = BigDecimal.ZERO;

    @Column(nullable = false)
    private String currency = "CAD";

    @Column(precision = 19, scale = 2)
    private BigDecimal declaredValue;

    @Column(nullable = false)
    private boolean restrictedItemsConfirmed = false;

    private String originDropOffLocation;

    private String destinationLocation;

    /** PICKUP or DELIVERY at destination. */
    @Column(length = 20)
    private String deliveryPreference = "PICKUP";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus paymentStatus = PaymentStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ShipmentRequestStatus status = ShipmentRequestStatus.REQUEST_CREATED;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SourceChannel sourceChannel = SourceChannel.VENDOR_LINK;

    private String pickupCode;

    @Column(length = 2000)
    private String notes;

    /** Vendor/batch charge before the platform fee: (weight × ratePerKg) + handling + delivery. */
    public BigDecimal baseCharge() {
        BigDecimal weight = actualWeight != null ? actualWeight : estimatedWeight;
        if (weight == null) {
            weight = BigDecimal.ZERO;
        }
        return weight.multiply(ratePerKg)
                .add(handlingFee).add(deliveryFee)
                .setScale(2, RoundingMode.HALF_UP);
    }

    /** Recompute {@link #totalCharge}: the base charge plus the platform cargo fee. */
    public BigDecimal computeCharge() {
        return baseCharge().add(platformCargoFee).setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal balanceDue() {
        BigDecimal due = totalCharge.subtract(amountPaid);
        return due.signum() < 0 ? BigDecimal.ZERO : due;
    }
}
