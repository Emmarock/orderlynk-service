package com.myorderlynk.app.batch;

import com.myorderlynk.app.common.enums.BatchStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface BatchRepository extends JpaRepository<Batch, UUID> {

    List<Batch> findByVendorIdOrderByCreatedAtDesc(UUID vendorId);

    List<Batch> findByVendorId(UUID vendorId);

    /** Marketplace listing: publicly visible batches still open for orders/requests. */
    List<Batch> findByVisibilityAndBatchStatusInOrderByCloseDateAsc(
            BatchVisibility visibility, Collection<BatchStatus> statuses);
}
