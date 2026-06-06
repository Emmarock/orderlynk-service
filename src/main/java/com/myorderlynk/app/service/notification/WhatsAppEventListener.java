package com.myorderlynk.app.service.notification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Sends {@link WhatsAppRequestedEvent}s in the background, after the publishing
 * transaction commits. Failures are logged, never propagated.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WhatsAppEventListener {

    private final WhatsAppProvider provider;

    @Async("whatsappExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onWhatsAppRequested(WhatsAppRequestedEvent event) {
        try {
            provider.send(event.to(), event.body());
            log.info("WhatsApp dispatched to {}", event.to());
        } catch (Exception e) {
            log.error("Failed to send WhatsApp to {}", event.to(), e);
        }
    }
}