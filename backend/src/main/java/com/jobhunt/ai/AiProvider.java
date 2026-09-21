package com.jobhunt.ai;

/**
 * Abstraction over a text-generation provider.
 *
 * <p>Implemented so the application never talks to a vendor directly and so the "no provider
 * configured" case is a first-class state rather than a crash. Keys are read from the
 * environment only and are never exposed to the browser.
 */
public interface AiProvider {

    /** Short identifier reported to clients, e.g. "openai-compatible" or "none". */
    String name();

    /** True when the provider has everything it needs to make a call. */
    boolean isAvailable();

    /**
     * Sends a system + user prompt and returns the generated text.
     *
     * @throws IllegalStateException when the provider is not available
     */
    String complete(String systemPrompt, String userPrompt);
}
