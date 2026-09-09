package com.c24.vehiclesearch.search;

import com.c24.vehiclesearch.eval.GoldenSet;
import com.c24.vehiclesearch.search.concept.ConceptDictionary;
import com.c24.vehiclesearch.search.parse.RulesParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * The deterministic parser against the golden set, with no database and no
 * network. A broken parse fails here in milliseconds rather than surfacing as
 * "no results" during a demo.
 */
class RulesParserGoldenTest {

    private static final RulesParser PARSER = new RulesParser(new ConceptDictionary());

    static List<GoldenSet.Case> goldenCases() {
        return GoldenSet.load();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("goldenCases")
    @DisplayName("natural language parses to the expected filter spec")
    void parsesAsExpected(GoldenSet.Case c) {
        var spec = PARSER.parse(c.query()).spec();
        assertThat(GoldenSet.mismatches(spec, c.expect()))
                .as("query: %s", c.query())
                .isEmpty();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("goldenCases")
    @DisplayName("no query can make the parser throw")
    void neverThrows(GoldenSet.Case c) {
        assertThatCode(() -> PARSER.parse(c.query())).doesNotThrowAnyException();
    }
}
