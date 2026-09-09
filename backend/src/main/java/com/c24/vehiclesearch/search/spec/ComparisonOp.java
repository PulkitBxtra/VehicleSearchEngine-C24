package com.c24.vehiclesearch.search.spec;

public enum ComparisonOp {
    GTE(">="), LTE("<="), EQ("=");

    private final String sql;
    ComparisonOp(String sql) { this.sql = sql; }
    public String sql() { return sql; }
}
