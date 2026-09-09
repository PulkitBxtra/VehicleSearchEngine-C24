package com.c24.vehiclesearch.search.spec;

/**
 * An explicit user sort. When present it overrides relevance ranking entirely —
 * someone who asked for "cheapest first" does not want a deal-score thumb on
 * the scale.
 */
public enum SortOption {
    RELEVANCE(null),
    PRICE_ASC("price_inr ASC"),
    PRICE_DESC("price_inr DESC"),
    KM_ASC("km_driven ASC"),
    YEAR_DESC("year DESC"),
    NEWEST_LISTED("listed_at DESC");

    private final String orderBy;
    SortOption(String orderBy) { this.orderBy = orderBy; }
    public String orderBy() { return orderBy; }
}
