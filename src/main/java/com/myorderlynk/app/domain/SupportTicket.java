package com.myorderlynk.app.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/** A support request a vendor raises with OrderLynk ("Message Us"). */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "support_tickets", indexes = @Index(name = "idx_support_vendor", columnList = "vendorId"))
public class SupportTicket extends BaseEntity {

    @Column(nullable = false)
    private UUID vendorId;

    /** User who raised the ticket. */
    @Column(nullable = false)
    private UUID userId;

    /** Free-form category, e.g. PAYMENT, ORDER, PRODUCT, ACCOUNT, TECHNICAL, OTHER. */
    @Column(nullable = false)
    private String category;

    @Column(nullable = false)
    private String subject;

    @Column(nullable = false, length = 4000)
    private String message;

    /** OPEN or RESOLVED. */
    @Column(nullable = false)
    private String status = "OPEN";
}