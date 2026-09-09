package com.c24.vehiclesearch.search.concept;

import com.c24.vehiclesearch.search.spec.FilterSpec;
import com.c24.vehiclesearch.search.spec.Preference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.dataformat.yaml.YAMLFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Loads concepts.yml and applies concepts to a {@link FilterSpec}.
 *
 * Deliberately a plain component over a static file rather than analyzer config
 * inside a search engine: the vocabulary is the part of this system most likely
 * to change weekly, and it should change by editing a reviewed file, not by
 * reindexing.
 */
@Component
public class ConceptDictionary {

    private static final String LOCATION = "concepts/concepts.yml";

    private final Map<String, ConceptDefinition> concepts;

    /** Term -> concept key, longest term first so "high safety rating" wins over "safety". */
    private final List<Map.Entry<String, String>> termIndex;

    public ConceptDictionary() {
        this.concepts = load();
        this.termIndex = concepts.entrySet().stream()
                .flatMap(e -> e.getValue().terms().stream()
                        .map(t -> Map.entry(t.toLowerCase(), e.getKey())))
                .sorted(Comparator.comparingInt((Map.Entry<String, String> e) -> e.getKey().length())
                        .reversed())
                .toList();
    }

    private record ConceptFile(Map<String, ConceptDefinition> concepts) {}

    private static Map<String, ConceptDefinition> load() {
        var mapper = new ObjectMapper(new YAMLFactory());
        try (InputStream in = new ClassPathResource(LOCATION).getInputStream()) {
            ConceptFile file = mapper.readValue(in, ConceptFile.class);
            if (file == null || file.concepts() == null || file.concepts().isEmpty()) {
                throw new IllegalStateException(LOCATION + " defined no concepts");
            }
            return new LinkedHashMap<>(file.concepts());
        } catch (IOException e) {
            // Fail at startup rather than silently degrading every search that
            // depends on domain vocabulary.
            throw new IllegalStateException("Could not load " + LOCATION, e);
        }
    }

    public Map<String, ConceptDefinition> all() {
        return Map.copyOf(concepts);
    }

    public Optional<ConceptDefinition> get(String key) {
        return Optional.ofNullable(concepts.get(key));
    }

    /** Terms ordered longest-first, for greedy left-to-right matching. */
    public List<Map.Entry<String, String>> termIndex() {
        return termIndex;
    }

    /**
     * Fold a concept into a spec: constraints merge, preferences append with
     * their source stamped so the response can explain why a car ranked where
     * it did.
     */
    public FilterSpec apply(FilterSpec spec, String key) {
        ConceptDefinition def = concepts.get(key);
        if (def == null) return spec;

        List<Preference> sourced = def.preferences().stream()
                .map(p -> new Preference(p.field(), p.op(), p.value(), p.boost(), key))
                .toList();

        return spec.withConstraints(spec.constraints().merge(def.constraints()))
                .addPreferences(sourced)
                .addConcept(key);
    }
}
