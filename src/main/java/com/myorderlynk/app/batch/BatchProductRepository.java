package com.myorderlynk.app.batch;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface BatchProductRepository extends JpaRepository<BatchProduct, UUID> {

    List<BatchProduct> findByBatchIdOrderByCreatedAtAsc(UUID batchId);

    List<BatchProduct> findByBatchIdAndStatus(UUID batchId, BatchProductStatus status);
}
