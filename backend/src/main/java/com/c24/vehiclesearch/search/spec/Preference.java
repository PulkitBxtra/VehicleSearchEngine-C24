package com.c24.vehiclesearch.search.spec;

import jakarta.validation.constraints.NotNull;

/**
 * A soft signal. Contributes to the score; never removes a row.
 *
 * "Family cars with high safety ratings" is a ranking request, not a filter:
 * a five-seat five-star car should still appear, just lower. Expressing that
 * as a WHERE clause is the single most common way to get this problem wrong.
 *
 * @param source which concept or rule produced this, for explainability
 */
public record Preference(
        @NotNull PreferenceField field,
        @NotNull ComparisonOp op,
        @NotNull Double value,
        double boost,
        String source) {

    public Preference {
        if (boost <= 0) throw new IllegalArgumentException("boost must be positive");
    }
}
