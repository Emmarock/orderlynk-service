package com.myorderlynk.app.integration;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.myorderlynk.app.exception.ApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;
import java.util.Map;

/**
 * Thin client over the OpenAI Chat Completions API. Acts as transport only —
 * callers own the prompt and any post-processing. Degrades gracefully: when no
 * API key is configured the bean still loads and {@link #complete} throws a
 * clean 503 rather than failing at startup.
 */
@Service
@Slf4j
public class OpenAiService {

    private final RestClient client;
    private final String model;
    private final boolean configured;

    public OpenAiService(
            @Value("${app.openai.api-key:}") String apiKey,
            @Value("${app.openai.base-url:https://api.openai.com/v1}") String baseUrl,
            @Value("${app.openai.model:gpt-4o-mini}") String model) {
        this.model = model;
        this.configured = apiKey != null && !apiKey.isBlank();
        this.client = configured
                ? RestClient.builder()
                    .baseUrl(baseUrl)
                    .defaultHeader("Authorization", "Bearer " + apiKey)
                    .build()
                : null;
        if (!configured) {
            log.warn("OpenAI is not configured (app.openai.api-key unset) — AI generation is disabled.");
        }
    }

    public boolean isConfigured() {
        return configured;
    }

    /**
     * Run a single-turn chat completion and return the assistant's message text.
     *
     * @param systemPrompt instruction defining the assistant's role/constraints
     * @param userPrompt   the concrete request
     * @param maxTokens    upper bound on the completion length
     * @param temperature  sampling temperature (higher = more creative)
     */
    public String complete(String systemPrompt, String userPrompt, int maxTokens, double temperature) {
        if (!configured) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "AI generation is not configured");
        }
        Map<String, Object> body = Map.of(
                "model", model,
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userPrompt)),
                "max_tokens", maxTokens,
                "temperature", temperature);
        try {
            ChatResponse resp = client.post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(ChatResponse.class);
            String content = resp == null ? null : resp.firstMessageContent();
            if (content == null || content.isBlank()) {
                throw new ApiException(HttpStatus.BAD_GATEWAY, "AI returned an empty response");
            }
            return content.trim();
        } catch (RestClientResponseException e) {
            // 4xx/5xx from OpenAI — log the detail, surface a safe message.
            log.error("OpenAI request failed ({}): {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new ApiException(HttpStatus.BAD_GATEWAY, "AI service request failed");
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("OpenAI request errored", e);
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Could not reach the AI service");
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ChatResponse(List<Choice> choices) {
        String firstMessageContent() {
            return choices == null || choices.isEmpty() || choices.get(0).message() == null
                    ? null
                    : choices.get(0).message().content();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Choice(Message message) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Message(String role, String content) {
    }
}