package com.c24.vehiclesearch.search.llm;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Thin HTTP client for the Gemini generateContent endpoint.
 *
 * Deliberately hand-rolled rather than pulled from an SDK: it is one POST with
 * one response shape, and a reviewer can read the whole integration in a screen.
 * An SDK would add a dependency, a version to track and an abstraction over the
 * exact thing this design wants visible — the schema we constrain the model to.
 *
 * Every failure path returns empty rather than throwing. Callers fall back to
 * the deterministic parser, so a bad day at the model provider degrades result
 * quality instead of returning 500s.
 */
@Component
public class GeminiClient {

    private static final Logger log = LoggerFactory.getLogger(GeminiClient.class);

    private final GeminiProperties props;
    private final GeminiContract contract;
    private final ObjectMapper mapper;
    private final RestClient http;

    private final AtomicInteger callsToday = new AtomicInteger();
    private final AtomicInteger quotaFailures = new AtomicInteger();
    private final AtomicInteger otherFailures = new AtomicInteger();
    private volatile LocalDate budgetDate = LocalDate.now();

    /**
     * Takes an already-configured client rather than a builder so tests can bind
     * a MockRestServiceServer to it; timeouts are applied in {@link
     * com.c24.vehiclesearch.config.GeminiClientConfig}.
     */
    public GeminiClient(GeminiProperties props, GeminiContract contract,
                        ObjectMapper mapper, RestClient http) {
        this.props = props;
        this.contract = contract;
        this.mapper = mapper;
        this.http = http;
    }

    public Optional<LlmFilterDraft> parse(String query) {
        if (!props.usable()) return Optional.empty();
        if (!withinBudget()) {
            log.warn("Gemini daily call budget of {} exhausted; serving rules-parsed results.",
                    props.maxDailyCalls());
            return Optional.empty();
        }

        for (int attempt = 1; attempt <= props.maxAttempts(); attempt++) {
            try {
                JsonNode response = http.post()
                        .uri("%s/v1beta/models/%s:generateContent"
                                .formatted(props.baseUrl(), props.model()))
                        .header("x-goog-api-key", props.apiKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(requestBody(query))
                        .retrieve()
                        .body(JsonNode.class);

                return extract(response);

            } catch (HttpClientErrorException.TooManyRequests e) {
                // Quota, not congestion. The free tier's window is tens of
                // seconds; nobody waits that long for a search box, and the
                // rules parser already has an answer ready.
                quotaFailures.incrementAndGet();
                log.warn("Gemini quota exhausted; falling back to rules for [{}]", query);
                return Optional.empty();

            } catch (HttpServerErrorException.ServiceUnavailable e) {
                // Genuine transient congestion — worth one short retry.
                if (attempt < props.maxAttempts()) {
                    sleep(250L * attempt);
                    continue;
                }
                otherFailures.incrementAndGet();
                log.warn("Gemini unavailable after {} attempts for [{}]", attempt, query);
                return Optional.empty();

            } catch (Exception e) {
                otherFailures.incrementAndGet();
                log.warn("Gemini parse failed for query [{}]: {}", query, e.toString());
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    ObjectNode requestBody(String query) {
        ObjectNode root = mapper.createObjectNode();

        root.set("system_instruction", mapper.createObjectNode().set("parts",
                mapper.createArrayNode().add(
                        mapper.createObjectNode().put("text", contract.systemInstruction()))));

        root.set("contents", mapper.createArrayNode().add(
                mapper.createObjectNode()
                        .put("role", "user")
                        .set("parts", mapper.createArrayNode().add(
                                mapper.createObjectNode().put("text", query)))));

        ObjectNode generation = mapper.createObjectNode()
                .put("responseMimeType", "application/json")
                // Parsing is extraction, not composition; sampling only adds
                // variance to a result we cache and compare against a golden set.
                .put("temperature", 0);
        generation.set("responseSchema", contract.responseSchema());
        root.set("generationConfig", generation);

        return root;
    }

    private Optional<LlmFilterDraft> extract(JsonNode response) {
        if (response == null) return Optional.empty();

        JsonNode text = response.path("candidates").path(0)
                .path("content").path("parts").path(0).path("text");
        if (text.isMissingNode() || text.asText().isBlank()) {
            // A blocked or truncated candidate looks like this. finishReason
            // tells us which, and is worth logging over a bare "empty response".
            log.warn("Gemini returned no usable candidate (finishReason={})",
                    response.path("candidates").path(0).path("finishReason").asText("unknown"));
            return Optional.empty();
        }

        try {
            return Optional.of(mapper.readValue(text.asText(), LlmFilterDraft.class));
        } catch (Exception e) {
            log.warn("Gemini returned JSON that did not match the draft schema: {}", e.toString());
            return Optional.empty();
        }
    }

    /** Resets at midnight; a coarse guard, not an accounting system. */
    private boolean withinBudget() {
        LocalDate today = LocalDate.now();
        if (!today.equals(budgetDate)) {
            synchronized (this) {
                if (!today.equals(budgetDate)) {
                    budgetDate = today;
                    callsToday.set(0);
                }
            }
        }
        return callsToday.incrementAndGet() <= props.maxDailyCalls();
    }

    public int callsToday() { return callsToday.get(); }

    /** Separated so a quota wall is not mistaken for a model that parses badly. */
    public int quotaFailures() { return quotaFailures.get(); }

    public int otherFailures() { return otherFailures.get(); }
}
