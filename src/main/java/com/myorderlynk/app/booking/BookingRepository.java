package com.myorderlynk.app.booking;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BookingRepository extends JpaRepository<Booking, UUID> {

    /** Booking statuses that occupy a time slot for capacity checks. */
    List<BookingStatus> OCCUPYING_STATUSES = List.of(
            BookingStatus.REQUESTED, BookingStatus.APPROVED, BookingStatus.DEPOSIT_PENDING,
            BookingStatus.CONFIRMED, BookingStatus.REMINDER_SENT, BookingStatus.IN_PROGRESS,
            BookingStatus.COMPLETED, BookingStatus.BALANCE_PENDING);

    Optional<Booking> findByPublicBookingId(String publicBookingId);

    boolean existsByPublicBookingId(String publicBookingId);

    List<Booking> findByVendorIdOrderByAppointmentStartDesc(UUID vendorId);

    Page<Booking> findByVendorIdOrderByAppointmentStartDesc(UUID vendorId, Pageable pageable);

    List<Booking> findByVendorIdAndAppointmentStartBetweenOrderByAppointmentStartAsc(
            UUID vendorId, Instant from, Instant to);

    /** Bookings a vendor took in a window, newest-first — for the customer list and earnings rollup. */
    List<Booking> findByVendorIdAndCreatedAtBetweenOrderByCreatedAtDesc(
            UUID vendorId, Instant from, Instant to);

    Page<Booking> findByVendorIdAndAppointmentStartBetweenOrderByAppointmentStartAsc(
            UUID vendorId, Instant from, Instant to, Pageable pageable);

    List<Booking> findByCustomerUserIdOrderByAppointmentStartDesc(UUID customerUserId);

    Page<Booking> findByCustomerUserIdOrderByAppointmentStartDesc(UUID customerUserId, Pageable pageable);

    List<Booking> findAllByOrderByAppointmentStartDesc();

    Page<Booking> findAllByOrderByAppointmentStartDesc(Pageable pageable);

    /** Active bookings overlapping a window for a vendor — used for capacity checks. */
    @Query("select b from Booking b where b.vendorId = :vendorId "
            + "and b.status in :statuses "
            + "and b.appointmentStart < :to and b.appointmentEnd > :from")
    List<Booking> findOverlapping(@Param("vendorId") UUID vendorId,
                                  @Param("from") Instant from, @Param("to") Instant to,
                                  @Param("statuses") Collection<BookingStatus> statuses);

    /** Active bookings for one worker overlapping a window — used for per-worker capacity checks. */
    @Query("select b from Booking b where b.vendorId = :vendorId and b.staffId = :staffId "
            + "and b.status in :statuses "
            + "and b.appointmentStart < :to and b.appointmentEnd > :from")
    List<Booking> findOverlappingByStaff(@Param("vendorId") UUID vendorId, @Param("staffId") UUID staffId,
                                         @Param("from") Instant from, @Param("to") Instant to,
                                         @Param("statuses") Collection<BookingStatus> statuses);

    /** Bookings starting within a window in the given statuses — used by the reminder scheduler. */
    @Query("select b from Booking b where b.status in :statuses "
            + "and b.appointmentStart >= :from and b.appointmentStart < :to")
    List<Booking> findUpcoming(@Param("from") Instant from, @Param("to") Instant to,
                               @Param("statuses") Collection<BookingStatus> statuses);

    /** Unpaid deposit-pending bookings whose hold has expired — used to release slots. */
    @Query("select b from Booking b where b.status = :status and b.holdExpiresAt is not null and b.holdExpiresAt < :now")
    List<Booking> findExpiredHolds(@Param("status") BookingStatus status, @Param("now") Instant now);
}
