package com.myorderlynk.app.service;

import com.myorderlynk.app.domain.StatusChangeLog;
import com.myorderlynk.app.repository.StatusChangeLogRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/** Records every payment/fulfillment status change for traceability (PRD §17). */
@Service
public class AuditService {

    private final StatusChangeLogRepository repo;

    public AuditService(StatusChangeLogRepository repo) {
        this.repo = repo;
    }

    public void logChange(UUID orderId, String statusType, String from, String to, String changedBy, String note) {
        StatusChangeLog entry = new StatusChangeLog();
        entry.setOrderId(orderId);
        entry.setStatusType(statusType);
        entry.setFromStatus(from);
        entry.setToStatus(to);
        entry.setChangedBy(changedBy);
        entry.setNote(note);
        repo.save(entry);
    }

    public List<StatusChangeLog> forOrder(UUID orderId) {
        return repo.findByOrderIdOrderByCreatedAtAsc(orderId);
    }
}
