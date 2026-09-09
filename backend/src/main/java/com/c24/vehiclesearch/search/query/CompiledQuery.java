package com.c24.vehiclesearch.search.query;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

/**
 * @param rowsSql   the paged result query
 * @param countSql  total matching rows, for pagination
 * @param facetSql  counts per categorical dimension, one round trip
 * @param scoreSql  the ranking expression, surfaced in the API response so a
 *                  reviewer (or a user) can see exactly why a car ranked where
 *                  it did rather than trusting an opaque relevance number
 */
public record CompiledQuery(
        String rowsSql,
        String countSql,
        String facetSql,
        String scoreSql,
        MapSqlParameterSource params) {}
