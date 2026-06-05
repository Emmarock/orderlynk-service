package com.myorderlynk.app.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * Immutable audit trail for every payment and fulfillment status change
 * (PRD §17: "Every payment and fulfillment status change should be logged").
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "status_change_logs", indexes = @Index(name = "idx_statuslog_order", columnList = "orderId"))
public class StatusChangeLog extends BaseEntity {

    @Column(nullable = false)
    private UUID orderId;

    /** PAYMENT or FULFILLMENT. */
    @Column(nullable = false)
    private String statusType;

    private String fromStatus;

    @Column(nullable = false)
    private String toStatus;

    /** Identifier of the actor who triggered the change (user id, "SYSTEM", etc). */
    private String changedBy;

    private String note;
}
