package com.c24.vehiclesearch.api;

import com.c24.vehiclesearch.catalog.Vehicle;
import com.c24.vehiclesearch.search.Interpretation;
import com.c24.vehiclesearch.search.spec.FilterSpec;

import java.util.List;
import java.util.Map;

/**
 * @param filters   the spec that actually ran — echoed so the client can edit
 *                  one field and resubmit without re-parsing anything
 * @param parser    RULES | EXPLICIT | LLM | NONE — which path produced the spec
 * @param scoreSql  the ranking expression that ordered these rows
 */
public record SearchResponse(
        Interpretation interpretation,
        FilterSpec filters,
        List<Vehicle> results,
        long totalElements,
        int page,
        int size,
        Map<String, Map<String, Long>> facets,
        String parser,
        long tookMs,
        List<String> warnings,
        String scoreSql) {}
