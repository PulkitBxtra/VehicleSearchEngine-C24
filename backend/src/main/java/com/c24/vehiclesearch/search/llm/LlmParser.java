package com.c24.vehiclesearch.search.llm;

import com.c24.vehiclesearch.catalog.BodyType;
import com.c24.vehiclesearch.catalog.FuelType;
import com.c24.vehiclesearch.catalog.Transmission;
import com.c24.vehiclesearch.search.concept.ConceptDictionary;
import com.c24.vehiclesearch.search.spec.Constraints;
import com.c24.vehiclesearch.search.spec.FilterSpec;
import com.c24.vehiclesearch.search.spec.NumRange;
import com.c24.vehiclesearch.search.spec.SortOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * Turns the model's draft into a validated {@link FilterSpec}.
 *
 * This is the trust boundary. Everything arriving here is untrusted text that
 * happened to satisfy a schema: enum values are re-parsed defensively, unknown
 * concept keys are demoted to warnings rather than silently dropped, and nothing
 * reaches SQL that did not come through a typed field.
 */
@Service
public class LlmParser {

    private static final Logger log = LoggerFactory.getLogger(LlmParser.class);

    private final GeminiClient client;
    private final ConceptDictionary concepts;

    public LlmParser(GeminiClient client, ConceptDictionary concepts) {
        this.client = client;
        this.concepts = concepts;
    }

    /**
     * Cached on the normalised query. Marketplace query distributions are
     * Zipfian, so in steady state the head is served from memory and the model
     * only ever sees the tail.
     */
    // Spring's cache abstraction unwraps Optional return values, so #result here
    // is the FilterSpec itself and is null when the Optional was empty --
    // "#result.isEmpty()" would dereference null on every failed parse.
    @Cacheable(value = "llmParses", key = "#normalisedQuery", unless = "#result == null")
    public Optional<FilterSpec> parse(String normalisedQuery) {
        return client.parse(normalisedQuery).map(this::toSpec);
    }

    /** Same normalisation as the cache key, so "SUVs  under 15L" and "suvs under 15l" share an entry. */
    public static String normalise(String query) {
        return query == null ? "" : query.trim().toLowerCase().replaceAll("\\s+", " ");
    }

    FilterSpec toSpec(LlmFilterDraft draft) {
        FilterSpec spec = FilterSpec.empty();

        for (String key : draft.concepts()) {
            if (concepts.get(key).isPresent()) {
                spec = concepts.apply(spec, key);
            } else {
                // The schema constrains this to a closed enum, so reaching here
                // means the contract and the dictionary have drifted apart.
                log.warn("Model emitted unknown concept key '{}'", key);
                spec = spec.addUnmapped(key);
            }
        }

        spec = spec.withConstraints(spec.constraints().merge(new Constraints(
                draft.makes(),
                parseEnums(draft.bodyTypes(), BodyType::valueOf, "bodyType"),
                parseEnums(draft.fuelTypes(), FuelType::valueOf, "fuelType"),
                parseEnums(draft.transmissions(), Transmission::valueOf, "transmission"),
                draft.cities(),
                range(draft.priceMin(), draft.priceMax()),
                range(null, draft.emiMax()),
                range(draft.kmMin(), draft.kmMax()),
                range(draft.yearMin(), draft.yearMax()),
                range(draft.seatsMin(), null),
                draft.maxOwners(),
                draft.minNcapStars())));

        if (draft.freeText() != null && !draft.freeText().isBlank()) {
            spec = spec.withFreeText(draft.freeText());
        }
        spec = spec.withSort(parseSort(draft.sort()));

        for (String term : draft.unmapped()) {
            spec = spec.addUnmapped(term);
        }
        return spec;
    }

    private static NumRange range(Long gte, Long lte) {
        return (gte == null && lte == null) ? null : new NumRange(gte, lte);
    }

    private <E> List<E> parseEnums(List<String> raw, Function<String, E> parser, String field) {
        return raw.stream()
                .map(v -> {
                    try {
                        return parser.apply(v.trim().toUpperCase());
                    } catch (IllegalArgumentException e) {
                        log.warn("Model emitted invalid {} value '{}'; ignoring", field, v);
                        return null;
                    }
                })
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    private static SortOption parseSort(String raw) {
        if (raw == null || raw.isBlank()) return SortOption.RELEVANCE;
        try {
            return SortOption.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return SortOption.RELEVANCE;
        }
    }
}
