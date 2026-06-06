package com.myorderlynk.app.repository;

import com.myorderlynk.app.domain.SupportTicket;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SupportTicketRepository extends JpaRepository<SupportTicket, UUID> {
    List<SupportTicket> findByVendorIdOrderByCreatedAtDesc(UUID vendorId);
}