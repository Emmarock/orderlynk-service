package com.myorderlynk.app.booking;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ServiceAddOnRepository extends JpaRepository<ServiceAddOn, UUID> {
    List<ServiceAddOn> findByServiceIdOrderByCreatedAtAsc(UUID serviceId);

    List<ServiceAddOn> findByServiceIdAndActiveTrue(UUID serviceId);

    void deleteByServiceId(UUID serviceId);
}
