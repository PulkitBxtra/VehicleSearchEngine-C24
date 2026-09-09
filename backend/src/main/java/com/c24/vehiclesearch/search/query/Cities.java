package com.c24.vehiclesearch.search.query;

import java.util.Map;

/**
 * Canonicalises Indian city names before they reach a WHERE clause.
 *
 * Two independent ways a city filter silently returns nothing:
 *
 *   1. Case. The catalogue stores "Bengaluru"; a parser emits "bengaluru".
 *   2. Renaming. Half the country still says Bangalore, Bombay, Calcutta and
 *      Gurgaon, and every one of those is a different string from what the
 *      inventory is stored under.
 *
 * Both produce an empty result set that reads as "no stock in your city"
 * rather than "we did not recognise your city", which is the worse of the two
 * failures — the user leaves believing the inventory is thin.
 *
 * This lives at the compile step rather than in either parser so that every
 * path is covered, including a FilterSpec submitted directly by the UI.
 */
public final class Cities {

    private static final Map<String, String> ALIASES = Map.ofEntries(
            Map.entry("bangalore", "bengaluru"),
            Map.entry("bombay", "mumbai"),
            Map.entry("calcutta", "kolkata"),
            Map.entry("madras", "chennai"),
            Map.entry("gurgaon", "gurugram"),
            Map.entry("poona", "pune"),
            Map.entry("mysore", "mysuru"),
            Map.entry("baroda", "vadodara"),
            Map.entry("cochin", "kochi"),
            Map.entry("trivandrum", "thiruvananthapuram"),
            Map.entry("pondicherry", "puducherry"),
            Map.entry("new delhi", "delhi"),
            Map.entry("ncr", "delhi"),
            Map.entry("blr", "bengaluru"),
            Map.entry("bom", "mumbai"),
            Map.entry("hyd", "hyderabad"));

    private Cities() {}

    /** Lower-cased and de-aliased, to be compared against LOWER(city). */
    public static String canonical(String raw) {
        if (raw == null) return null;
        String key = raw.trim().toLowerCase();
        return ALIASES.getOrDefault(key, key);
    }
}
