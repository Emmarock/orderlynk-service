package com.myorderlynk.app.service.notification;

import java.util.Map;

/**
 * Published when the system wants to send an email. Consumed asynchronously by
 * {@link EmailEventListener}, which renders {@code template} with {@code model}
 * and dispatches it via the {@link EmailProvider} off the request thread.
 *
 * @param to       recipient email address
 * @param subject  email subject line
 * @param template template file name (without extension) under {@code resources/templates}
 * @param model    placeholder values; keys whose name ends in {@code Html} are inserted as raw HTML
 */
public record EmailRequestedEvent(String to, String subject, String template, Map<String, String> model) {
}