package com.c24.vehiclesearch.search.llm;


import java.util.List;

/**
 * Exactly what the model is allowed to emit.
 *
 * Flatter than {@link com.c24.vehiclesearch.search.spec.FilterSpec} on purpose:
 * nested objects and unions are where structured-output compliance degrades, and
 * every field here is a scalar or an array of strings drawn from a closed enum.
 *
 * Note what is absent — the model cannot emit preferences, boosts, column names
 * or sort expressions. It picks concept *keys*; concepts.yml decides what they
 * mean. That keeps ranking semantics in a reviewed file rather than in whatever
 * the model felt like weighting today.
 */
public record LlmFilterDraft(
        List<String> concepts,
        List<String> makes,
        List<String> bodyTypes,
        List<String> fuelTypes,
        List<String> transmissions,
        List<String> cities,
        Long priceMin,
        Long priceMax,
        Long emiMax,
        Long kmMin,
        Long kmMax,
        Long yearMin,
        Long yearMax,
        Long seatsMin,
        Long maxOwners,
        Long minNcapStars,
        String sort,
        String freeText,
        List<String> unmapped) {

    public LlmFilterDraft {
        concepts      = safe(concepts);
        makes         = safe(makes);
        bodyTypes     = safe(bodyTypes);
        fuelTypes     = safe(fuelTypes);
        transmissions = safe(transmissions);
        cities        = safe(cities);
        unmapped      = safe(unmapped);
    }

    private static List<String> safe(List<String> in) {
        return in == null ? List.of() : in.stream().filter(s -> s != null && !s.isBlank()).toList();
    }
}
