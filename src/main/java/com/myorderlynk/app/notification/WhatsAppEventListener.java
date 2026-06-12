package com.myorderlynk.app.notification;

import com.myorderlynk.app.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Sends {@link WhatsAppRequestedEvent}s in the background (after the publishing transaction
 * commits) and records the attempt in notification_logs. The provider message id is stored so
 * the Twilio status webhook can later update the row to delivered/failed.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WhatsAppEventListener {

    private final WhatsAppProvider provider;
    private final NotificationService notifications;

    @Async("whatsappExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onWhatsAppRequested(WhatsAppRequestedEvent event) {
        try {
            String messageId = provider.send(event);
            String status = messageId == null ? "SKIPPED" : "QUEUED";
            notifications.recordWhatsApp(event.orderId(), event.to(), event.template(), event.body(), status, messageId);
            log.info("WhatsApp {} to {} (id={})", status, event.to(), messageId);
        } catch (IllegalStateException e) {
            // Provider-surfaced delivery error (bad sender, unverified template) — message says it all.
            notifications.recordWhatsApp(event.orderId(), event.to(), event.template(), event.body(), "FAILED", null);
            log.error("WhatsApp not delivered to {}: {}", event.to(), e.getMessage());
        } catch (Exception e) {
            notifications.recordWhatsApp(event.orderId(), event.to(), event.template(), event.body(), "FAILED", null);
            log.error("Failed to send WhatsApp to {}", event.to(), e);
        }
    }
}