package com.myorderlynk.app.batch;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ShipmentRequestRepository extends JpaRepository<ShipmentRequest, UUID> {

    Optional<ShipmentRequest> findByPublicRequestId(String publicRequestId);

    boolean existsByPublicRequestId(String publicRequestId);

    List<ShipmentRequest> findByBatchIdOrderByCreatedAtDesc(UUID batchId);

    Page<ShipmentRequest> findByBatchIdOrderByCreatedAtDesc(UUID batchId, Pageable pageable);

    Page<ShipmentRequest> findByBatchIdAndVendorIdOrderByCreatedAtDesc(UUID batchId, UUID vendorId, Pageable pageable);

    List<ShipmentRequest> findByVendorIdOrderByCreatedAtDesc(UUID vendorId);

    Page<ShipmentRequest> findByVendorIdOrderByCreatedAtDesc(UUID vendorId, Pageable pageable);

    List<ShipmentRequest> findByCustomerUserIdOrderByCreatedAtDesc(UUID customerUserId);

    Page<ShipmentRequest> findByCustomerUserIdOrderByCreatedAtDesc(UUID customerUserId, Pageable pageable);
}
