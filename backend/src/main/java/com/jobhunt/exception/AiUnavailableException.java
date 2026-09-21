package com.jobhunt.exception;

/**
 * Thrown when generated content is requested but no AI provider is configured. Mapped to
 * HTTP 503 with a setup instruction rather than silently returning invented content.
 */
public class AiUnavailableException extends RuntimeException {

    public AiUnavailableException(String providerName) {
        super("No AI provider is configured (current provider: " + providerName + "). Set AI_API_KEY "
                + "and optionally AI_PROVIDER, AI_MODEL and AI_BASE_URL to enable generated content. "
                + "The resume-to-job matching feature works without a provider.");
    }
}
