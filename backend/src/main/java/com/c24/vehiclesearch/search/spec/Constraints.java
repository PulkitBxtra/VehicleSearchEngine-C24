package com.c24.vehiclesearch.search.spec;

import com.c24.vehiclesearch.catalog.BodyType;
import com.c24.vehiclesearch.catalog.FuelType;
import com.c24.vehiclesearch.catalog.Transmission;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Hard constraints. These become WHERE clauses: a row that fails any of them
 * is not a result at any score. "Under Rs 15L" is absolute; the user does not
 * want a 40L car no matter how well it matches otherwise.
 */
public record Constraints(
        List<String> makes,
        List<BodyType> bodyTypes,
        List<FuelType> fuelTypes,
        List<Transmission> transmissions,
        List<String> cities,
        NumRange priceInr,
        NumRange emiMonthly,
        NumRange kmDriven,
        NumRange year,
        NumRange seats,
        @Min(1) @Max(6) Long maxOwners,
        @Min(0) @Max(5) Long minNcapStars) {

    public Constraints {
        makes          = nullSafe(makes);
        bodyTypes      = nullSafe(bodyTypes);
        fuelTypes      = nullSafe(fuelTypes);
        transmissions  = nullSafe(transmissions);
        cities         = nullSafe(cities);
    }

    public static Constraints empty() {
        return new Constraints(List.of(), List.of(), List.of(), List.of(), List.of(),
                null, null, null, null, null, null, null);
    }

    /**
     * Union the categorical sets, intersect the ranges.
     *
     * Union is right for categories because two mentions of a body type are
     * alternatives ("SUV or MPV"), while two mentions of a range are successive
     * narrowings ("under 15L" then "over 8L").
     */
    public Constraints merge(Constraints o) {
        if (o == null) return this;
        return new Constraints(
                union(makes, o.makes),
                union(bodyTypes, o.bodyTypes),
                union(fuelTypes, o.fuelTypes),
                union(transmissions, o.transmissions),
                union(cities, o.cities),
                merge(priceInr, o.priceInr),
                merge(emiMonthly, o.emiMonthly),
                merge(kmDriven, o.kmDriven),
                merge(year, o.year),
                merge(seats, o.seats),
                min(maxOwners, o.maxOwners),
                max(minNcapStars, o.minNcapStars));
    }

    /** A query whose bounds cross returns nothing; we say so rather than showing an empty page. */
    public boolean isUnsatisfiable() {
        return anyCrossed(priceInr, emiMonthly, kmDriven, year, seats);
    }

    private static boolean anyCrossed(NumRange... rs) {
        for (NumRange r : rs) if (r != null && r.isUnsatisfiable()) return true;
        return false;
    }

    private static NumRange merge(NumRange a, NumRange b) {
        if (a == null) return b;
        return a.intersect(b);
    }

    // Written as statements, not ternaries: mixing a Long with Math.min's long
    // return promotes the whole conditional to primitive and unboxes the null
    // branch, which is a NullPointerException on every unbounded constraint.
    private static Long min(Long a, Long b) {
        if (a == null) return b;
        if (b == null) return a;
        return Math.min(a, b);
    }

    private static Long max(Long a, Long b) {
        if (a == null) return b;
        if (b == null) return a;
        return Math.max(a, b);
    }

    private static <T> List<T> union(List<T> a, List<T> b) {
        var out = new LinkedHashSet<T>(nullSafe(a));
        out.addAll(nullSafe(b));
        return List.copyOf(out);
    }

    private static <T> List<T> nullSafe(List<T> in) {
        return in == null ? List.of() : List.copyOf(new ArrayList<>(in));
    }
}
