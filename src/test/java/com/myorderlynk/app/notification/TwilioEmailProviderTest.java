package com.myorderlynk.app.notification;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Drives {@link TwilioEmailProvider} against a JDK {@link HttpServer} standing in for Twilio
 * SendGrid, so we exercise the real WebClient path: request shape, auth header, and error mapping.
 */
class TwilioEmailProviderTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private HttpServer server;
    private String baseUrl;

    /** What the last request to the stub server looked like; captured by the active handler. */
    private final AtomicReference<RecordedRequest> lastRequest = new AtomicReference<>();

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void sendsWellFormedRequestToSendGrid() throws Exception {
        stub("/v3/mail/send", 202, "");
        TwilioEmailProvider provider = provider("SG.secret-key", "noreply@myorderlynk.com", "MyOrderLynk");

        provider.sendEmail("buyer@example.com", "Your order shipped", "<p>On its way</p>");

        RecordedRequest req = lastRequest.get();
        assertThat(req).isNotNull();
        assertThat(req.method).isEqualTo("POST");
        assertThat(req.path).isEqualTo("/v3/mail/send");
        assertThat(req.authorization).isEqualTo("Bearer SG.secret-key");
        assertThat(req.contentType).startsWith("application/json");

        JsonNode body = MAPPER.readTree(req.body);
        assertThat(body.at("/from/email").asText()).isEqualTo("noreply@myorderlynk.com");
        assertThat(body.at("/from/name").asText()).isEqualTo("MyOrderLynk");
        assertThat(body.at("/subject").asText()).isEqualTo("Your order shipped");
        assertThat(body.at("/personalizations/0/to/0/email").asText()).isEqualTo("buyer@example.com");
        assertThat(body.at("/content/0/type").asText()).isEqualTo("text/html");
        assertThat(body.at("/content/0/value").asText()).isEqualTo("<p>On its way</p>");
        // A recipient has no name — SendGrid rejects "name":null, so it must be omitted entirely.
        assertThat(body.at("/personalizations/0/to/0").has("name")).isFalse();
    }

    @Test
    void surfacesSendGridErrorBody() {
        stub("/v3/mail/send", 403, "{\"errors\":[{\"message\":\"from address does not match a verified Sender Identity\"}]}");
        TwilioEmailProvider provider = provider("SG.secret-key", "noreply@myorderlynk.com", "MyOrderLynk");

        assertThatThrownBy(() -> provider.sendEmail("buyer@example.com", "Hi", "<p>Hi</p>"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("buyer@example.com")
                .hasMessageContaining("403")
                .hasMessageContaining("verified Sender Identity");
    }

    @Test
    void skipsSendWhenApiKeyMissing() {
        stub("/v3/mail/send", 202, "");
        TwilioEmailProvider provider = provider("", "noreply@myorderlynk.com", "MyOrderLynk");

        provider.sendEmail("buyer@example.com", "Hi", "<p>Hi</p>");

        assertThat(lastRequest.get()).as("no HTTP call when unconfigured").isNull();
    }

    @Test
    void skipsSendWhenRecipientBlank() {
        stub("/v3/mail/send", 202, "");
        TwilioEmailProvider provider = provider("SG.secret-key", "noreply@myorderlynk.com", "MyOrderLynk");

        provider.sendEmail("  ", "Hi", "<p>Hi</p>");

        assertThat(lastRequest.get()).as("no HTTP call when recipient blank").isNull();
    }

    private TwilioEmailProvider provider(String apiKey, String fromEmail, String fromName) {
        TwilioEmailProperties props = new TwilioEmailProperties();
        props.setApiKey(apiKey);
        props.setFromEmail(fromEmail);
        props.setFromName(fromName);
        props.setApiBaseUrl(baseUrl);
        return new TwilioEmailProvider(props);
    }

    private void stub(String path, int status, String responseBody) {
        server.createContext(path, exchange -> {
            lastRequest.set(record(exchange));
            byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
            // 202/204 carry no body; -1 tells HttpServer to send no content-length body.
            exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
            if (bytes.length > 0) {
                exchange.getResponseBody().write(bytes);
            }
            exchange.close();
        });
    }

    private RecordedRequest record(HttpExchange exchange) throws IOException {
        RecordedRequest req = new RecordedRequest();
        req.method = exchange.getRequestMethod();
        req.path = exchange.getRequestURI().getPath();
        req.authorization = exchange.getRequestHeaders().getFirst("Authorization");
        req.contentType = exchange.getRequestHeaders().getFirst("Content-Type");
        req.body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        return req;
    }

    private static final class RecordedRequest {
        String method;
        String path;
        String authorization;
        String contentType;
        String body;
    }
}