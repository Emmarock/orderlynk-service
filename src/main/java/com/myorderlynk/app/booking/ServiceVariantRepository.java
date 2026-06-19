package com.myorderlynk.app.booking;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ServiceVariantRepository extends JpaRepository<ServiceVariant, UUID> {
    List<ServiceVariant> findByServiceIdOrderByCreatedAtAsc(UUID serviceId);

    List<ServiceVariant> findByServiceIdAndActiveTrue(UUID serviceId);

    void deleteByServiceId(UUID serviceId);
}