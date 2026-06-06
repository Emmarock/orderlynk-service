package com.myorderlynk.app.service.notification;

/** Sends a plain-text WhatsApp message to a recipient phone number. */
public interface WhatsAppProvider {

    void send(String toPhone, String body);
}