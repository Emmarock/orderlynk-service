package com.myorderlynk.app.batch;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Drives batch cycle transitions by close date (batch-cargo spec §8.1). Runs hourly so the
 * 18:00-local close fires within the hour; the work itself is idempotent and forward-only, so a
 * missed run (or a freshly-created back-dated batch) simply lands the batch on its correct status.
 */
@Component
@Slf4j
public class BatchLifecycleScheduler {

    private final BatchService batchService;

    public BatchLifecycleScheduler(BatchService batchService) {
        this.batchService = batchService;
    }

    @Scheduled(cron = "${app.batches.lifecycle-cron:0 0 * * * *}")
    public void tick() {
        try {
            batchService.advanceLifecycle();
        } catch (Exception e) {
            log.error("Batch lifecycle pass failed", e);
        }
    }
}
