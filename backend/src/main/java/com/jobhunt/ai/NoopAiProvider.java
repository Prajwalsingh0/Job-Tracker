package com.jobhunt.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The default provider. Reports itself as unavailable and refuses to generate anything.
 *
 * <p>It exists so the application starts and works without an AI key, and so any feature
 * that needs generation can say "not configured" honestly instead of returning invented
 * content.
 */
public class NoopAiProvider implements AiProvider {

    private static final Logger log = LoggerFactory.getLogger(NoopAiProvider.class);

    @Override
    public String name() {
        return "none";
    }

    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public String complete(String systemPrompt, String userPrompt) {
        log.debug("Generation requested but no AI provider is configured");
        throw new IllegalStateException(
                "No AI provider is configured. Set AI_API_KEY (and optionally AI_PROVIDER / "
                        + "AI_MODEL / AI_BASE_URL) to enable generated content.");
    }
}
