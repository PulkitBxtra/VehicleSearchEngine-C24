package com.c24.vehiclesearch.eval;

import com.c24.vehiclesearch.search.concept.ConceptDictionary;
import com.c24.vehiclesearch.search.llm.GeminiClient;
import com.c24.vehiclesearch.search.llm.GeminiContract;
import com.c24.vehiclesearch.search.llm.GeminiProperties;
import com.c24.vehiclesearch.search.llm.LlmParser;
import com.c24.vehiclesearch.search.parse.RulesParser;
import com.c24.vehiclesearch.search.spec.FilterSpec;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Grades the live model against the same golden set the rules parser is graded
 * on, and prints a side-by-side report.
 *
 * Excluded from the default build — it costs money and needs a network. Run it
 * deliberately:
 *
 *   GEMINI_API_KEY=... ./mvnw test -DexcludedGroups= -Dtest=GeminiLiveEvalTest
 *
 * Paced on purpose. The Gemini free tier allows 20 requests per minute, and a
 * 28-query burst trips it — the API reports the overflow as 503 "high demand"
 * for a while before it admits to 429 RESOURCE_EXHAUSTED, which reads as a bad
 * model rather than a throttled client. Spacing the calls is the difference
 * between measuring parse quality and measuring quota.
 *
 * The assertion floor is deliberately loose. The number this produces is the
 * point: it tells you whether a prompt edit helped or hurt, which is otherwise
 * guesswork.
 */
@Tag("live")
class GeminiLiveEvalTest {

    private static final double REQUIRED_ACCURACY = 0.75;

    /** ~17 requests/minute, comfortably inside the free tier's 20. */
    private static final long PACE_MS = Long.parseLong(
            System.getenv().getOrDefault("EVAL_PACE_MS", "3500"));
    private static final long QUOTA_BACKOFF_MS = 35_000;
    private static final int QUOTA_RETRIES = 2;

    @Test
    @DisplayName("the model parses the golden set at least as well as the floor")
    void gradeAgainstGoldenSet() {
        String key = System.getenv("GEMINI_API_KEY");
        Assumptions.assumeTrue(key != null && !key.isBlank(),
                "GEMINI_API_KEY not set — skipping the live evaluation");

        var concepts = new ConceptDictionary();
        var props = new GeminiProperties(true, key, null,
                System.getenv().getOrDefault("GEMINI_MODEL", "gemini-3.8-flash"),
                20_000, 1_000, 3);
        var rules = new RulesParser(concepts);

        var client = new GeminiClient(props, new GeminiContract(concepts), new ObjectMapper(),
                RestClient.builder().build());
        var pacedParser = new LlmParser(client, concepts);

        // EVAL_ONLY re-checks a handful of queries after a prompt edit without
        // paying for, or waiting out, a full graded run.
        String only = System.getenv("EVAL_ONLY");
        List<GoldenSet.Case> cases = GoldenSet.load().stream()
                .filter(c -> only == null || only.isBlank()
                        || java.util.Arrays.stream(only.split("\\|"))
                                .anyMatch(f -> c.query().toLowerCase().contains(f.trim().toLowerCase())))
                .toList();
        if (cases.isEmpty()) throw new IllegalStateException("EVAL_ONLY matched no queries");
        int llmPassed = 0, rulesPassed = 0, unanswered = 0;

        System.out.printf("%n%-44s  %-6s  %-6s%n", "QUERY", "RULES", "LLM");
        System.out.println("-".repeat(62));

        for (int i = 0; i < cases.size(); i++) {
            GoldenSet.Case c = cases.get(i);

            var rulesMismatches = GoldenSet.mismatches(rules.parse(c.query()).spec(), c.expect());
            if (rulesMismatches.isEmpty()) rulesPassed++;

            Optional<FilterSpec> fromModel = askWithQuotaRetries(pacedParser, client, c.query());

            List<String> llmMismatches;
            if (fromModel.isEmpty()) {
                unanswered++;
                llmMismatches = List.of("no response");
            } else {
                llmMismatches = GoldenSet.mismatches(fromModel.get(), c.expect());
                if (llmMismatches.isEmpty()) llmPassed++;
            }

            System.out.printf("%-44s  %-6s  %-6s%n",
                    truncate(c.query()),
                    rulesMismatches.isEmpty() ? "pass" : "FAIL",
                    llmMismatches.isEmpty() ? "pass" : "FAIL");
            llmMismatches.stream().filter(m -> !m.equals("no response"))
                    .forEach(m -> System.out.println("      llm: " + m));

            if (i < cases.size() - 1) pause(PACE_MS);
        }

        // Unanswered queries are a quota story, not a parse story, so accuracy is
        // reported over the ones that actually got an answer as well as overall.
        int answered = cases.size() - unanswered;
        double llmAccuracy = (double) llmPassed / cases.size();
        double answeredAccuracy = answered == 0 ? 0 : (double) llmPassed / answered;

        System.out.println("-".repeat(62));
        System.out.printf("rules      %d/%d (%.0f%%)%n", rulesPassed, cases.size(),
                100.0 * rulesPassed / cases.size());
        System.out.printf("llm        %d/%d (%.0f%%) overall, %d/%d (%.0f%%) of answered%n",
                llmPassed, cases.size(), llmAccuracy * 100,
                llmPassed, answered, answeredAccuracy * 100);
        System.out.printf("unanswered %d  (quota %d, other %d)%n%n",
                unanswered, client.quotaFailures(), client.otherFailures());

        assertThat(unanswered)
                .as("queries the model never answered — rerun with a larger EVAL_PACE_MS if this is high")
                .isLessThan(cases.size() / 4);
        assertThat(answeredAccuracy)
                .as("live model accuracy over answered queries")
                .isGreaterThanOrEqualTo(REQUIRED_ACCURACY);
    }

    /** Quota is a window, not a wall: for a batch job it is worth waiting out. */
    private static Optional<FilterSpec> askWithQuotaRetries(
            LlmParser parser, GeminiClient client, String query) {
        for (int attempt = 0; attempt <= QUOTA_RETRIES; attempt++) {
            int quotaBefore = client.quotaFailures();
            Optional<FilterSpec> result = parser.parse(LlmParser.normalise(query));
            if (result.isPresent()) return result;
            if (client.quotaFailures() == quotaBefore) return Optional.empty();  // a real failure
            System.out.println("      (quota window hit, waiting " + QUOTA_BACKOFF_MS / 1000 + "s)");
            pause(QUOTA_BACKOFF_MS);
        }
        return Optional.empty();
    }

    private static void pause(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static String truncate(String s) {
        return s.length() <= 42 ? s : s.substring(0, 39) + "...";
    }
}
