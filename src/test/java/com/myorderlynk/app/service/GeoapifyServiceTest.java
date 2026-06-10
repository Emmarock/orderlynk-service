package com.myorderlynk.app.service;

import com.myorderlynk.app.dto.AddressDtos.AddressSuggestion;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Drives {@link GeoapifyService} against a JDK {@link HttpServer} standing in for Geoapify, so we
 * exercise the real RestClient path: request shape (key/limit/format/filter), response mapping,
 * country normalisation, and the graceful fallbacks (short query, unconfigured, upstream error).
 */
class GeoapifyServiceTest {

    private static final String AUTOCOMPLETE_PATH = "/v1/geocode/autocomplete";

    private static final String ONE_RESULT = """
            {
              "results": [
                {
                  "housenumber": "10",
                  "street": "Downing Street",
                  "city": "London",
                  "state": "England",
                  "postcode": "SW1A 2AA",
                  "country": "United Kingdom",
                  "country_code": "gb",
                  "formatted": "10 Downing Street, London SW1A 2AA, United Kingdom",
                  "rank": { "confidence": 0.95 }
                }
              ]
            }
            """;

    private HttpServer server;
    private String baseUrl;
    private final AtomicReference<String> lastQuery = new AtomicReference<>();

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        lastQuery.set(null);
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private GeoapifyService service(String apiKey) {
        return new GeoapifyService(apiKey, baseUrl);
    }

    @Test
    void mapsResultsAndSendsKeyLimitAndFormat() {
        stub(200, ONE_RESULT);

        List<AddressSuggestion> results = service("test-key").autocomplete("10 Downing", null);

        assertThat(results).hasSize(1);
        AddressSuggestion s = results.get(0);
        assertThat(s.houseNumber()).isEqualTo("10");
        assertThat(s.street()).isEqualTo("Downing Street");
        assertThat(s.city()).isEqualTo("London");
        assertThat(s.state()).isEqualTo("England");
        assertThat(s.postcode()).isEqualTo("SW1A 2AA");
        assertThat(s.country()).isEqualTo("United Kingdom");
        assertThat(s.formatted()).isEqualTo("10 Downing Street, London SW1A 2AA, United Kingdom");
        assertThat(s.confidence()).isEqualTo(0.95);

        String query = decodedQuery();
        assertThat(query).contains("text=10 Downing");
        assertThat(query).contains("apiKey=test-key");
        assertThat(query).contains("format=json");
        assertThat(query).contains("limit=6");
        assertThat(query).as("no country → no filter").doesNotContain("filter=");
    }

    @Test
    void resolvesCountryNameToIsoFilter() {
        stub(200, ONE_RESULT);

        service("test-key").autocomplete("10 Downing", "Canada");

        assertThat(decodedQuery()).contains("filter=countrycode:ca");
    }

    @Test
    void acceptsIsoCountryCodeDirectly() {
        stub(200, ONE_RESULT);

        service("test-key").autocomplete("10 Downing", "CA");

        assertThat(decodedQuery()).contains("filter=countrycode:ca");
    }

    @Test
    void omitsFilterForUnknownCountry() {
        stub(200, ONE_RESULT);

        service("test-key").autocomplete("10 Downing", "Atlantis");

        assertThat(decodedQuery()).doesNotContain("filter=");
    }

    @Test
    void skipsCallForShortQuery() {
        stub(200, ONE_RESULT);

        List<AddressSuggestion> results = service("test-key").autocomplete("ab", "Canada");

        assertThat(results).isEmpty();
        assertThat(lastQuery.get()).as("no HTTP call for sub-3-char query").isNull();
    }

    @Test
    void returnsEmptyWhenUnconfigured() {
        stub(200, ONE_RESULT);

        List<AddressSuggestion> results = service("").autocomplete("10 Downing", "Canada");

        assertThat(results).isEmpty();
        assertThat(lastQuery.get()).as("no HTTP call when API key is blank").isNull();
    }

    @Test
    void returnsEmptyOnUpstreamError() {
        stub(500, "{\"error\":\"boom\"}");

        List<AddressSuggestion> results = service("test-key").autocomplete("10 Downing", null);

        assertThat(results).as("a geocoder failure must not break the form").isEmpty();
        assertThat(lastQuery.get()).as("the call was attempted").isNotNull();
    }

    private String decodedQuery() {
        String raw = lastQuery.get();
        assertThat(raw).as("expected an autocomplete request").isNotNull();
        return URLDecoder.decode(raw, StandardCharsets.UTF_8);
    }

    private void stub(int status, String responseBody) {
        server.createContext(AUTOCOMPLETE_PATH, exchange -> {
            lastQuery.set(exchange.getRequestURI().getRawQuery());
            respond(exchange, status, responseBody);
        });
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}