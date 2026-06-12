package com.myorderlynk.app.identity;

import com.myorderlynk.app.identity.CustomerAddress;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CustomerAddressRepository extends JpaRepository<CustomerAddress, UUID> {
    List<CustomerAddress> findByUserIdOrderByIsDefaultDescCreatedAtDesc(UUID userId);

    Optional<CustomerAddress> findByIdAndUserId(UUID id, UUID userId);

    List<CustomerAddress> findByUserId(UUID userId);
}