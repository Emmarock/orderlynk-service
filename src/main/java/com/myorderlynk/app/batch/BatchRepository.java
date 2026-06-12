package com.myorderlynk.app.batch;

import com.myorderlynk.app.batch.Batch;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface BatchRepository extends JpaRepository<Batch, UUID> {
    List<Batch> findByVendorId(UUID vendorId);
}
