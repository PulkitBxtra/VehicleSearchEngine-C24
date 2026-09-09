package com.c24.vehiclesearch.api;

import java.util.List;
import java.util.Map;

/**
 * Everything a client needs to build filter controls and to know what
 * vocabulary the parser understands.
 *
 * The frontend builds its sort control from {@code sorts} rather than
 * hardcoding the enum, so a sort option added here appears in the UI with no
 * frontend change. {@code concepts} is the same vocabulary handed to the model
 * in {@link com.c24.vehiclesearch.search.llm.GeminiContract}, which is what
 * keeps the prompt and the dictionary from drifting apart.
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
