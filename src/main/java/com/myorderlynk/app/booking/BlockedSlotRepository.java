package com.myorderlynk.app.booking;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface BlockedSlotRepository extends JpaRepository<BlockedSlot, UUID> {
    List<BlockedSlot> findByVendorId(UUID vendorId);

    /** Blocks overlapping a [from, to) window. */
    @Query("select b from BlockedSlot b where b.vendorId = :vendorId "
            + "and b.startDatetime < :to and b.endDatetime > :from")
    List<BlockedSlot> findOverlapping(@Param("vendorId") UUID vendorId,
                                      @Param("from") Instant from, @Param("to") Instant to);
}
