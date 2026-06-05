package com.myorderlynk.app.service;

import com.myorderlynk.app.domain.NotificationLog;
import com.myorderlynk.app.domain.Order;
import com.myorderlynk.app.repository.NotificationLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * MVP notifications are recorded to {@link NotificationLog} (PRD §16). Real
 * email/WhatsApp delivery is a future integration — this is the seam for it.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationLogRepository repo;

    public NotificationService(NotificationLogRepository repo) {
        this.repo = repo;
    }

    public void notifyOrder(Order order, String channel, String template, String recipient, String body) {
        NotificationLog entry = new NotificationLog();
        entry.setOrderId(order.getId());
        entry.setUserId(order.getCustomerUserId());
        entry.setChannel(channel);
        entry.setTemplate(template);
        entry.setRecipient(recipient);
        entry.setBody(body);
        entry.setStatus("SENT");
        entry.setSentDate(Instant.now());
        repo.save(entry);
        log.info("[notify:{}] order={} template={} -> {}", channel, order.getPublicOrderId(), template, recipient);
    }

    public List<NotificationLog> forOrder(UUID orderId) {
        return repo.findByOrderIdOrderByCreatedAtDesc(orderId);
    }
}
