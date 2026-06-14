package com.myorderlynk.app.support;

import com.myorderlynk.app.support.SupportTicket;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SupportTicketRepository extends JpaRepository<SupportTicket, UUID> {
    List<SupportTicket> findByVendorIdOrderByCreatedAtDesc(UUID vendorId);

    Page<SupportTicket> findByVendorIdOrderByCreatedAtDesc(UUID vendorId, Pageable pageable);
}