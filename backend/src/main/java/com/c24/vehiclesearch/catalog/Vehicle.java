package com.c24.vehiclesearch.catalog;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** A catalogue row as the API returns it. {@code score} is null outside search results. */
public record Vehicle(
        long id,
        String registration,
        String make,
        String model,
        String variant,
        int year,
        BodyType bodyType,
        FuelType fuelType,
        Transmission transmission,
        long priceInr,
        int emiMonthly,
        int kmDriven,
        int owners,
        int seats,
        int engineCc,
        BigDecimal mileageKmpl,
        int bootLitres,
        Integer ncapStars,
        String city,
        String hub,
        String colour,
        VehicleStatus status,
        LocalDate listedAt,
        BigDecimal inspectionScore,
        BigDecimal dealScore,
        List<String> features,
        Double score) {}
