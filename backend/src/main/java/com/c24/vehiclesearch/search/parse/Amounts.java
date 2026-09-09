package com.c24.vehiclesearch.search.parse;

/**
 * Indian numeric shorthand. "15L" is fifteen lakh (1,500,000), not fifteen;
 * "80k" is eighty thousand; "15,00,000" groups in the lakh system, not thousands.
 *
 * Getting this wrong is a silent two-orders-of-magnitude error: a search for
 * "under 15L" that parses to 15 returns nothing, and one that parses to 1500
 * returns nothing either, so the bug reads as "no inventory" rather than as a
 * parse failure.
 */
public final class Amounts {

    private Amounts() {}

    /**
     * Number, with optional Indian magnitude suffix. Handles 1,50,000 and 150000.
     *
     * The trailing lookahead is load-bearing. Without it, "below 50000 km" lets
     * the suffix alternation claim the "k" of "km" and leaves a stray "m", so the
     * query silently becomes a price bound of five crore instead of a distance
     * bound of fifty thousand. Requiring a non-letter after the suffix makes the
     * engine backtrack to no-suffix and hand "km" to the unit group instead.
     */
    public static final String NUM = "((?:\\d{1,3}(?:,\\d{2,3})+|\\d+)(?:\\.\\d+)?)\\s*"
            + "(lakhs?|lacs?|crores?|cr|l|k|thousand)?(?![a-z])";

    public static long parse(String digits, String unit) {
        double base = Double.parseDouble(digits.replace(",", ""));
        if (unit == null || unit.isBlank()) return Math.round(base);

        return switch (unit.toLowerCase()) {
            case "l", "lakh", "lakhs", "lac", "lacs"  -> Math.round(base * 100_000);
            case "cr", "crore", "crores"              -> Math.round(base * 10_000_000);
            case "k", "thousand"                      -> Math.round(base * 1_000);
            default -> Math.round(base);
        };
    }
}
