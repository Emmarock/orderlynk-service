package com.myorderlynk.app.repository;

import com.myorderlynk.app.domain.NotificationLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface NotificationLogRepository extends JpaRepository<NotificationLog, UUID> {
    List<NotificationLog> findByOrderIdOrderByCreatedAtDesc(UUID orderId);
}
