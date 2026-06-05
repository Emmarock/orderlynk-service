package com.myorderlynk.app.repository;

import com.myorderlynk.app.domain.PaymentRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PaymentRecordRepository extends JpaRepository<PaymentRecord, UUID> {
    List<PaymentRecord> findByOrderId(UUID orderId);
}
