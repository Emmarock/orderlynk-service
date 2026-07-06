package com.myorderlynk.app.booking;

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

/** A vacation / unavailable period that removes slots from availability (PRD §9, §13 BlockedSlot). */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "blocked_slots",
        indexes = @Index(name = "idx_blocked_vendor", columnList = "vendorId"))
public class BlockedSlot extends BaseEntity {

    @Column(nullable = false)
    private UUID vendorId;

    /** The team member this block belongs to, or {@code null} for a shop-wide block (applies to all). */
    private UUID staffId;

    @Column(nullable = false)
    private Instant startDatetime;

    @Column(nullable = false)
    private Instant endDatetime;

    private String reason;
}
