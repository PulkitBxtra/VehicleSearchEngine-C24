package com.c24.vehiclesearch.search.concept;

import com.c24.vehiclesearch.search.spec.Constraints;
import com.c24.vehiclesearch.search.spec.Preference;

import java.util.List;

/**
 * One entry from concepts.yml.
 *
 * A concept may contribute constraints, preferences, or both. "Diesel" is pure
 * constraint; "family" is pure preference; "well maintained" is both.
 */
public record ConceptDefinition(
        String label,
        List<String> terms,
        Constraints constraints,
        List<Preference> preferences) {

    public ConceptDefinition {
        terms       = terms == null ? List.of() : List.copyOf(terms);
        preferences = preferences == null ? List.of() : List.copyOf(preferences);
        constraints = constraints == null ? Constraints.empty() : constraints;
    }
}
