package com.c24.vehiclesearch.search;

/** Renders rupee amounts the way Indian listings do: lakh and crore, not millions. */
public final class Money {

    private Money() {}

    public static String format(long paise) {
        if (paise >= 10_000_000) return trim(paise / 10_000_000.0) + " Cr";
        if (paise >= 100_000)    return trim(paise / 100_000.0) + "L";
        if (paise >= 1_000)      return trim(paise / 1_000.0) + "k";
        return String.valueOf(paise);
    }

    private static String trim(double v) {
        String s = String.format("%.2f", v);
        s = s.replaceAll("0+$", "").replaceAll("\\.$", "");
        return "₹" + s;
    }
}
