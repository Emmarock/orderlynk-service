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

/**
 * A booking-related notification record (PRD §11, §13 BookingNotification). MVP delivery is
 * recorded here (and logged); real email/WhatsApp dispatch reuses the existing notification
 * seam. {@code scheduledAt} supports the reminder engine; {@code sentAt}/{@code status} track delivery.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "booking_notifications",
        indexes = @Index(name = "idx_booknotif_booking", columnList = "bookingId"))
public class BookingNotification extends BaseEntity {

    @Column(nullable = false)
    private UUID bookingId;

    /** PROVIDER or CUSTOMER. */
    @Column(nullable = false)
    private String recipientRole;

    /** Email / WhatsApp / Dashboard. */
    @Column(nullable = false)
    private String channel;

    private String recipient;

    /** Event key, e.g. BOOKING_REQUESTED, REMINDER_24H (PRD §11). */
    @Column(nullable = false)
    private String template;

    @Column(length = 2000)
    private String body;

    private Instant scheduledAt;

    private Instant sentAt;

    @Column(nullable = false)
    private String status = "SENT";
}
