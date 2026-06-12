package com.myorderlynk.app.batch;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ShipmentRequestRepository extends JpaRepository<ShipmentRequest, UUID> {

    Optional<ShipmentRequest> findByPublicRequestId(String publicRequestId);

    boolean existsByPublicRequestId(String publicRequestId);

    List<ShipmentRequest> findByBatchIdOrderByCreatedAtDesc(UUID batchId);

    List<ShipmentRequest> findByVendorIdOrderByCreatedAtDesc(UUID vendorId);

    List<ShipmentRequest> findByCustomerUserIdOrderByCreatedAtDesc(UUID customerUserId);
}
