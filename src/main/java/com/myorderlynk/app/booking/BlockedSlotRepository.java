package com.myorderlynk.app.booking;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface BlockedSlotRepository extends JpaRepository<BlockedSlot, UUID> {
    List<BlockedSlot> findByVendorId(UUID vendorId);

    /** Shop-wide blocks only (no staff member) — apply to the whole shop and every worker. */
    List<BlockedSlot> findByVendorIdAndStaffIdIsNull(UUID vendorId);

    /** Blocks belonging to one worker. */
    List<BlockedSlot> findByVendorIdAndStaffId(UUID vendorId, UUID staffId);

    void deleteByStaffId(UUID staffId);

    /** Blocks overlapping a [from, to) window. */
    @Query("select b from BlockedSlot b where b.vendorId = :vendorId "
            + "and b.startDatetime < :to and b.endDatetime > :from")
    List<BlockedSlot> findOverlapping(@Param("vendorId") UUID vendorId,
                                      @Param("from") Instant from, @Param("to") Instant to);
}
