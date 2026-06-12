package com.myorderlynk.app.batch;

import com.myorderlynk.app.common.BaseEntity;
import com.myorderlynk.app.common.enums.BatchStatus;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A batch/shipment cycle (batch-cargo spec §11.1): a window with origin → destination, open/close
 * dates, shipping method, and — for cargo — a per-kg rate. Owns batch products and shipment requests.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "batches", indexes = @Index(name = "idx_batch_vendor", columnList = "vendorId"))
public class Batch extends BaseEntity {

    @Column(nullable = false)
    private UUID vendorId;

    @Column(nullable = false)
    private String batchName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BatchType batchType = BatchType.PRODUCT_BATCH;

    private String route;

    private String originCountry;

    private String originCity;

    private String destinationCountry;

    private String destinationCity;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private ShippingMethod shippingMethod = ShippingMethod.AIR_CARGO;

    private LocalDate openDate;

    private LocalDate closeDate;

    private LocalDate estimatedDeparture;

    private LocalDate estimatedArrival;

    /** Cargo rate per kilogram (for shipment requests); optional for product-only batches. */
    @Column(precision = 19, scale = 2)
    private BigDecimal ratePerKg;

    @Column(precision = 19, scale = 2)
    private BigDecimal handlingFee = BigDecimal.ZERO;

    @Column(nullable = false)
    private String currency = "CAD";

    private String pickupLocation;

    /** Origin drop-off locations customers can use. */
    @ElementCollection
    @CollectionTable(name = "batch_collection_points", joinColumns = @JoinColumn(name = "batch_id"))
    @Column(name = "location", length = 500)
    private List<String> collectionPoints = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BatchStatus batchStatus = BatchStatus.DRAFT;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BatchVisibility visibility = BatchVisibility.DRAFT;

    @Column(length = 2000)
    private String notes;

    /** True once the order-close date has passed (computed, not persisted). */
    public boolean isPastClose() {
        return closeDate != null && LocalDate.now().isAfter(closeDate);
    }
}
