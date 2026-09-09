package com.c24.vehiclesearch.api;

import com.c24.vehiclesearch.search.spec.FilterSpec;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * Two front doors, one contract.
 *
 * Send {@code query} for natural language, or {@code filters} to run a
 * FilterSpec directly — which is what the UI does when a user edits a chip.
 * When both are present, {@code filters} wins: an explicit edit is a stronger
 * signal than the sentence it came from, and it costs no LLM call.
 */
public record SearchRequest(
        @Size(max = 500) String query,
        @Valid FilterSpec filters,
        @Min(0) Integer page,
        @Min(1) @Max(100) Integer size) {

    public int pageOrDefault() { return page == null ? 0 : page; }
    public int sizeOrDefault() { return size == null ? 20 : size; }
}
