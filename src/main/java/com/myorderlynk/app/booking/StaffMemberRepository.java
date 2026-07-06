package com.myorderlynk.app.booking;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface StaffMemberRepository extends JpaRepository<StaffMember, UUID> {

    /** All of a vendor's team, ordered for display. */
    List<StaffMember> findByVendorIdOrderByDisplayOrderAscCreatedAtAsc(UUID vendorId);

    /** A vendor's bookable team (active + accepting bookings), ordered for display. */
    List<StaffMember> findByVendorIdAndActiveTrueAndAcceptsBookingsTrueOrderByDisplayOrderAscCreatedAtAsc(UUID vendorId);

    boolean existsByVendorId(UUID vendorId);
}
