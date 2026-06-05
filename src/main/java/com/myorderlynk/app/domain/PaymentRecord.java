package com.myorderlynk.app.domain;

import com.myorderlynk.app.domain.enums.PaymentMethod;
import com.myorderlynk.app.domain.enums.PaymentStatus;
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

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "payment_records", indexes = @Index(name = "idx_payment_order", columnList = "orderId"))
public class PaymentRecord extends BaseEntity {

    @Column(nullable = false)
    private UUID orderId;

    private UUID customerUserId;

    @Column(nullable = false)
    private UUID vendorId;

    @Column(nullable = false)
    private BigDecimal amountPaid;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentMethod paymentMethod = PaymentMethod.OTHER;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus paymentStatus = PaymentStatus.PAID;

    private String transactionReference;

    /** Tokenized reference only — never store raw card data (PRD §17). */
    private String stripePaymentId;

    private Instant paidDate;

    private String refundStatus;
}
