package com.c24.vehiclesearch.search.spec;

/**
 * An inclusive numeric bound. Either end may be null (open).
 * Everything filterable in this catalogue is integral, so one type covers
 * price, kilometres, year, seats and EMI without generic noise.
 */
public record NumRange(Long gte, Long lte) {

    public static NumRange atMost(long v)  { return new NumRange(null, v); }
    public static NumRange atLeast(long v) { return new NumRange(v, null); }
    public static NumRange between(long lo, long hi) { return new NumRange(lo, hi); }

    public boolean isEmpty() { return gte == null && lte == null; }

    /** Narrowest-wins. Two constraints on the same field intersect, never widen. */
    public NumRange intersect(NumRange other) {
        if (other == null) return this;
        return new NumRange(higher(gte, other.gte), lower(lte, other.lte));
    }

    // Same autoboxing hazard as Constraints.min/max: a ternary that mixes Long
    // with a primitive-returning Math call unboxes the null branch.
    private static Long higher(Long a, Long b) {
        if (a == null) return b;
        if (b == null) return a;
        return Math.max(a, b);
    }

    private static Long lower(Long a, Long b) {
        if (a == null) return b;
        if (b == null) return a;
        return Math.min(a, b);
    }

    /** True when the bounds cross, e.g. "over 20L" merged with "under 5L". */
    public boolean isUnsatisfiable() {
        return gte != null && lte != null && gte > lte;
    }
}
