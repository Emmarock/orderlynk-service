package com.myorderlynk.app.payment;

import com.myorderlynk.app.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Deduplication record for inbound payment-service events. The payment-service
 * delivers at-least-once (transactional outbox + retry), so each event id is
 * applied exactly once.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "processed_payment_events",
        uniqueConstraints = @UniqueConstraint(name = "uq_processed_payment_event", columnNames = "eventId"))
public class ProcessedPaymentEvent extends BaseEntity {

    @Column(nullable = false)
    private String eventId;

    private String eventType;
}