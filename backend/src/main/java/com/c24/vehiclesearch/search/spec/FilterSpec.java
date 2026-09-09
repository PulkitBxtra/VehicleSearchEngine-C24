package com.c24.vehiclesearch.search.spec;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.Valid;

import java.util.ArrayList;
import java.util.List;

/**
 * The one contract in this system.
 *
 * Natural-language search and the facet UI both produce a FilterSpec, and only
 * a FilterSpec reaches the query compiler. That is deliberate: if the language
 * path could express something the facet path could not, we would have built a
 * second search engine with no tests and no way to reproduce a bad result.
 *
 * Removing a filter chip in the UI re-submits a FilterSpec directly, which is
 * why refining a search costs no LLM call.
 */
public record FilterSpec(
        @Valid Constraints constraints,
        @Valid List<Preference> preferences,
        String freeText,
        SortOption sort,
        List<String> appliedConcepts,
        List<String> unmapped) {

    public FilterSpec {
        constraints     = constraints == null ? Constraints.empty() : constraints;
        preferences     = preferences == null ? List.of() : List.copyOf(preferences);
        sort            = sort == null ? SortOption.RELEVANCE : sort;
        appliedConcepts = appliedConcepts == null ? List.of() : List.copyOf(appliedConcepts);
        unmapped        = unmapped == null ? List.of() : List.copyOf(unmapped);
        freeText        = (freeText == null || freeText.isBlank()) ? null : freeText.trim();
    }

    public static FilterSpec empty() {
        return new FilterSpec(Constraints.empty(), List.of(), null,
                SortOption.RELEVANCE, List.of(), List.of());
    }

    public FilterSpec withConstraints(Constraints c) {
        return new FilterSpec(c, preferences, freeText, sort, appliedConcepts, unmapped);
    }

    public FilterSpec withFreeText(String text) {
        return new FilterSpec(constraints, preferences, text, sort, appliedConcepts, unmapped);
    }

    public FilterSpec withSort(SortOption s) {
        return new FilterSpec(constraints, preferences, freeText, s, appliedConcepts, unmapped);
    }

    public FilterSpec addPreferences(List<Preference> extra) {
        var merged = new ArrayList<>(preferences);
        merged.addAll(extra);
        return new FilterSpec(constraints, merged, freeText, sort, appliedConcepts, unmapped);
    }

    public FilterSpec addConcept(String concept) {
        var merged = new ArrayList<>(appliedConcepts);
        if (!merged.contains(concept)) merged.add(concept);
        return new FilterSpec(constraints, preferences, freeText, sort, merged, unmapped);
    }

    public FilterSpec addUnmapped(String term) {
        var merged = new ArrayList<>(unmapped);
        if (!merged.contains(term)) merged.add(term);
        return new FilterSpec(constraints, preferences, freeText, sort, appliedConcepts, merged);
    }

    /** True when nothing at all was understood — the caller should say so, not show the whole catalogue. */
    @JsonIgnore
    public boolean isBlank() {
        return constraints.equals(Constraints.empty())
                && preferences.isEmpty()
                && freeText == null;
    }
}
