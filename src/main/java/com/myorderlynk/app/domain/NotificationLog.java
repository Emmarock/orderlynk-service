package com.myorderlynk.app.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "notification_logs")
public class NotificationLog extends BaseEntity {

    private UUID userId;

    private UUID orderId;

    /** Email / WhatsApp / SMS / Dashboard. */
    @Column(nullable = false)
    private String channel;

    @Column(nullable = false)
    private String template;

    private String recipient;

    @Column(length = 2000)
    private String body;

    @Column(nullable = false)
    private String status = "SENT";

    private Instant sentDate;
}
