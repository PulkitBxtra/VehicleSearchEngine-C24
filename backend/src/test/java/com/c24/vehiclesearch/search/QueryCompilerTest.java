package com.c24.vehiclesearch.search;

import com.c24.vehiclesearch.catalog.BodyType;
import com.c24.vehiclesearch.search.query.QueryCompiler;
import com.c24.vehiclesearch.search.spec.ComparisonOp;
import com.c24.vehiclesearch.search.spec.Constraints;
import com.c24.vehiclesearch.search.spec.FilterSpec;
import com.c24.vehiclesearch.search.spec.NumRange;
import com.c24.vehiclesearch.search.spec.Preference;
import com.c24.vehiclesearch.search.spec.PreferenceField;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("unchecked")
class QueryCompilerTest {

    private final QueryCompiler compiler = new QueryCompiler(1.5, 0.5, 4.0, 0.25);

    @Test
    @DisplayName("the availability guardrail is present on every query")
    void alwaysFiltersToAvailable() {
        var q = compiler.compile(FilterSpec.empty(), 0, 20);
        assertThat(q.rowsSql()).contains("status = 'AVAILABLE'");
        assertThat(q.countSql()).contains("status = 'AVAILABLE'");
        assertThat(q.facetSql()).contains("status = 'AVAILABLE'");
    }

    @Test
    @DisplayName("constraints filter, preferences only reorder")
    void preferencesNeverAppearInTheWhereClause() {
        var spec = FilterSpec.empty()
                .withConstraints(Constraints.empty().merge(new Constraints(
                        null, List.of(BodyType.SUV), null, null, null,
                        NumRange.atMost(1_500_000), null, null, null, null, null, null)))
                .addPreferences(List.of(
                        new Preference(PreferenceField.NCAP_STARS, ComparisonOp.GTE, 4.0, 4.0, "high_safety")));

        var q = compiler.compile(spec, 0, 20);
        String where = q.rowsSql().substring(q.rowsSql().indexOf("WHERE"), q.rowsSql().indexOf("ORDER BY"));

        assertThat(where).contains("body_type IN");
        assertThat(where).contains("price_inr <= :priceMax");
        // The whole point: a safety preference must not remove a three-star car.
        assertThat(where).doesNotContain("ncap_stars");
        assertThat(q.scoreSql()).contains("ncap_stars >=");
    }

    @Test
    @DisplayName("no user-supplied value is ever inlined into SQL")
    void bindsEveryLiteral() {
        var spec = FilterSpec.empty().withFreeText("creta'; DROP TABLE vehicles;--");
        var q = compiler.compile(spec, 0, 20);

        assertThat(q.rowsSql()).doesNotContain("DROP TABLE");
        assertThat(q.rowsSql()).contains(":freeText");
        assertThat(q.params().getValue("freeText")).isEqualTo("creta'; DROP TABLE vehicles;--");
    }

    @Test
    @DisplayName("city names are matched case-insensitively and de-aliased")
    void citiesAreCanonicalised() {
        var spec = FilterSpec.empty().withConstraints(new Constraints(
                null, null, null, null, List.of("Bangalore", "BOMBAY"),
                null, null, null, null, null, null, null));

        var q = compiler.compile(spec, 0, 20);

        // The catalogue stores "Bengaluru" and "Mumbai"; a literal IN on what the
        // user typed returns nothing and looks like empty inventory.
        assertThat(q.rowsSql()).contains("LOWER(city) IN (:cities)");
        assertThat((List<String>) q.params().getValue("cities"))
                .containsExactly("bengaluru", "mumbai");
    }

    @Test
    @DisplayName("an explicit sort replaces relevance ranking")
    void explicitSortWins() {
        var spec = FilterSpec.empty().withSort(com.c24.vehiclesearch.search.spec.SortOption.PRICE_ASC);
        var q = compiler.compile(spec, 0, 20);
        assertThat(q.rowsSql()).contains("ORDER BY price_inr ASC");
        assertThat(q.rowsSql()).doesNotContain("deal_score) DESC");
    }
}
