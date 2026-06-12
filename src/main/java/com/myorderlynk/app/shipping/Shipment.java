package com.myorderlynk.app.shipping;

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
import java.time.Instant;
import java.util.UUID;

/**
 * A carrier shipment for an order: the rate chosen, the purchased label, and the latest tracking
 * state. One order may be re-rated/re-shipped, so several rows can share an {@code orderId}; the
 * most recent is the live one. The parcel weight/dimensions are snapshotted (in g/cm) so a label
 * can be re-priced even if the products are later edited.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "shipments", indexes = {
        @Index(name = "idx_shipment_order", columnList = "orderId"),
        @Index(name = "idx_shipment_tracking", columnList = "trackingNumber"),
        @Index(name = "idx_shipment_provider_shipment", columnList = "providerShipmentId")
})
public class Shipment extends BaseEntity {

    @Column(nullable = false)
    private UUID orderId;

    @Column(nullable = false)
    private UUID vendorId;

    /** Provider key that owns this shipment, e.g. "shippo". */
    @Column(nullable = false)
    private String provider;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ShipmentStatus status = ShipmentStatus.RATED;

    /** Provider's parent shipment id (Shippo shipment object_id). */
    private String providerShipmentId;

    /** Provider's id for the selected rate (used to buy the label). */
    private String rateId;

    /** Provider's transaction/label id once purchased. */
    private String transactionId;

    private String carrier;

    private String serviceLevel;

    /** Stable service token, e.g. "usps_priority"; survives rate refreshes. */
    private String serviceToken;

    @Column(precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(length = 8)
    private String currency = "CAD";

    private Integer estimatedDays;

    private String trackingNumber;

    @Column(length = 1024)
    private String trackingUrl;

    @Column(length = 1024)
    private String labelUrl;

    /** Last carrier status detail text seen on a tracking update. */
    @Column(length = 512)
    private String trackingStatusDetail;

    private Instant eta;

    // ---- Parcel snapshot (canonical units: grams + centimetres) ----
    @Column(precision = 12, scale = 4)
    private BigDecimal weightGrams;

    @Column(precision = 12, scale = 4)
    private BigDecimal lengthCm;

    @Column(precision = 12, scale = 4)
    private BigDecimal widthCm;

    @Column(precision = 12, scale = 4)
    private BigDecimal heightCm;
}