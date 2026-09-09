package com.c24.vehiclesearch.search.spec;

/**
 * Whitelist of columns a preference may score on.
 *
 * The enum is the injection boundary: the LLM emits a name, Jackson either
 * resolves it to a constant here or the request is rejected. No string from a
 * model ever reaches the SQL builder, so the ranking expression cannot be
 * steered into arbitrary SQL.
 */
public enum PreferenceField {
    SEATS("seats"),
    NCAP_STARS("ncap_stars"),
    BOOT_LITRES("boot_litres"),
    MILEAGE_KMPL("mileage_kmpl"),
    PRICE_INR("price_inr"),
    KM_DRIVEN("km_driven"),
    YEAR("year"),
    ENGINE_CC("engine_cc"),
    INSPECTION_SCORE("inspection_score"),
    OWNERS("owners");

    private final String column;
    PreferenceField(String column) { this.column = column; }
    public String column() { return column; }
}
