package com.c24.vehiclesearch.search.llm;

import com.c24.vehiclesearch.catalog.BodyType;
import com.c24.vehiclesearch.catalog.Transmission;
import com.c24.vehiclesearch.search.concept.ConceptDictionary;
import com.c24.vehiclesearch.search.spec.SortOption;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The trust boundary, tested directly.
 *
 * Everything arriving from the model is untrusted text that happened to satisfy
 * a schema, so these cases are mostly about what happens when it does not behave.
 */
class LlmParserTest {

    private static final ConceptDictionary CONCEPTS = new ConceptDictionary();

    private final LlmParser parser = new LlmParser(
            new GeminiClient(
                    new GeminiProperties(false, null, null, null, 0, 0, 1),
                    new GeminiContract(CONCEPTS), new ObjectMapper(), RestClient.builder().build()),
            CONCEPTS);

    private static LlmFilterDraft draft(List<String> concepts, List<String> transmissions,
                                        Long priceMax, String sort, String freeText,
                                        List<String> unmapped) {
        return new LlmFilterDraft(concepts, List.of(), List.of(), List.of(), transmissions,
                List.of(), null, priceMax, null, null, null, null, null, null, null, null,
                sort, freeText, unmapped);
    }

    @Test
    @DisplayName("concept keys expand through the dictionary, not through the model")
    void conceptsExpandLocally() {
        var spec = parser.toSpec(draft(List.of("automatic", "family"), List.of(),
                null, null, null, List.of()));

        // The model said "automatic"; concepts.yml decided that means four gearboxes.
        assertThat(spec.constraints().transmissions())
                .containsExactlyInAnyOrder(Transmission.AMT, Transmission.CVT,
                        Transmission.DCT, Transmission.TORQUE_CONVERTER);
        // And that "family" ranks rather than filters.
        assertThat(spec.preferences()).isNotEmpty();
        assertThat(spec.preferences()).allSatisfy(p -> assertThat(p.source()).isEqualTo("family"));
        assertThat(spec.constraints().seats()).isNull();
    }

    @Test
    @DisplayName("a concept key outside the vocabulary becomes a warning, not a silent drop")
    void unknownConceptSurfaces() {
        var spec = parser.toSpec(draft(List.of("teleporting"), List.of(), null, null, null, List.of()));
        assertThat(spec.unmapped()).contains("teleporting");
        assertThat(spec.appliedConcepts()).isEmpty();
    }

    @Test
    @DisplayName("an invalid enum value is discarded rather than reaching SQL")
    void invalidEnumIgnored() {
        var spec = parser.toSpec(draft(List.of(), List.of("SEMI_AUTOMATIC", "AMT"),
                null, null, null, List.of()));
        assertThat(spec.constraints().transmissions()).containsExactly(Transmission.AMT);
    }

    @Test
    @DisplayName("an unrecognised sort falls back to relevance")
    void badSortFallsBack() {
        var spec = parser.toSpec(draft(List.of(), List.of(), null, "BY_VIBES", null, List.of()));
        assertThat(spec.sort()).isEqualTo(SortOption.RELEVANCE);
    }

    @Test
    @DisplayName("numeric bounds and free text carry through")
    void boundsAndTextCarry() {
        var spec = parser.toSpec(draft(List.of("suv"), List.of(), 1_500_000L,
                "PRICE_ASC", "creta", List.of("sunroof")));

        assertThat(spec.constraints().priceInr().lte()).isEqualTo(1_500_000L);
        assertThat(spec.constraints().bodyTypes())
                .containsExactlyInAnyOrder(BodyType.SUV, BodyType.COMPACT_SUV);
        assertThat(spec.freeText()).isEqualTo("creta");
        assertThat(spec.sort()).isEqualTo(SortOption.PRICE_ASC);
        assertThat(spec.unmapped()).contains("sunroof");
    }

    @Test
    @DisplayName("cache keys ignore case and spacing")
    void normalisation() {
        assertThat(LlmParser.normalise("  SUVs   under 15L ")).isEqualTo("suvs under 15l");
    }
}
