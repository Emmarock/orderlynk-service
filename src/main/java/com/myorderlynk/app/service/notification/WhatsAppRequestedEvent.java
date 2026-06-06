package com.myorderlynk.app.service.notification;

import java.util.List;
import java.util.UUID;

/**
 * Published when the system wants to send a WhatsApp message. Consumed asynchronously
 * by {@link WhatsAppEventListener} after the publishing transaction commits.
 *
 * @param to        recipient phone number (any format; normalised by the provider)
 * @param template  template key — names the Content template (Twilio) and labels the notification log
 * @param variables ordered template variables; used to build Content variables when a template is configured
 * @param body      plain-text fallback (sent when no Content template is configured for {@code template})
 * @param orderId   related order id, for correlating the delivery record (nullable)
 */
public record WhatsAppRequestedEvent(String to, String template, List<String> variables, String body, UUID orderId) {
}