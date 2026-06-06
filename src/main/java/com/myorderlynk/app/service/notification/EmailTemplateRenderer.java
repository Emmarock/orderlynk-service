package com.myorderlynk.app.service.notification;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Minimal HTML email renderer: loads a template from {@code resources/templates/{name}.html}
 * and substitutes {@code {{placeholder}}} tokens from the model. Values are HTML-escaped to
 * prevent injection, except keys whose name ends in {@code Html} (e.g. pre-built table rows),
 * which are inserted as raw markup. Templates are cached after first load.
 */
@Component
public class EmailTemplateRenderer {

    private static final Pattern TOKEN = Pattern.compile("\\{\\{(\\w+)}}");
    private final Map<String, String> cache = new ConcurrentHashMap<>();

    public String render(String templateName, Map<String, String> model) {
        String template = cache.computeIfAbsent(templateName, EmailTemplateRenderer::load);
        Matcher matcher = TOKEN.matcher(template);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String key = matcher.group(1);
            String raw = model.getOrDefault(key, "");
            if (raw == null) raw = "";
            String value = key.endsWith("Html") ? raw : escape(raw);
            matcher.appendReplacement(out, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private static String load(String name) {
        ClassPathResource resource = new ClassPathResource("templates/" + name + ".html");
        try (InputStream in = resource.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Email template not found: templates/" + name + ".html", e);
        }
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}