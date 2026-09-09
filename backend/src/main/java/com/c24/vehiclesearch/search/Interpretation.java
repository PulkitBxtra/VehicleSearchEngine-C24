package com.c24.vehiclesearch.search;

import java.util.List;

/**
 * What the system understood, in the user's language.
 *
 * Returning this alongside results is the difference between a search box that
 * feels magic-but-unpredictable and one a user can correct. Each chip maps back
 * to exactly one field of the FilterSpec, so removing a chip in the UI is a
 * mechanical edit that re-queries with no LLM call.
 */
public record Interpretation(List<Chip> chips, List<String> notes) {

    /**
     * @param kind  CONSTRAINT chips narrow results; PREFERENCE chips only reorder.
     *              The UI renders them differently so "high safety" does not look
     *              like it excluded anything.
     * @param field the FilterSpec path this chip came from, e.g. "constraints.priceInr"
     */
    public record Chip(String kind, String field, String label, String source) {

        public static Chip constraint(String field, String label) {
            return new Chip("CONSTRAINT", field, label, null);
        }

        public static Chip preference(String field, String label, String source) {
            return new Chip("PREFERENCE", field, label, source);
        }
    }
}
