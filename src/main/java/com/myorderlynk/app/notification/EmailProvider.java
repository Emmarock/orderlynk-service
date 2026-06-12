package com.myorderlynk.app.notification;

public interface EmailProvider {

    void sendEmail(
            String recipient,
            String subject,
            String html
    );
}