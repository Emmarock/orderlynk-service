package com.myorderlynk.app.order;

import com.myorderlynk.app.order.PaymentRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PaymentRecordRepository extends JpaRepository<PaymentRecord, UUID> {
    List<PaymentRecord> findByOrderId(UUID orderId);
}
