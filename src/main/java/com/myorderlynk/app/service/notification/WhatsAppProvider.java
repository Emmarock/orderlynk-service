package com.myorderlynk.app.service.notification;

/** Sends a WhatsApp message. Returns the provider message id (for delivery tracking), or null if skipped. */
public interface WhatsAppProvider {

    String send(WhatsAppRequestedEvent message);
}