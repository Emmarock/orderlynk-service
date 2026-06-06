package com.myorderlynk.app.service.notification;

/**
 * Published when the system wants to send a WhatsApp message. Consumed asynchronously
 * by {@link WhatsAppEventListener} after the publishing transaction commits.
 *
 * @param to   recipient phone number (any format; normalised to digits by the provider)
 * @param body plain-text message
 */
public record WhatsAppRequestedEvent(String to, String body) {
}