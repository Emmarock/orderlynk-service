package com.myorderlynk.app.payment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ProcessedPaymentEventRepository extends JpaRepository<ProcessedPaymentEvent, UUID> {
    boolean existsByEventId(String eventId);
}