package com.myorderlynk.app.finance;

import com.myorderlynk.app.common.BaseEntity;
import com.myorderlynk.app.common.enums.VatCollector;
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
 * One ledger record per order that charged VAT: the taxable base, the VAT amount, and who is
 * responsible for remitting it to the government (the vendor or the platform). This is the
 * authoritative VAT transaction record; remittance is tracked via {@link #remitted}.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "vat_ledger_entries", indexes = {
        @Index(name = "idx_vat_ledger_vendor", columnList = "vendorId"),
        @Index(name = "idx_vat_ledger_collector", columnList = "collector"),
        @Index(name = "idx_vat_ledger_order", columnList = "orderId")
})
public class VatLedgerEntry extends BaseEntity {

    @Column(nullable = false)
    private UUID orderId;

    /** Human-friendly order id for readability in exports/statements. */
    private String publicOrderId;

    @Column(nullable = false)
    private UUID vendorId;

    /** Who must remit this VAT: the vendor or the platform (snapshot of the vendor's choice). */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private VatCollector collector;

    /** Taxable base the VAT was charged on (product subtotal of taxable items). */
    @Column(nullable = false)
    private BigDecimal taxableAmount = BigDecimal.ZERO;

    /** VAT amount collected. */
    @Column(nullable = false)
    private BigDecimal amount = BigDecimal.ZERO;

    @Column(nullable = false, length = 8)
    private String currency = "CAD";

    /** Whether this VAT has been remitted to the government. */
    @Column(nullable = false)
    private boolean remitted = false;

    private Instant remittedAt;
}
