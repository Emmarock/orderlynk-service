package com.myorderlynk.app.batch;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BatchOrderRepository extends JpaRepository<BatchOrder, UUID> {

    Optional<BatchOrder> findByPublicOrderId(String publicOrderId);

    boolean existsByPublicOrderId(String publicOrderId);

    List<BatchOrder> findByBatchIdOrderByCreatedAtDesc(UUID batchId);

    Page<BatchOrder> findByBatchIdOrderByCreatedAtDesc(UUID batchId, Pageable pageable);

    Page<BatchOrder> findByBatchIdAndVendorIdOrderByCreatedAtDesc(UUID batchId, UUID vendorId, Pageable pageable);

    List<BatchOrder> findByVendorIdOrderByCreatedAtDesc(UUID vendorId);

    Page<BatchOrder> findByVendorIdOrderByCreatedAtDesc(UUID vendorId, Pageable pageable);

    List<BatchOrder> findByCustomerUserIdOrderByCreatedAtDesc(UUID customerUserId);

    Page<BatchOrder> findByCustomerUserIdOrderByCreatedAtDesc(UUID customerUserId, Pageable pageable);
}
