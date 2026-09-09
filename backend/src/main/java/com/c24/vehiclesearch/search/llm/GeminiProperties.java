package com.c24.vehiclesearch.search.llm;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param model         a pinned stable id, not an alias — a silent model swap
 *                      changes parse behaviour with no code change to review.
 * @param timeoutMs     the LLM is an enhancement, never a dependency. When it
 *                      does not answer quickly the rules result ships instead.
 * @param maxAttempts   attempts per query, for transient 503s only. Quota
 *                      exhaustion is never retried in the request path — the
 *                      free tier's retry window is tens of seconds, and a
 *                      slightly worse parse now beats a correct one after the
 *                      user has given up.
 * @param maxDailyCalls hard budget. A public endpoint with a paid model behind
 *                      it is a wallet-drain vector; past the cap we serve rules.
 */
@ConfigurationProperties(prefix = "gemini")
public record GeminiProperties(
        boolean enabled,
        String apiKey,
        String baseUrl,
        String model,
        int timeoutMs,
        int maxDailyCalls,
        int maxAttempts) {

    public GeminiProperties {
        baseUrl = (baseUrl == null || baseUrl.isBlank())
                ? "https://generativelanguage.googleapis.com" : baseUrl;
        model = (model == null || model.isBlank()) ? "gemini-3.5-flash-lite" : model;
        timeoutMs = timeoutMs <= 0 ? 4000 : timeoutMs;
        maxDailyCalls = maxDailyCalls <= 0 ? 500 : maxDailyCalls;
        maxAttempts = maxAttempts <= 0 ? 2 : maxAttempts;
    }

    /** Enabled means configured: a blank key is a disabled integration, not an error. */
    public boolean usable() {
        return enabled && apiKey != null && !apiKey.isBlank();
    }
}
