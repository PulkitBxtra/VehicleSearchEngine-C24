package com.c24.vehiclesearch.search.llm;

import com.c24.vehiclesearch.catalog.BodyType;
import com.c24.vehiclesearch.catalog.FuelType;
import com.c24.vehiclesearch.catalog.Transmission;
import com.c24.vehiclesearch.search.concept.ConceptDictionary;
import com.c24.vehiclesearch.search.spec.Constraints;
import com.c24.vehiclesearch.search.spec.SortOption;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * Builds the response schema and system instruction handed to Gemini.
 *
 * Both are derived from the live enums and concepts.yml rather than written out
 * by hand, so adding a fuel type or a concept updates what the model is allowed
 * to say without anyone remembering to edit a prompt string. A prompt that
 * drifts from the schema is the usual source of "the model keeps inventing
 * fields".
 */
@Component
public class GeminiContract {

    private static final JsonNodeFactory NODES = JsonNodeFactory.instance;

    private final ConceptDictionary concepts;

    public GeminiContract(ConceptDictionary concepts) {
        this.concepts = concepts;
    }

    /**
     * OpenAPI-subset schema. Kept flat and free of $ref, oneOf and
     * additionalProperties, which are the parts of JSON Schema that structured
     * output supports least reliably.
     */
    public ObjectNode responseSchema() {
        ObjectNode props = NODES.objectNode();

        props.set("concepts",      enumArray(concepts.all().keySet().stream().sorted().toList()));
        props.set("makes",         stringArray());
        props.set("bodyTypes",     enumArray(names(BodyType.values())));
        props.set("fuelTypes",     enumArray(names(FuelType.values())));
        props.set("transmissions", enumArray(names(Transmission.values())));
        props.set("cities",        stringArray());

        for (String numeric : List.of("priceMin", "priceMax", "emiMax", "kmMin", "kmMax",
                                      "yearMin", "yearMax", "seatsMin", "maxOwners", "minNcapStars")) {
            props.set(numeric, nullableInteger());
        }

        ObjectNode sort = NODES.objectNode().put("type", "STRING");
        sort.set("enum", array(names(SortOption.values())));
        props.set("sort", sort);

        props.set("freeText", NODES.objectNode().put("type", "STRING").put("nullable", true));
        props.set("unmapped", stringArray());

        ObjectNode schema = NODES.objectNode().put("type", "OBJECT");
        schema.set("properties", props);
        // Requiring every key removes the "did it omit this or mean null?"
        // ambiguity; nulls are explicit.
        schema.set("required", array(iterate(props)));
        schema.set("propertyOrdering", array(iterate(props)));
        return schema;
    }

    public String systemInstruction() {
        var sb = new StringBuilder();
        sb.append("""
                You convert Indian used-car search queries into a filter object. \
                Reply with the JSON object only.

                Rules:
                1. Hard limits the user states go in the numeric fields: price, EMI, \
                kilometres, year, seats, owners, safety stars.
                2. Vague, subjective or lifestyle wording goes in `concepts`, using ONLY \
                the keys listed below. Do not translate a concept into numeric fields \
                yourself — the server expands concepts and knows what each one means.
                3. Amounts use Indian magnitudes: L or lakh = 100000, cr or crore = \
                10000000, k = 1000. "under 15L" is priceMax 1500000. "80k km" is \
                kmMax 80000.
                4. A number followed by "per month", "monthly" or "EMI" is emiMax, \
                not priceMax.
                5. Put model or brand words in `freeText` exactly as typed, including \
                misspellings ("hundai creta"). Only fill `makes` when a manufacturer is \
                named unambiguously.
                6. Anything you cannot map to a field or a concept goes in `unmapped` so \
                the user can be told it was ignored. Never guess.
                7. If the query states no filters at all, return empty arrays and nulls.
                8. All bounds are inclusive. "before 2018" is yearMax 2018, not 2017; \
                "under 15L" is priceMax 1500000, which still matches a car at exactly \
                that price.
                9. Sort values mean specific things. NEWEST_LISTED is most recently \
                added to the catalogue and is what "newest", "latest" and "just \
                arrived" mean. YEAR_DESC is newest manufacturing year, and only \
                applies when the user says "model year", "make year" or similar.
                10. `freeText` is for make and model names only. Wording you cannot \
                place belongs in `unmapped`, never in freeText — searching model \
                names for it would return nothing and hide the real reason.

                Concept vocabulary (key — meaning — effect):
                """);

        concepts.all().forEach((key, def) -> {
            boolean narrows = !def.constraints().equals(Constraints.empty());
            boolean ranks = !def.preferences().isEmpty();
            String effect = narrows && ranks ? "filters and ranks"
                    : narrows ? "filters" : "ranks only, removes nothing";
            sb.append("  ").append(key).append(" — ").append(def.label())
              .append(" — ").append(effect).append('\n');
        });

        sb.append("""

                Worked examples:
                  "Show SUVs under Rs 15L"
                    concepts ["suv"], priceMax 1500000
                  "Diesel automatic cars below 80k km"
                    concepts ["diesel","automatic"], kmMax 80000
                  "Family cars with high safety ratings"
                    concepts ["family","high_safety"], everything else null
                  "7 seater under 12 lakh first owner"
                    concepts ["seven_seater","first_owner"], seatsMin 7, priceMax 1200000
                  "something sporty with a sunroof"
                    concepts ["powerful"], unmapped ["sunroof"]
                """);
        return sb.toString();
    }

    // ------------------------------------------------------------------ helpers

    private static ObjectNode nullableInteger() {
        return NODES.objectNode().put("type", "INTEGER").put("nullable", true);
    }

    private static ObjectNode stringArray() {
        ObjectNode node = NODES.objectNode().put("type", "ARRAY");
        node.set("items", NODES.objectNode().put("type", "STRING"));
        return node;
    }

    private static ObjectNode enumArray(List<String> values) {
        ObjectNode items = NODES.objectNode().put("type", "STRING");
        items.set("enum", array(values));
        ObjectNode node = NODES.objectNode().put("type", "ARRAY");
        node.set("items", items);
        return node;
    }

    private static ArrayNode array(List<String> values) {
        ArrayNode node = NODES.arrayNode();
        values.forEach(node::add);
        return node;
    }

    /** Jackson 3 renamed fieldNames() to propertyNames() and returns a collection. */
    private static List<String> iterate(ObjectNode props) {
        return List.copyOf(props.propertyNames());
    }

    private static <E extends Enum<E>> List<String> names(E[] values) {
        return Arrays.stream(values).map(Enum::name).toList();
    }

    /** Exposed so tests can assert the schema stays flat and closed. */
    public JsonNode schemaForTest() { return responseSchema(); }
}
