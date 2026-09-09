package com.c24.vehiclesearch.search.llm;

import com.c24.vehiclesearch.search.concept.ConceptDictionary;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.http.HttpMethod.POST;

/**
 * Exercises the wire format with no network. The failure cases matter more than
 * the happy one: this integration's contract with the rest of the system is
 * "never throw, return empty and let the rules parser answer".
 */
class GeminiClientTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private MockRestServiceServer server;
    private GeminiClient client;

    @BeforeEach
    void setUp() {
        var builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        var props = new GeminiProperties(true, "test-key", "https://gemini.test",
                "gemini-3.8-flash", 2000, 500, 1);
        client = new GeminiClient(props, new GeminiContract(new ConceptDictionary()),
                MAPPER, builder.build());
    }

    @Test
    @DisplayName("sends the schema and the key, and parses the candidate back")
    void happyPath() {
        server.expect(method(POST))
                .andExpect(header("x-goog-api-key", "test-key"))
                .andRespond(withSuccess(candidate("""
                        {"concepts":["suv"],"makes":[],"bodyTypes":[],"fuelTypes":[],
                         "transmissions":[],"cities":[],"priceMin":null,"priceMax":1500000,
                         "emiMax":null,"kmMin":null,"kmMax":null,"yearMin":null,"yearMax":null,
                         "seatsMin":null,"maxOwners":null,"minNcapStars":null,
                         "sort":"RELEVANCE","freeText":null,"unmapped":[]}
                        """), MediaType.APPLICATION_JSON));

        var draft = client.parse("suvs under 15L");

        assertThat(draft).isPresent();
        assertThat(draft.get().concepts()).containsExactly("suv");
        assertThat(draft.get().priceMax()).isEqualTo(1_500_000L);
        server.verify();
    }

    @Test
    @DisplayName("a provider error returns empty rather than propagating")
    void serverErrorDegrades() {
        server.expect(method(POST)).andRespond(withServerError());
        assertThat(client.parse("anything")).isEmpty();
    }

    @Test
    @DisplayName("a candidate that is not valid JSON returns empty")
    void malformedCandidateDegrades() {
        server.expect(method(POST))
                .andRespond(withSuccess(candidate("not json at all"), MediaType.APPLICATION_JSON));
        assertThat(client.parse("anything")).isEmpty();
    }

    @Test
    @DisplayName("a blocked or truncated response returns empty")
    void noCandidateDegrades() {
        server.expect(method(POST)).andRespond(withSuccess(
                "{\"candidates\":[{\"finishReason\":\"SAFETY\"}]}", MediaType.APPLICATION_JSON));
        assertThat(client.parse("anything")).isEmpty();
    }

    @Test
    @DisplayName("no call is made when the key is missing")
    void disabledWithoutKey() {
        var props = new GeminiProperties(true, "  ", "https://gemini.test", null, 0, 0, 1);
        var offline = new GeminiClient(props, new GeminiContract(new ConceptDictionary()),
                MAPPER, RestClient.builder().build());
        assertThat(offline.parse("suvs under 15L")).isEmpty();
        assertThat(offline.callsToday()).isZero();
    }

    @Test
    @DisplayName("the request pins temperature 0 and carries the response schema")
    void requestIsDeterministicAndConstrained() {
        JsonNode body = client.requestBody("suvs under 15L");
        var generation = body.path("generationConfig");

        assertThat(generation.path("temperature").asInt()).isZero();
        assertThat(generation.path("responseMimeType").asText()).isEqualTo("application/json");
        assertThat(generation.path("responseSchema").path("properties").has("priceMax")).isTrue();
        assertThat(body.path("contents").path(0).path("parts").path(0).path("text").asText())
                .isEqualTo("suvs under 15L");
    }

    @Test
    @DisplayName("a 503 is retried; a 429 is not")
    void retriesCongestionButNotQuota() {
        var builder = RestClient.builder();
        var mock = MockRestServiceServer.bindTo(builder).build();
        var retrying = new GeminiClient(
                new GeminiProperties(true, "k", "https://gemini.test", "m", 2000, 500, 2),
                new GeminiContract(new ConceptDictionary()), MAPPER, builder.build());

        // Congestion clears on the second attempt.
        mock.expect(method(POST)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        mock.expect(method(POST)).andRespond(withSuccess(candidate("""
                {"concepts":[],"makes":[],"bodyTypes":[],"fuelTypes":[],"transmissions":[],
                 "cities":[],"priceMin":null,"priceMax":900000,"emiMax":null,"kmMin":null,
                 "kmMax":null,"yearMin":null,"yearMax":null,"seatsMin":null,"maxOwners":null,
                 "minNcapStars":null,"sort":"RELEVANCE","freeText":null,"unmapped":[]}
                """), MediaType.APPLICATION_JSON));

        assertThat(retrying.parse("anything")).isPresent();
        mock.verify();

        // Quota is a wall, not congestion: give up immediately so the rules
        // parser can answer instead of the user waiting out the window.
        var quotaBuilder = RestClient.builder();
        var quotaMock = MockRestServiceServer.bindTo(quotaBuilder).build();
        var quotaClient = new GeminiClient(
                new GeminiProperties(true, "k", "https://gemini.test", "m", 2000, 500, 3),
                new GeminiContract(new ConceptDictionary()), MAPPER, quotaBuilder.build());

        quotaMock.expect(method(POST)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        assertThat(quotaClient.parse("anything")).isEmpty();
        assertThat(quotaClient.quotaFailures()).isEqualTo(1);
        quotaMock.verify();
    }

    private static String candidate(String text) {
        try {
            var root = MAPPER.createObjectNode();
            root.set("candidates", MAPPER.createArrayNode().add(
                    MAPPER.createObjectNode().set("content", MAPPER.createObjectNode()
                            .set("parts", MAPPER.createArrayNode().add(
                                    MAPPER.createObjectNode().put("text", text))))));
            return MAPPER.writeValueAsString(root);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
