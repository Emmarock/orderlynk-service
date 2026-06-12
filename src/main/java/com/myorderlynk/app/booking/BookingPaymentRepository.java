package com.myorderlynk.app.booking;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface BookingPaymentRepository extends JpaRepository<BookingPayment, UUID> {
    List<BookingPayment> findByBookingIdOrderByCreatedAtAsc(UUID bookingId);
}
