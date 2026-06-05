package com.myorderlynk.app.repository;

import com.myorderlynk.app.domain.StatusChangeLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface StatusChangeLogRepository extends JpaRepository<StatusChangeLog, UUID> {
    List<StatusChangeLog> findByOrderIdOrderByCreatedAtAsc(UUID orderId);
}
