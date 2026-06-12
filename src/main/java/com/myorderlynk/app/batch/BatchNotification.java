package com.myorderlynk.app.batch;

import com.myorderlynk.app.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * A batch/cargo notification record (batch-cargo spec §14). MVP delivery is persisted and logged;
 * real email/WhatsApp dispatch reuses the existing seam — mirrors {@code BookingNotification}.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "batch_notifications",
        indexes = @Index(name = "idx_batchnotif_subject", columnList = "subjectId"))
public class BatchNotification extends BaseEntity {

    /** The batch, batch-order or shipment-request id this notification concerns. */
    @Column(nullable = false)
    private UUID subjectId;

    /** BATCH, BATCH_ORDER or SHIPMENT_REQUEST. */
    @Column(nullable = false, length = 20)
    private String subjectType;

    /** PROVIDER or CUSTOMER. */
    @Column(nullable = false)
    private String recipientRole;

    @Column(nullable = false)
    private String channel;

    private String recipient;

    @Column(nullable = false)
    private String template;

    @Column(length = 2000)
    private String body;

    private Instant sentAt;

    @Column(nullable = false)
    private String status = "SENT";
}
