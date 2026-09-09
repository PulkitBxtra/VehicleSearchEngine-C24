package com.c24.vehiclesearch.search;

import com.c24.vehiclesearch.search.concept.ConceptDictionary;
import com.c24.vehiclesearch.search.spec.Constraints;
import com.c24.vehiclesearch.search.spec.FilterSpec;
import com.c24.vehiclesearch.search.spec.NumRange;
import com.c24.vehiclesearch.search.spec.SortOption;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.LongFunction;

/** Turns a FilterSpec into human-readable chips for the response. */
@Component
public class Interpreter {

    private final ConceptDictionary concepts;

    public Interpreter(ConceptDictionary concepts) {
        this.concepts = concepts;
    }

    public Interpretation describe(FilterSpec spec) {
        List<Interpretation.Chip> chips = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        Constraints c = spec.constraints();

        list(chips, "constraints.makes",         "Make",         c.makes());
        list(chips, "constraints.bodyTypes",     "Body",         c.bodyTypes());
        list(chips, "constraints.fuelTypes",     "Fuel",         c.fuelTypes());
        list(chips, "constraints.transmissions", "Transmission", c.transmissions());
        // Show what was actually searched. "bangalore" matches Bengaluru stock,
        // and saying so is more useful than echoing the alias back.
        list(chips, "constraints.cities", "City",
                c.cities().stream()
                        .map(com.c24.vehiclesearch.search.query.Cities::canonical)
                        .toList());

        range(chips, "constraints.priceInr",   "Price", c.priceInr(), Money::format);
        range(chips, "constraints.emiMonthly", "EMI",   c.emiMonthly(), v -> Money.format(v) + "/mo");
        range(chips, "constraints.kmDriven",   "Driven", c.kmDriven(), v -> String.format("%,d km", v));
        range(chips, "constraints.year",       "Year",  c.year(), String::valueOf);
        range(chips, "constraints.seats",      "Seats", c.seats(), String::valueOf);

        if (c.maxOwners() != null) {
            chips.add(Interpretation.Chip.constraint("constraints.maxOwners",
                    c.maxOwners() == 1 ? "First owner only" : "At most " + c.maxOwners() + " owners"));
        }
        if (c.minNcapStars() != null) {
            chips.add(Interpretation.Chip.constraint("constraints.minNcapStars",
                    c.minNcapStars() + "+ NCAP stars"));
        }
        if (spec.freeText() != null) {
            chips.add(Interpretation.Chip.constraint("freeText", "Matching \"" + spec.freeText() + "\""));
        }

        // Preferences are grouped by the concept that produced them, so the UI
        // shows one "Family car" chip rather than three opaque numeric rules.
        spec.appliedConcepts().stream()
                .filter(k -> concepts.get(k).map(d -> !d.preferences().isEmpty()).orElse(false))
                .forEach(k -> chips.add(Interpretation.Chip.preference(
                        "preferences", concepts.get(k).map(d -> d.label()).orElse(k) + " (ranked higher)", k)));

        if (spec.sort() != SortOption.RELEVANCE) {
            notes.add("Sorted by " + spec.sort().name().toLowerCase().replace('_', ' ')
                    + " — relevance ranking is off.");
        }
        if (!spec.unmapped().isEmpty()) {
            notes.add("Could not interpret: " + String.join(", ", spec.unmapped())
                    + ". These terms did not narrow the search.");
        }
        if (c.isUnsatisfiable()) {
            notes.add("These filters contradict each other, so nothing can match.");
        }

        return new Interpretation(List.copyOf(chips), List.copyOf(notes));
    }

    private void list(List<Interpretation.Chip> chips, String field, String label, List<?> values) {
        if (values == null || values.isEmpty()) return;
        String rendered = values.stream()
                .map(v -> v.toString().replace('_', ' ').toLowerCase())
                .reduce((a, b) -> a + " / " + b).orElse("");
        chips.add(Interpretation.Chip.constraint(field, label + ": " + rendered));
    }

    private void range(List<Interpretation.Chip> chips, String field, String label,
                       NumRange r, LongFunction<String> fmt) {
        if (r == null || r.isEmpty()) return;
        String text;
        if (r.gte() != null && r.lte() != null) {
            text = label + ": " + fmt.apply(r.gte()) + " – " + fmt.apply(r.lte());
        } else if (r.lte() != null) {
            text = label + " under " + fmt.apply(r.lte());
        } else {
            // Bounds are inclusive; "over 2020" would exclude 2020 itself.
            text = label + " " + fmt.apply(r.gte()) + "+";
        }
        chips.add(Interpretation.Chip.constraint(field, text));
    }
}
