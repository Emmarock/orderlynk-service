package com.myorderlynk.app.booking;

import com.myorderlynk.app.common.BaseEntity;
import com.myorderlynk.app.common.enums.PaymentMethod;
import com.myorderlynk.app.common.enums.PaymentStatus;
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

/** A deposit / balance / full / refund payment recorded against a booking (PRD §13 BookingPayment). */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "booking_payments",
        indexes = @Index(name = "idx_bookingpay_booking", columnList = "bookingId"))
public class BookingPayment extends BaseEntity {

    @Column(nullable = false)
    private UUID bookingId;

    @Column(nullable = false)
    private UUID vendorId;

    private UUID customerUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BookingPaymentType paymentType;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status = PaymentStatus.PAID;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentMethod method = PaymentMethod.OTHER;

    private String transactionReference;

    private Instant paidAt;
}
