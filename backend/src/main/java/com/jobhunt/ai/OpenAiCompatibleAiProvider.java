package com.jobhunt.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * Talks to any OpenAI-compatible chat-completions endpoint (OpenAI itself, Azure OpenAI,
 * OpenRouter, a local server, ...). The base URL and model are configuration, so no vendor
 * is baked in.
 *
 * <p>The API key comes from configuration, which is populated from the environment only -
 * it is never logged, never returned to a client, and never sent to the browser.
 */
public class OpenAiCompatibleAiProvider implements AiProvider {

    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleAiProvider.class);

    private final RestClient client;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String model;

    public OpenAiCompatibleAiProvider(RestClient.Builder builder,
                                      ObjectMapper objectMapper,
                                      String baseUrl,
                                      String apiKey,
                                      String model,
                                      Duration timeout) {
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
        this.model = model;

        String normalisedBase = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.client = builder
                .baseUrl(normalisedBase)
                .requestFactory(new org.springframework.http.client.SimpleClientHttpRequestFactory() {{
                    setConnectTimeout((int) timeout.toMillis());
                    setReadTimeout((int) timeout.toMillis());
                }})
                .build();
    }

    @Override
    public String name() {
        return "openai-compatible";
    }

    @Override
    public boolean isAvailable() {
        return apiKey != null && !apiKey.isBlank();
    }

    @Override
    public String complete(String systemPrompt, String userPrompt) {
        if (!isAvailable()) {
            throw new IllegalStateException("AI provider is not configured");
        }

        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("model", model);
        payload.put("temperature", 0.2);

        ArrayNode messages = payload.putArray("messages");
        messages.addObject().put("role", "system").put("content", systemPrompt);
        messages.addObject().put("role", "user").put("content", userPrompt);

        try {
            String response = client.post()
                    .uri("/chat/completions")
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload.toString())
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(response);
            JsonNode content = root.path("choices").path(0).path("message").path("content");
            if (content.isMissingNode() || content.isNull()) {
                throw new IllegalStateException("The AI provider returned no content");
            }
            return content.asText();
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            // Deliberately does not include the request body or the key.
            log.warn("AI request failed: {}", ex.getMessage());
            throw new IllegalStateException("The AI provider could not be reached. Please try again.");
        }
    }
}
