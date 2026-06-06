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

    /**
     * Records a non-order broadcast message to a single recipient (PRD §16 seam).
     * Used by vendor → customer broadcasts; orderId is null since it isn't order-tied.
     */
    public void notifyBroadcast(UUID userId, String channel, String recipient, String template, String body) {
        NotificationLog entry = new NotificationLog();
        entry.setUserId(userId);
        entry.setChannel(channel);
        entry.setTemplate(template);
        entry.setRecipient(recipient);
        entry.setBody(body);
        entry.setStatus("SENT");
        entry.setSentDate(Instant.now());
        repo.save(entry);
        log.info("[notify:{}] template={} -> {}", channel, template, recipient);
    }

    /**
     * Records a WhatsApp send attempt with its initial status and the provider's message id,
     * so later delivery-status callbacks can update this row.
     */
    public void recordWhatsApp(UUID orderId, String recipient, String template, String body,
                               String status, String providerMessageId) {
        NotificationLog entry = new NotificationLog();
        entry.setOrderId(orderId);
        entry.setChannel("WHATSAPP");
        entry.setTemplate(template == null ? "WHATSAPP" : template);
        entry.setRecipient(recipient);
        entry.setBody(body);
        entry.setStatus(status);
        entry.setProviderMessageId(providerMessageId);
        entry.setSentDate(Instant.now());
        repo.save(entry);
    }

    /**
     * Applies a delivery-status update from a provider callback (e.g. Twilio status webhook),
     * correlating by the provider message id. Returns true if a matching record was updated.
     */
    public boolean updateDeliveryStatus(String providerMessageId, String status) {
        if (providerMessageId == null || providerMessageId.isBlank()) {
            return false;
        }
        return repo.findFirstByProviderMessageIdOrderByCreatedAtDesc(providerMessageId)
                .map(entry -> {
                    entry.setStatus(status);
                    repo.save(entry);
                    log.info("Delivery status for {} -> {}", providerMessageId, status);
                    return true;
                })
                .orElseGet(() -> {
                    log.warn("Delivery callback for unknown message id {} (status {})", providerMessageId, status);
                    return false;
                });
    }

    public List<NotificationLog> forOrder(UUID orderId) {
        return repo.findByOrderIdOrderByCreatedAtDesc(orderId);
    }
}
