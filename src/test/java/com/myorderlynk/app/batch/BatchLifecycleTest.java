package com.myorderlynk.app.batch;

import com.myorderlynk.app.common.enums.BatchStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the close-date-driven batch lifecycle transitions (batch-cargo spec §8.1). Close dates
 * are chosen relative to "today" in the configured zone so the expected target is deterministic
 * regardless of the time of day the test runs.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class BatchLifecycleTest {

    @Autowired BatchRepository batches;
    @Autowired BatchService batchService;

    private final LocalDate today = LocalDate.now(ZoneId.of("America/Toronto"));

    private UUID newOpenBatch(LocalDate closeDate) {
        Batch b = new Batch();
        b.setVendorId(UUID.randomUUID());
        b.setBatchName("Test batch");
        b.setBatchStatus(BatchStatus.OPEN);
        b.setVisibility(BatchVisibility.MARKETPLACE);
        b.setCloseDate(closeDate);
        return batches.save(b).getId();
    }

    private BatchStatus statusOf(UUID id) {
        return batches.findById(id).orElseThrow().getBatchStatus();
    }

    @Test
    void advancesByCloseDate() {
        UUID future = newOpenBatch(today.plusDays(10));   // >5 days out → stays OPEN
        UUID soon = newOpenBatch(today.plusDays(3));      // within 5 days → CLOSING_SOON
        UUID justClosed = newOpenBatch(today.minusDays(1)); // past close, <2 days → CLOSED
        UUID sourcing = newOpenBatch(today.minusDays(3));   // 2+ days after close → SOURCING

        batchService.advanceLifecycle();

        assertThat(statusOf(future)).isEqualTo(BatchStatus.OPEN);
        assertThat(statusOf(soon)).isEqualTo(BatchStatus.CLOSING_SOON);
        assertThat(statusOf(justClosed)).isEqualTo(BatchStatus.CLOSED);
        assertThat(statusOf(sourcing)).isEqualTo(BatchStatus.SOURCING);
    }

    @Test
    void doesNotRewindManuallyAdvancedBatches() {
        // A batch the vendor already shipped must not be pulled back even though its close date passed.
        Batch b = new Batch();
        b.setVendorId(UUID.randomUUID());
        b.setBatchName("Shipped batch");
        b.setBatchStatus(BatchStatus.SHIPPED);
        b.setVisibility(BatchVisibility.MARKETPLACE);
        b.setCloseDate(today.minusDays(1));
        UUID id = batches.save(b).getId();

        batchService.advanceLifecycle();

        assertThat(statusOf(id)).isEqualTo(BatchStatus.SHIPPED);
    }
}
