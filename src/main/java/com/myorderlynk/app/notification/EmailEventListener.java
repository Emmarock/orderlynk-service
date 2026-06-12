package com.myorderlynk.app.notification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Consumes {@link EmailRequestedEvent}s and sends them in the background.
 *
 * <p>Runs after the publishing transaction commits ({@code AFTER_COMMIT}) so we never email
 * about a change that gets rolled back; {@code fallbackExecution=true} also handles events
 * published outside a transaction. {@code @Async} moves the network call off the request thread.
 * Failures are logged, never propagated (nothing is waiting on this thread).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmailEventListener {

    private final EmailTemplateRenderer renderer;
    private final EmailProvider emailProvider;

    @Async("emailExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onEmailRequested(EmailRequestedEvent event) {
        try {
            String html = renderer.render(event.template(), event.model());
            emailProvider.sendEmail(event.to(), event.subject(), html);
            log.info("Email dispatched: template={} to={}", event.template(), event.to());
        } catch (Exception e) {
            log.error("Failed to send email template={} to={}", event.template(), event.to(), e);
        }
    }
}