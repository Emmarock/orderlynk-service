package com.myorderlynk.app.batch;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BatchOrderRepository extends JpaRepository<BatchOrder, UUID> {

    Optional<BatchOrder> findByPublicOrderId(String publicOrderId);

    boolean existsByPublicOrderId(String publicOrderId);

    List<BatchOrder> findByBatchIdOrderByCreatedAtDesc(UUID batchId);

    List<BatchOrder> findByVendorIdOrderByCreatedAtDesc(UUID vendorId);

    List<BatchOrder> findByCustomerUserIdOrderByCreatedAtDesc(UUID customerUserId);
}
