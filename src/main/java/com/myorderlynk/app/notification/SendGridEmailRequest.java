package com.myorderlynk.app.notification;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Body for Twilio SendGrid's v3 Mail Send API ({@code POST /v3/mail/send}).
 * Shape: one personalization with the recipient(s), a single sender, and one HTML content part.
 */
public record SendGridEmailRequest(
        List<Personalization> personalizations,
        Address from,
        String subject,
        List<Content> content
) {

    public static SendGridEmailRequest html(Address from, String recipient, String subject, String html) {
        return new SendGridEmailRequest(
                List.of(new Personalization(List.of(new Address(recipient, null)))),
                from,
                subject,
                List.of(new Content("text/html", html)));
    }

    public record Personalization(List<Address> to) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Address(String email, String name) {
    }

    public record Content(String type, String value) {
    }
}