package com.myorderlynk.app.booking;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface BookingNotificationRepository extends JpaRepository<BookingNotification, UUID> {
    List<BookingNotification> findByBookingIdOrderByCreatedAtDesc(UUID bookingId);

    boolean existsByBookingIdAndTemplate(UUID bookingId, String template);
}
