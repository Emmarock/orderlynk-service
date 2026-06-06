package com.myorderlynk.app.service.notification;

public interface EmailProvider {

    void sendEmail(
            String recipient,
            String subject,
            String html
    );
}