package com.myorderlynk.app.service;

import com.myorderlynk.app.domain.SupportTicket;
import com.myorderlynk.app.dto.SupportDtos.SupportRequest;
import com.myorderlynk.app.dto.SupportDtos.SupportTicketResponse;
import com.myorderlynk.app.repository.SupportTicketRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/** Vendor support requests ("Message Us"). Tickets are persisted for the OrderLynk team to action. */
@Service
public class SupportService {

    private final SupportTicketRepository tickets;

    public SupportService(SupportTicketRepository tickets) {
        this.tickets = tickets;
    }

    @Transactional
    public SupportTicketResponse create(UUID vendorId, UUID userId, SupportRequest req) {
        SupportTicket ticket = new SupportTicket();
        ticket.setVendorId(vendorId);
        ticket.setUserId(userId);
        ticket.setCategory(req.category());
        ticket.setSubject(req.subject().trim());
        ticket.setMessage(req.message().trim());
        ticket.setStatus("OPEN");
        return toResponse(tickets.save(ticket));
    }

    @Transactional(readOnly = true)
    public List<SupportTicketResponse> forVendor(UUID vendorId) {
        return tickets.findByVendorIdOrderByCreatedAtDesc(vendorId).stream().map(this::toResponse).toList();
    }

    private SupportTicketResponse toResponse(SupportTicket t) {
        return new SupportTicketResponse(t.getId(), t.getCategory(), t.getSubject(), t.getMessage(),
                t.getStatus(), t.getCreatedAt());
    }
}