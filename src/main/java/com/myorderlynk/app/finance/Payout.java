package com.myorderlynk.app.finance;
import com.myorderlynk.app.common.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "payouts", indexes = @Index(name = "idx_payout_vendor", columnList = "vendorId"))
public class Payout extends BaseEntity {

    @Column(nullable = false)
    private UUID vendorId;

    @Column(nullable = false)
    private LocalDate periodStart;

    @Column(nullable = false)
    private LocalDate periodEnd;

    @Column(nullable = false)
    private BigDecimal grossSales = BigDecimal.ZERO;

    @Column(nullable = false)
    private BigDecimal platformFees = BigDecimal.ZERO;

    @Column(nullable = false)
    private BigDecimal logisticsFees = BigDecimal.ZERO;

    @Column(nullable = false)
    private BigDecimal refunds = BigDecimal.ZERO;

    @Column(nullable = false)
    private BigDecimal netPayout = BigDecimal.ZERO;

    @Column(nullable = false)
    private String payoutStatus = "PENDING";

    private Instant paidDate;
}
