package com.myorderlynk.app.notification;

import com.myorderlynk.app.notification.NotificationService;
import com.myorderlynk.app.notification.TwilioSignatureValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Receives Twilio delivery-status callbacks and records the outcome against the matching
 * notification log row. Public (Twilio is unauthenticated) but signature-verified.
 */
@Slf4j
@RestController
@RequestMapping("/api/webhooks/twilio")
public class TwilioWebhookController {

    private final NotificationService notifications;
    private final TwilioSignatureValidator signatureValidator;

    public TwilioWebhookController(NotificationService notifications, TwilioSignatureValidator signatureValidator) {
        this.notifications = notifications;
        this.signatureValidator = signatureValidator;
    }

    @PostMapping(value = "/status", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<Void> status(@RequestParam Map<String, String> params,
                                       @RequestHeader(value = "X-Twilio-Signature", required = false) String signature) {
        if (!signatureValidator.isValid(signature, params)) {
            return ResponseEntity.status(403).build();
        }
        String sid = params.get("MessageSid");
        String messageStatus = params.get("MessageStatus");
        String errorCode = params.get("ErrorCode");
        String status = messageStatus == null ? "UNKNOWN" : messageStatus.toUpperCase();
        if (errorCode != null && !errorCode.isBlank()) {
            status = status + ":" + errorCode;
        }
        notifications.updateDeliveryStatus(sid, status);
        return ResponseEntity.ok().build();
    }
}