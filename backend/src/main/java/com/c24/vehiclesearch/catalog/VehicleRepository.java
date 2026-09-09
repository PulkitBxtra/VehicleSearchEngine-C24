package com.c24.vehiclesearch.catalog;

import com.c24.vehiclesearch.search.query.CompiledQuery;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class VehicleRepository {

    private static final RowMapperHolder SCORED = new RowMapperHolder(true);
    private static final RowMapperHolder PLAIN  = new RowMapperHolder(false);

    private record RowMapperHolder(boolean scored) {
        VehicleRowMapper mapper() { return new VehicleRowMapper(scored); }
    }

    private final NamedParameterJdbcTemplate jdbc;

    public VehicleRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Vehicle> search(CompiledQuery q) {
        return jdbc.query(q.rowsSql(), q.params(), SCORED.mapper());
    }

    public long count(CompiledQuery q) {
        Long n = jdbc.queryForObject(q.countSql(), q.params(), Long.class);
        return n == null ? 0 : n;
    }

    /** dimension -> (value -> count), for the filter chips in the UI. */
    public Map<String, Map<String, Long>> facets(CompiledQuery q) {
        Map<String, Map<String, Long>> out = new LinkedHashMap<>();
        jdbc.query(q.facetSql(), q.params(), rs -> {
            out.computeIfAbsent(rs.getString("dimension"), k -> new LinkedHashMap<>())
               .put(rs.getString("value"), rs.getLong("count"));
        });
        return out;
    }

    public Optional<Vehicle> findById(long id) {
        var rows = jdbc.query(
                "SELECT * FROM vehicles WHERE id = :id",
                Map.of("id", id),
                PLAIN.mapper());
        return rows.stream().findFirst();
    }

    public long countAll() {
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM vehicles", Map.of(), Long.class);
        return n == null ? 0 : n;
    }

    /** Actual min/max in the catalogue, so the UI's sliders match the inventory. */
    public Map<String, com.c24.vehiclesearch.api.SchemaResponse.Range> ranges() {
        return jdbc.query("""
                SELECT MIN(price_inr) pmin, MAX(price_inr) pmax,
                       MIN(km_driven) kmin, MAX(km_driven) kmax,
                       MIN(year)      ymin, MAX(year)      ymax,
                       MIN(emi_monthly) emin, MAX(emi_monthly) emax
                FROM vehicles WHERE status = 'AVAILABLE'
                """, Map.of(), rs -> {
            var out = new LinkedHashMap<String, com.c24.vehiclesearch.api.SchemaResponse.Range>();
            if (rs.next()) {
                out.put("priceInr",   new com.c24.vehiclesearch.api.SchemaResponse.Range(rs.getLong("pmin"), rs.getLong("pmax")));
                out.put("kmDriven",   new com.c24.vehiclesearch.api.SchemaResponse.Range(rs.getLong("kmin"), rs.getLong("kmax")));
                out.put("year",       new com.c24.vehiclesearch.api.SchemaResponse.Range(rs.getLong("ymin"), rs.getLong("ymax")));
                out.put("emiMonthly", new com.c24.vehiclesearch.api.SchemaResponse.Range(rs.getLong("emin"), rs.getLong("emax")));
            }
            return out;
        });
    }

    /** Column name is caller-supplied but never user-supplied; whitelisted here regardless. */
    public List<String> distinct(String column) {
        if (!List.of("city", "make", "hub", "colour").contains(column)) {
            throw new IllegalArgumentException("not a distinct-able column: " + column);
        }
        return jdbc.queryForList(
                "SELECT DISTINCT " + column + " FROM vehicles WHERE status = 'AVAILABLE' ORDER BY 1",
                Map.of(), String.class);
    }
}
