package com.myorderlynk.app.repository;

import com.myorderlynk.app.domain.Batch;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface BatchRepository extends JpaRepository<Batch, UUID> {
    List<Batch> findByVendorId(UUID vendorId);
}
