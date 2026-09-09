package com.c24.vehiclesearch.api;

import java.util.List;
import java.util.Map;

/**
 * Everything a client needs to build the facet UI and to know what vocabulary
 * the parser understands. The frontend renders its filter controls from this
 * rather than hardcoding enum values, so adding a fuel type is a backend-only
 * change. It is also the vocabulary handed to the LLM in the Day 2 prompt.
 */
public record SchemaResponse(
        Map<String, List<String>> enums,
        List<ConceptInfo> concepts,
        Map<String, Range> ranges,
        List<String> cities,
        List<String> makes,
        List<String> sorts) {

    /** @param kind CONSTRAINT, PREFERENCE or BOTH — whether matching this term narrows or reorders */
    public record ConceptInfo(String key, String label, String kind, List<String> terms) {}

    public record Range(long min, long max) {}
}
