package com.myorderlynk.app.batch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

/**
 * Records Batch &amp; Cargo notifications (spec §14). MVP delivery is persisted to
 * {@link BatchNotification} and logged; real email/WhatsApp dispatch plugs into the same seam later.
 */
@Service
public class BatchNotificationService {

    private static final Logger log = LoggerFactory.getLogger(BatchNotificationService.class);

    public static final String ROLE_PROVIDER = "PROVIDER";
    public static final String ROLE_CUSTOMER = "CUSTOMER";

    private final BatchNotificationRepository repo;

    public BatchNotificationService(BatchNotificationRepository repo) {
        this.repo = repo;
    }

    /** Notify a customer via email when on file, otherwise WhatsApp. */
    public void notifyCustomer(String subjectType, UUID subjectId, String email, String phone,
                               String template, String body) {
        boolean hasEmail = email != null && !email.isBlank();
        record(subjectType, subjectId, ROLE_CUSTOMER, hasEmail ? "EMAIL" : "WHATSAPP",
                hasEmail ? email : phone, template, body);
    }

    /** Notify the vendor/cargo partner (dashboard event). */
    public void notifyProvider(String subjectType, UUID subjectId, String template, String body) {
        record(subjectType, subjectId, ROLE_PROVIDER, "DASHBOARD", null, template, body);
    }

    public void record(String subjectType, UUID subjectId, String role, String channel,
                        String recipient, String template, String body) {
        BatchNotification n = new BatchNotification();
        n.setSubjectType(subjectType);
        n.setSubjectId(subjectId);
        n.setRecipientRole(role);
        n.setChannel(channel);
        n.setRecipient(recipient);
        n.setTemplate(template);
        n.setBody(body);
        n.setSentAt(Instant.now());
        n.setStatus("SENT");
        repo.save(n);
        log.info("[batch-notify:{}] {}={} template={} -> {}", channel, subjectType, subjectId, template, recipient);
    }
}
