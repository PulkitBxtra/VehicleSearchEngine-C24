package com.c24.vehiclesearch.eval;

import com.c24.vehiclesearch.search.spec.Constraints;
import com.c24.vehiclesearch.search.spec.FilterSpec;
import com.c24.vehiclesearch.search.spec.NumRange;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.StreamSupport;

/**
 * The shared grader.
 *
 * Both parsers are scored by this one class against one file. That is what makes
 * "the LLM is better than the rules" a measurement rather than an opinion — and
 * what stops the two paths from quietly drifting into different behaviours that
 * are each individually "tested".
 */
public final class GoldenSet {

    public record Case(String query, JsonNode expect) {
        @Override public String toString() { return query; }
    }

    private GoldenSet() {}

    public static List<Case> load() {
        var mapper = new ObjectMapper();
        try (InputStream in = GoldenSet.class.getResourceAsStream("/eval/queries.json")) {
            JsonNode root = mapper.readTree(in);
            return StreamSupport.stream(root.spliterator(), false)
                    .map(n -> new Case(n.get("query").asString(), n.get("expect")))
                    .toList();
        } catch (Exception e) {
            throw new IllegalStateException("Could not load /eval/queries.json", e);
        }
    }

    /** Empty means the spec matched every stated expectation. */
    public static List<String> mismatches(FilterSpec spec, JsonNode e) {
        var out = new ArrayList<String>();
        Constraints k = spec.constraints();

        expectSet(out, e, "bodyTypes", names(k.bodyTypes()));
        expectSet(out, e, "fuelTypes", names(k.fuelTypes()));
        expectSet(out, e, "transmissions", names(k.transmissions()));

        expectLong(out, e, "priceMax", upper(k.priceInr()));
        expectLong(out, e, "priceMin", lower(k.priceInr()));
        expectLong(out, e, "kmMax", upper(k.kmDriven()));
        expectLong(out, e, "kmMin", lower(k.kmDriven()));
        expectLong(out, e, "yearMin", lower(k.year()));
        expectLong(out, e, "yearMax", upper(k.year()));
        expectLong(out, e, "seatsMin", lower(k.seats()));
        expectLong(out, e, "emiMax", upper(k.emiMonthly()));
        expectLong(out, e, "maxOwners", k.maxOwners());

        if (e.has("concepts")) {
            var wanted = strings(e, "concepts");
            var missing = wanted.stream().filter(c -> !spec.appliedConcepts().contains(c)).toList();
            if (!missing.isEmpty()) out.add("concepts missing " + missing + ", got " + spec.appliedConcepts());
        }
        if (e.has("freeText")) {
            String want = e.get("freeText").asString();
            if (!want.equals(spec.freeText())) out.add("freeText: want " + want + ", got " + spec.freeText());
        }
        if (e.has("sort") && !e.get("sort").asString().equals(spec.sort().name())) {
            out.add("sort: want " + e.get("sort").asString() + ", got " + spec.sort());
        }
        if (e.path("unsatisfiable").asBoolean(false) && !k.isUnsatisfiable()) {
            out.add("expected contradictory bounds to be detected");
        }
        if (e.path("noConstraints").asBoolean(false)
                && (!k.bodyTypes().isEmpty() || !k.fuelTypes().isEmpty() || upper(k.priceInr()) != null)) {
            out.add("expected no hard constraints, got " + k);
        }
        return out;
    }

    private static void expectSet(List<String> out, JsonNode e, String field, List<String> actual) {
        if (!e.has(field)) return;
        var want = strings(e, field);
        if (!(actual.containsAll(want) && want.containsAll(actual))) {
            out.add(field + ": want " + want + ", got " + actual);
        }
    }

    private static void expectLong(List<String> out, JsonNode e, String field, Long actual) {
        if (!e.has(field)) return;
        long want = e.get(field).asLong();
        if (actual == null || actual != want) out.add(field + ": want " + want + ", got " + actual);
    }

    private static List<String> names(List<?> values) {
        return values.stream().map(Object::toString).toList();
    }

    private static List<String> strings(JsonNode node, String field) {
        var out = new ArrayList<String>();
        node.get(field).forEach(n -> out.add(n.asString()));
        return out;
    }

    private static Long upper(NumRange r) { return r == null ? null : r.lte(); }
    private static Long lower(NumRange r) { return r == null ? null : r.gte(); }
}
