package com.c24.vehiclesearch.search.query;

import com.c24.vehiclesearch.search.spec.Constraints;
import com.c24.vehiclesearch.search.spec.FilterSpec;
import com.c24.vehiclesearch.search.spec.NumRange;
import com.c24.vehiclesearch.search.spec.Preference;
import com.c24.vehiclesearch.search.spec.SortOption;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Compiles a {@link FilterSpec} into SQL.
 *
 * The whole architecture turns on this class being the only place a FilterSpec
 * becomes a query. Swapping the storage engine — to Elasticsearch, when the
 * catalogue outgrows a sequential scan — replaces this file and nothing else.
 *
 * Two rules hold everywhere below:
 *   1. Constraints go in WHERE. They remove rows.
 *   2. Preferences go in ORDER BY. They only reorder.
 *
 * No value from a parsed query is ever concatenated into SQL. Column names come
 * from enums, every literal is a bound parameter.
 */
@Component
public class QueryCompiler {

    private static final String SELECT_COLUMNS = """
            id, registration, make, model, variant, year, body_type, fuel_type, transmission,
            price_inr, emi_monthly, km_driven, owners, seats, engine_cc, mileage_kmpl,
            boot_litres, ncap_stars, city, hub, colour, status, listed_at, inspection_score,
            deal_score, features
            """;

    private final double dealWeight;
    private final double freshnessWeight;
    private final double textWeight;
    private final double trigramThreshold;

    public QueryCompiler(
            @Value("${search.ranking.deal-score-weight}") double dealWeight,
            @Value("${search.ranking.freshness-weight}") double freshnessWeight,
            @Value("${search.ranking.text-similarity-weight}") double textWeight,
            @Value("${search.ranking.trigram-threshold}") double trigramThreshold) {
        this.dealWeight = dealWeight;
        this.freshnessWeight = freshnessWeight;
        this.textWeight = textWeight;
        this.trigramThreshold = trigramThreshold;
    }

    public CompiledQuery compile(FilterSpec spec, int page, int size) {
        var params = new MapSqlParameterSource();
        String where = buildWhere(spec, params);
        String score = buildScore(spec, params);
        String orderBy = buildOrderBy(spec, score);

        params.addValue("limit", size);
        params.addValue("offset", (long) page * size);

        String rows = "SELECT " + SELECT_COLUMNS.strip() + ",\n       (" + score + ") AS score\n"
                + "FROM vehicles\nWHERE " + where + "\n"
                + "ORDER BY " + orderBy + "\n"
                + "LIMIT :limit OFFSET :offset";

        String count = "SELECT COUNT(*) FROM vehicles WHERE " + where;

        // Facet counts are computed over the already-filtered set, so they answer
        // "what is in these results" rather than "what would each alternative
        // return". Drill-down faceting needs one pass per dimension with that
        // dimension's own predicate removed; not worth the round trips here.
        String facets = """
                WITH base AS (SELECT body_type, fuel_type, transmission FROM vehicles WHERE %s)
                SELECT 'bodyType' AS dimension, body_type AS value, COUNT(*) AS count FROM base GROUP BY body_type
                UNION ALL
                SELECT 'fuelType', fuel_type, COUNT(*) FROM base GROUP BY fuel_type
                UNION ALL
                SELECT 'transmission', transmission, COUNT(*) FROM base GROUP BY transmission
                ORDER BY dimension, count DESC
                """.formatted(where);

        return new CompiledQuery(rows, count, facets, score, params);
    }

    // ------------------------------------------------------------------ WHERE

    private String buildWhere(FilterSpec spec, MapSqlParameterSource p) {
        Constraints c = spec.constraints();
        List<String> clauses = new ArrayList<>();

        // Non-negotiable guardrail. Injected server-side, after parsing, always.
        // Nothing a user types and nothing a model emits can widen this: showing
        // a sold car is the failure that actually costs the business money.
        clauses.add("status = 'AVAILABLE'");

        inList(clauses, p, "body_type",    "bodyTypes",     c.bodyTypes());
        inList(clauses, p, "fuel_type",    "fuelTypes",     c.fuelTypes());
        inList(clauses, p, "transmission", "transmissions", c.transmissions());
        inList(clauses, p, "city",         "cities",        c.cities());
        inList(clauses, p, "make",         "makes",         c.makes());

        range(clauses, p, "price_inr",   "price", c.priceInr());
        range(clauses, p, "emi_monthly", "emi",   c.emiMonthly());
        range(clauses, p, "km_driven",   "km",    c.kmDriven());
        range(clauses, p, "year",        "year",  c.year());
        range(clauses, p, "seats",       "seats", c.seats());

        if (c.maxOwners() != null) {
            clauses.add("owners <= :maxOwners");
            p.addValue("maxOwners", c.maxOwners());
        }
        if (c.minNcapStars() != null) {
            clauses.add("ncap_stars >= :minNcap");
            p.addValue("minNcap", c.minNcapStars());
        }

        if (spec.freeText() != null) {
            // word_similarity, not similarity: it scores the best-matching window
            // inside the text, so a two-word query still matches a long
            // "Hyundai Creta SX(O) Diesel" title. Plain similarity() normalises
            // over the whole string and would score that pair near zero.
            // ILIKE catches exact substrings that fall under the threshold.
            clauses.add("(word_similarity(:freeText, search_text) >= :trigramThreshold"
                    + " OR search_text ILIKE :freeTextLike)");
            p.addValue("freeText", spec.freeText());
            p.addValue("freeTextLike", "%" + spec.freeText() + "%");
            p.addValue("trigramThreshold", trigramThreshold);
        }

        return String.join("\n  AND ", clauses);
    }

    private void inList(List<String> clauses, MapSqlParameterSource p,
                        String column, String param, List<?> values) {
        if (values == null || values.isEmpty()) return;
        clauses.add(column + " IN (:" + param + ")");
        p.addValue(param, values.stream().map(Object::toString).toList());
    }

    private void range(List<String> clauses, MapSqlParameterSource p,
                       String column, String param, NumRange r) {
        if (r == null || r.isEmpty()) return;
        if (r.gte() != null) {
            clauses.add(column + " >= :" + param + "Min");
            p.addValue(param + "Min", r.gte());
        }
        if (r.lte() != null) {
            clauses.add(column + " <= :" + param + "Max");
            p.addValue(param + "Max", r.lte());
        }
    }

    // ------------------------------------------------------------------ SCORE

    /**
     * Every term is additive and independently readable, which is the point:
     * when someone asks why a car is third, the answer is a sum you can print.
     */
    private String buildScore(FilterSpec spec, MapSqlParameterSource p) {
        List<String> terms = new ArrayList<>();

        // Priced below its cohort — the "good deal" signal buyers respond to.
        terms.add(dealWeight + " * deal_score");

        // Gentle nudge for fresh stock; decays over roughly two months.
        terms.add(freshnessWeight + " * exp(-GREATEST(CURRENT_DATE - listed_at, 0) / 60.0)");

        if (spec.freeText() != null) {
            terms.add(textWeight + " * word_similarity(:freeText, search_text)");
        }

        int i = 0;
        for (Preference pref : spec.preferences()) {
            String value = "pref" + i++;
            // Binary step rather than a ramp: it mirrors the concept definition
            // one-to-one ("ncap_stars >= 4, boost 4.0"), so the YAML and the SQL
            // say the same thing and the weights stay explainable.
            terms.add("%s * (CASE WHEN %s %s :%s THEN 1 ELSE 0 END)"
                    .formatted(pref.boost(), pref.field().column(), pref.op().sql(), value));
            p.addValue(value, pref.value());
        }

        return String.join("\n        + ", terms);
    }

    private String buildOrderBy(FilterSpec spec, String score) {
        // An explicit sort is a direct instruction and beats relevance outright.
        if (spec.sort() != null && spec.sort() != SortOption.RELEVANCE) {
            return spec.sort().orderBy() + ", id ASC";
        }
        // id breaks ties so pagination is stable across pages.
        return "(" + score + ") DESC, price_inr ASC, id ASC";
    }
}
