package com.jobhunt.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * Chooses the AI provider at startup.
 *
 * <p>The key is read from the {@code AI_API_KEY} environment variable. When it is absent the
 * application still starts, using a provider that reports itself as unavailable, so the
 * deterministic matching features keep working.
 */
@Configuration
public class AiConfiguration {

    @Bean
    public AiProvider aiProvider(RestClient.Builder builder,
                                 ObjectMapper objectMapper,
                                 @Value("${app.ai.api-key:}") String apiKey,
                                 @Value("${app.ai.base-url:https://api.openai.com/v1}") String baseUrl,
                                 @Value("${app.ai.model:gpt-4o-mini}") String model,
                                 @Value("${app.ai.timeout-seconds:30}") long timeoutSeconds) {

        if (apiKey == null || apiKey.isBlank()) {
            return new NoopAiProvider();
        }

        return new OpenAiCompatibleAiProvider(
                builder,
                objectMapper,
                baseUrl,
                apiKey,
                model,
                Duration.ofSeconds(timeoutSeconds));
    }
}
