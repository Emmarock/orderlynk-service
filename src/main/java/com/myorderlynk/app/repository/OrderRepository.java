package com.myorderlynk.app.repository;

import com.myorderlynk.app.domain.Order;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {
    Optional<Order> findByPublicOrderId(String publicOrderId);

    boolean existsByPublicOrderId(String publicOrderId);

    List<Order> findByVendorIdOrderByCreatedAtDesc(UUID vendorId);

    List<Order> findByCustomerUserIdOrderByCreatedAtDesc(UUID customerUserId);

    List<Order> findByVendorIdAndCreatedAtBetween(UUID vendorId, Instant start, Instant end);

    List<Order> findAllByOrderByCreatedAtDesc();
}
