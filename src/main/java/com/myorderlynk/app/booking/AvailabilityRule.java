package com.myorderlynk.app.booking;

import com.myorderlynk.app.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.UUID;

/**
 * A recurring weekly working-hours window for a provider (PRD §9, §13 AvailabilityRule).
 * One row per working day + time range; the availability engine generates bookable slots
 * from these, the service duration, buffer and lead time.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "availability_rules",
        indexes = @Index(name = "idx_availrule_vendor", columnList = "vendorId"))
public class AvailabilityRule extends BaseEntity {

    @Column(nullable = false)
    private UUID vendorId;

    /** MONDAY … SUNDAY. */
    @Column(nullable = false, length = 12)
    private DayOfWeek dayOfWeek;

    @Column(nullable = false)
    private LocalTime startTime;

    @Column(nullable = false)
    private LocalTime endTime;

    /** Simultaneous bookings allowed in this window; null falls back to the profile default. */
    private Integer capacity;

    /** Gap after each booking; null falls back to the profile default. */
    private Integer bufferMinutes;

    /** Minimum lead time before a slot can start; null falls back to the profile default. */
    private Integer leadTimeHours;

    @Column(nullable = false)
    private boolean active = true;
}
