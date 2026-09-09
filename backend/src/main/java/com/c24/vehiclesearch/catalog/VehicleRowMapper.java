package com.c24.vehiclesearch.catalog;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

public class VehicleRowMapper implements RowMapper<Vehicle> {

    private static final ObjectMapper JSON = new ObjectMapper();
    private final boolean withScore;

    public VehicleRowMapper(boolean withScore) {
        this.withScore = withScore;
    }

    @Override
    public Vehicle mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new Vehicle(
                rs.getLong("id"),
                rs.getString("registration"),
                rs.getString("make"),
                rs.getString("model"),
                rs.getString("variant"),
                rs.getInt("year"),
                BodyType.valueOf(rs.getString("body_type")),
                FuelType.valueOf(rs.getString("fuel_type")),
                Transmission.valueOf(rs.getString("transmission")),
                rs.getLong("price_inr"),
                rs.getInt("emi_monthly"),
                rs.getInt("km_driven"),
                rs.getInt("owners"),
                rs.getInt("seats"),
                rs.getInt("engine_cc"),
                rs.getBigDecimal("mileage_kmpl"),
                rs.getInt("boot_litres"),
                (Integer) rs.getObject("ncap_stars"),
                rs.getString("city"),
                rs.getString("hub"),
                rs.getString("colour"),
                VehicleStatus.valueOf(rs.getString("status")),
                rs.getDate("listed_at").toLocalDate(),
                rs.getBigDecimal("inspection_score"),
                rs.getBigDecimal("deal_score"),
                features(rs.getString("features")),
                withScore ? rs.getDouble("score") : null);
    }

    private static List<String> features(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return JSON.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }
}
