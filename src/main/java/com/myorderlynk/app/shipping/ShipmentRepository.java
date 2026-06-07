package com.myorderlynk.app.shipping;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ShipmentRepository extends JpaRepository<Shipment, UUID> {

    /** Most recent shipment for an order (the live one). */
    Optional<Shipment> findFirstByOrderIdOrderByCreatedAtDesc(UUID orderId);

    List<Shipment> findByOrderIdOrderByCreatedAtDesc(UUID orderId);

    Optional<Shipment> findByTrackingNumber(String trackingNumber);

    Optional<Shipment> findByProviderShipmentId(String providerShipmentId);

    Optional<Shipment> findByTransactionId(String transactionId);
}