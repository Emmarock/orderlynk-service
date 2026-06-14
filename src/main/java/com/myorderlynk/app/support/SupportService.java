package com.myorderlynk.app.support;

import com.myorderlynk.app.support.SupportTicket;
import com.myorderlynk.app.support.SupportDtos.SupportRequest;
import com.myorderlynk.app.support.SupportDtos.SupportTicketResponse;
import com.myorderlynk.app.support.SupportTicketRepository;
import com.myorderlynk.app.common.PageResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** Vendor support requests ("Message Us"). Tickets are persisted for the OrderLynk team to action. */
@Service
@Slf4j
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
        SupportTicket saved = tickets.save(ticket);
        log.info("Support ticket {} opened by user {} (vendor {}, category {})",
                saved.getId(), userId, vendorId, req.category());
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public PageResponse<SupportTicketResponse> forVendor(UUID vendorId, Pageable pageable) {
        return PageResponse.of(tickets.findByVendorIdOrderByCreatedAtDesc(vendorId, pageable).map(this::toResponse));
    }

    private SupportTicketResponse toResponse(SupportTicket t) {
        return new SupportTicketResponse(t.getId(), t.getCategory(), t.getSubject(), t.getMessage(),
                t.getStatus(), t.getCreatedAt());
    }
}