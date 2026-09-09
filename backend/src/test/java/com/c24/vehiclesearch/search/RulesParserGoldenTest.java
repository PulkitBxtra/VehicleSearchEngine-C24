package com.c24.vehiclesearch.search;

import com.c24.vehiclesearch.search.concept.ConceptDictionary;
import com.c24.vehiclesearch.search.parse.RulesParser;
import com.c24.vehiclesearch.search.spec.Constraints;
import com.c24.vehiclesearch.search.spec.FilterSpec;
import com.c24.vehiclesearch.search.spec.NumRange;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * The golden set: natural language in, expected FilterSpec out.
 *
 * This is the regression net for every future change to the vocabulary or the
 * parser. It runs with no database and no network, so a broken parse fails in
 * seconds rather than surfacing as "no results" in a demo. When the LLM path
 * lands, it is graded against this same file, which is what makes the two
 * parsers comparable instead of merely both present.
 */
class RulesParserGoldenTest {

    private static final RulesParser PARSER = new RulesParser(new ConceptDictionary());

    record Case(String query, JsonNode expect) {
        @Override public String toString() { return query; }
    }

    static List<Case> goldenCases() throws Exception {
        var mapper = new ObjectMapper();
        try (InputStream in = RulesParserGoldenTest.class.getResourceAsStream("/eval/queries.json")) {
            JsonNode root = mapper.readTree(in);
            return StreamSupport.stream(root.spliterator(), false)
                    .map(n -> new Case(n.get("query").asText(), n.get("expect")))
                    .collect(Collectors.toList());
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("goldenCases")
    @DisplayName("natural language parses to the expected filter spec")
    void parsesAsExpected(Case c) {
        FilterSpec spec = PARSER.parse(c.query()).spec();
        Constraints k = spec.constraints();
        JsonNode e = c.expect();

        if (e.has("bodyTypes"))     assertThat(names(k.bodyTypes())).containsExactlyInAnyOrderElementsOf(strings(e, "bodyTypes"));
        if (e.has("fuelTypes"))     assertThat(names(k.fuelTypes())).containsExactlyInAnyOrderElementsOf(strings(e, "fuelTypes"));
        if (e.has("transmissions")) assertThat(names(k.transmissions())).containsExactlyInAnyOrderElementsOf(strings(e, "transmissions"));

        if (e.has("priceMax")) assertThat(max(k.priceInr())).isEqualTo(e.get("priceMax").asLong());
        if (e.has("priceMin")) assertThat(min(k.priceInr())).isEqualTo(e.get("priceMin").asLong());
        if (e.has("kmMax"))    assertThat(max(k.kmDriven())).isEqualTo(e.get("kmMax").asLong());
        if (e.has("kmMin"))    assertThat(min(k.kmDriven())).isEqualTo(e.get("kmMin").asLong());
        if (e.has("yearMin"))  assertThat(min(k.year())).isEqualTo(e.get("yearMin").asLong());
        if (e.has("yearMax"))  assertThat(max(k.year())).isEqualTo(e.get("yearMax").asLong());
        if (e.has("seatsMin")) assertThat(min(k.seats())).isEqualTo(e.get("seatsMin").asLong());
        if (e.has("emiMax"))   assertThat(max(k.emiMonthly())).isEqualTo(e.get("emiMax").asLong());
        if (e.has("maxOwners")) assertThat(k.maxOwners()).isEqualTo(e.get("maxOwners").asLong());

        if (e.has("concepts"))  assertThat(spec.appliedConcepts()).containsAll(strings(e, "concepts"));
        if (e.has("freeText"))  assertThat(spec.freeText()).isEqualTo(e.get("freeText").asText());
        if (e.has("sort"))      assertThat(spec.sort().name()).isEqualTo(e.get("sort").asText());

        if (e.path("unsatisfiable").asBoolean(false)) {
            assertThat(k.isUnsatisfiable())
                    .as("contradictory bounds must be detected, not silently returned as an empty page")
                    .isTrue();
        }
        if (e.path("noConstraints").asBoolean(false)) {
            assertThat(k.bodyTypes()).isEmpty();
            assertThat(k.fuelTypes()).isEmpty();
            assertThat(max(k.priceInr())).isNull();
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("goldenCases")
    @DisplayName("no query can make the parser throw")
    void neverThrows(Case c) {
        assertThatCode(() -> PARSER.parse(c.query())).doesNotThrowAnyException();
    }

    // ------------------------------------------------------------------ helpers

    private static List<String> names(List<?> values) {
        return values.stream().map(Object::toString).toList();
    }

    private static List<String> strings(JsonNode node, String field) {
        var out = new ArrayList<String>();
        node.get(field).forEach(n -> out.add(n.asText()));
        return out;
    }

    private static Long max(NumRange r) { return r == null ? null : r.lte(); }
    private static Long min(NumRange r) { return r == null ? null : r.gte(); }
}
