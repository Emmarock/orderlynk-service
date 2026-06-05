package com.myorderlynk.app.repo;

import com.myorderlynk.app.domain.Payout;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PayoutRepository extends JpaRepository<Payout, UUID> {
    List<Payout> findByVendorIdOrderByPeriodEndDesc(UUID vendorId);
}
