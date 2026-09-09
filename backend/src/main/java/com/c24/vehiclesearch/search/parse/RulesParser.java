package com.c24.vehiclesearch.search.parse;

import com.c24.vehiclesearch.search.concept.ConceptDictionary;
import com.c24.vehiclesearch.search.spec.Constraints;
import com.c24.vehiclesearch.search.spec.FilterSpec;
import com.c24.vehiclesearch.search.spec.NumRange;
import com.c24.vehiclesearch.search.spec.SortOption;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic query parser.
 *
 * This is the primary path, not a fallback. Marketplace query distributions are
 * heavily Zipfian and formulaic — "diesel suv under 10 lakh", "automatic below
 * 50000 km" — so a few dozen patterns cover most traffic at zero latency and
 * zero cost. The LLM handles the tail that lands here as residual text.
 *
 * Matched spans are blanked out as they are consumed, so each matcher sees only
 * what earlier ones did not claim, and whatever survives is genuinely
 * unrecognised rather than accidentally double-counted.
 */
@Component
public class RulesParser {

    private static final int MIN_YEAR = 1990;
    private static final int MAX_YEAR = 2100;

    private static final String LOWER = "under|below|less than|lesser than|upto|up to|within|cheaper than|max|maximum|at most";
    private static final String UPPER = "over|above|more than|at ?least|min|minimum|starting at|starting from";

    private static final Pattern BETWEEN = Pattern.compile(
            "(?:between|from)\\s*(?:rs\\.?|inr)?\\s*" + Amounts.NUM
                    + "\\s*(?:and|to|-|–)\\s*(?:rs\\.?|inr)?\\s*" + Amounts.NUM
                    + "\\s*(kms?|kilometers?|kilometres?)?");

    private static final Pattern BOUNDED = Pattern.compile(
            "(" + LOWER + "|" + UPPER + ")\\s*(?:rs\\.?|inr)?\\s*"
                    + Amounts.NUM + "\\s*(kms?|kilometers?|kilometres?)?");

    private static final Pattern EMI = Pattern.compile(
            "(?:emi\\s*(?:of|under|below|upto|up to)?\\s*(?:rs\\.?)?\\s*" + Amounts.NUM + ")"
                    + "|(?:(?:rs\\.?)?\\s*" + Amounts.NUM + "\\s*(?:per month|/month|a month|monthly|p\\.?m\\.?)\\b)");

    private static final Pattern YEAR_LOWER = Pattern.compile(
            "(?:after|post|newer than|since|from|above)\\s*((?:19|20)\\d{2})");
    private static final Pattern YEAR_UPPER = Pattern.compile(
            "(?:before|older than|upto|up to|till)\\s*((?:19|20)\\d{2})");
    private static final Pattern YEAR_BARE = Pattern.compile("\\b((?:19|20)\\d{2})\\b");

    private static final Pattern SEATER = Pattern.compile("(\\d)\\s*[- ]?seater\\b|\\b(\\d)\\s*seats?\\b");

    private static final Pattern SORT_CHEAP = Pattern.compile("\\b(cheapest|lowest price|price low to high|cheap first)\\b");
    private static final Pattern SORT_NEW   = Pattern.compile("\\b(newest|latest|most recent|new(?:est)? first)\\b");
    private static final Pattern SORT_KM    = Pattern.compile("\\b(least driven|lowest km|least km)\\b");

    /** Words that carry no filtering signal; dropped before free-text matching. */
    private static final Set<String> STOPWORDS = Set.of(
            "show", "me", "find", "get", "list", "search", "want", "looking", "need", "please",
            "car", "cars", "vehicle", "vehicles", "with", "and", "or", "a", "an", "the", "in",
            "for", "of", "all", "any", "some", "good", "nice", "best", "i", "my", "is", "are",
            "that", "which", "model", "variant", "used", "condition", "have", "has", "give", "under", "below", "km", "kms", "rs");

    private final ConceptDictionary concepts;

    public RulesParser(ConceptDictionary concepts) {
        this.concepts = concepts;
    }

    public RuleParse parse(String rawQuery) {
        String work = normalise(rawQuery);
        var acc = new Accumulator();

        work = applyEmi(work, acc);          // before generic bounds: "under 15k a month" is EMI, not price
        work = applyBetween(work, acc);
        work = applyBounded(work, acc);
        work = applyYears(work, acc);
        work = applySeater(work, acc);
        work = applySort(work, acc);
        work = applyConcepts(work, acc);

        String residual = residual(work);
        FilterSpec spec = acc.build();
        if (residual != null) spec = spec.withFreeText(residual);

        // Confident when we extracted real structure and nothing unexplained is left.
        boolean confident = acc.matched > 0 && residual == null;
        return new RuleParse(spec, residual, confident);
    }

    // ---------------------------------------------------------------- matchers

    private String applyEmi(String s, Accumulator acc) {
        Matcher m = EMI.matcher(s);
        var sb = new StringBuilder(s);
        while (m.find()) {
            String d = m.group(1) != null ? m.group(1) : m.group(3);
            String u = m.group(1) != null ? m.group(2) : m.group(4);
            if (d == null) continue;
            acc.emi(NumRange.atMost(Amounts.parse(d, u)));
            blank(sb, m.start(), m.end());
        }
        return sb.toString();
    }

    private String applyBetween(String s, Accumulator acc) {
        Matcher m = BETWEEN.matcher(s);
        var sb = new StringBuilder(s);
        while (m.find()) {
            // "between 8 and 12 lakh": the magnitude is stated once, after the
            // second number, and applies to both. Taking group(2) literally
            // yields a lower bound of eight rupees.
            String lowUnit = m.group(2) != null ? m.group(2) : m.group(4);
            long lo = Amounts.parse(m.group(1), lowUnit);
            long hi = Amounts.parse(m.group(3), m.group(4));
            if (lo > hi) { long t = lo; lo = hi; hi = t; }
            boolean km = m.group(5) != null;
            if (km) acc.km(NumRange.between(lo, hi)); else acc.price(NumRange.between(lo, hi));
            blank(sb, m.start(), m.end());
        }
        return sb.toString();
    }

    private String applyBounded(String s, Accumulator acc) {
        Matcher m = BOUNDED.matcher(s);
        var sb = new StringBuilder(s);
        while (m.find()) {
            String op = m.group(1);
            long value = Amounts.parse(m.group(2), m.group(3));
            boolean isKm = m.group(4) != null;
            boolean isLower = op.matches(LOWER);

            // A bare four-digit year after "after/before" is handled by the year
            // matchers; here a unit-less number in year range with no currency
            // hint would be ambiguous, so treat it as a year only if it parses
            // cleanly and no magnitude suffix was given.
            boolean looksLikeYear = !isKm && m.group(3) == null
                    && value >= MIN_YEAR && value <= MAX_YEAR
                    && m.group(2).length() == 4;

            if (isKm)             acc.km(isLower ? NumRange.atMost(value) : NumRange.atLeast(value));
            else if (looksLikeYear) acc.year(isLower ? NumRange.atMost(value) : NumRange.atLeast(value));
            else                  acc.price(isLower ? NumRange.atMost(value) : NumRange.atLeast(value));

            blank(sb, m.start(), m.end());
        }
        return sb.toString();
    }

    private String applyYears(String s, Accumulator acc) {
        var sb = new StringBuilder(s);
        sb = consume(sb, YEAR_LOWER, (m, b) -> acc.year(NumRange.atLeast(Long.parseLong(m.group(1)))));
        sb = consume(sb, YEAR_UPPER, (m, b) -> acc.year(NumRange.atMost(Long.parseLong(m.group(1)))));
        sb = consume(sb, YEAR_BARE,  (m, b) -> {
            long y = Long.parseLong(m.group(1));
            acc.year(new NumRange(y, y));
        });
        return sb.toString();
    }

    private String applySeater(String s, Accumulator acc) {
        return consume(new StringBuilder(s), SEATER, (m, b) -> {
            String g = m.group(1) != null ? m.group(1) : m.group(2);
            acc.seats(NumRange.atLeast(Long.parseLong(g)));
        }).toString();
    }

    private String applySort(String s, Accumulator acc) {
        var sb = new StringBuilder(s);
        sb = consume(sb, SORT_CHEAP, (m, b) -> acc.sort(SortOption.PRICE_ASC));
        sb = consume(sb, SORT_NEW,   (m, b) -> acc.sort(SortOption.NEWEST_LISTED));
        sb = consume(sb, SORT_KM,    (m, b) -> acc.sort(SortOption.KM_ASC));
        return sb.toString();
    }

    /**
     * Greedy longest-first term matching, so "high safety rating" is claimed as
     * one concept before the bare term "safety" can grab part of it.
     */
    private String applyConcepts(String s, Accumulator acc) {
        var sb = new StringBuilder(s);
        for (var entry : concepts.termIndex()) {
            String term = entry.getKey();
            int from = 0;
            while (true) {
                int idx = sb.indexOf(term, from);
                if (idx < 0) break;
                if (isWordBoundary(sb, idx, term.length())) {
                    acc.concept(entry.getValue());
                    blank(sb, idx, idx + term.length());
                }
                from = idx + term.length();
            }
        }
        return sb.toString();
    }

    // ---------------------------------------------------------------- helpers

    private static String normalise(String q) {
        if (q == null) return "";
        return q.toLowerCase()
                .replace('₹', ' ')            // rupee sign
                .replaceAll("\\brupees?\\b", " ")
                .replaceAll("[^a-z0-9,.\\-/ ]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String residual(String work) {
        String text = java.util.Arrays.stream(work.split("[^a-z0-9]+"))
                .filter(t -> !t.isBlank())
                .filter(t -> t.length() > 1)
                .filter(t -> !STOPWORDS.contains(t))
                .reduce("", (a, b) -> a.isEmpty() ? b : a + " " + b);
        return text.isBlank() ? null : text;
    }

    private static boolean isWordBoundary(CharSequence s, int start, int len) {
        boolean leftOk  = start == 0 || !Character.isLetterOrDigit(s.charAt(start - 1));
        int end = start + len;
        boolean rightOk = end >= s.length() || !Character.isLetterOrDigit(s.charAt(end));
        return leftOk && rightOk;
    }

    private static void blank(StringBuilder sb, int start, int end) {
        for (int i = start; i < end && i < sb.length(); i++) sb.setCharAt(i, ' ');
    }

    private interface Handler { void accept(Matcher m, StringBuilder sb); }

    private static StringBuilder consume(StringBuilder sb, Pattern p, Handler h) {
        Matcher m = p.matcher(sb.toString());
        while (m.find()) {
            h.accept(m, sb);
            blank(sb, m.start(), m.end());
        }
        return sb;
    }

    /** Mutable scratch space; folds into an immutable FilterSpec at the end. */
    private final class Accumulator {
        private Constraints constraints = Constraints.empty();
        private FilterSpec spec = FilterSpec.empty();
        private SortOption sort = SortOption.RELEVANCE;
        private int matched = 0;

        void price(NumRange r) { constraints = constraints.merge(priceOnly(r)); matched++; }
        void km(NumRange r)    { constraints = constraints.merge(kmOnly(r));    matched++; }
        void year(NumRange r)  { constraints = constraints.merge(yearOnly(r));  matched++; }
        void seats(NumRange r) { constraints = constraints.merge(seatsOnly(r)); matched++; }
        void emi(NumRange r)   { constraints = constraints.merge(emiOnly(r));   matched++; }
        void sort(SortOption s){ sort = s; matched++; }

        void concept(String key) {
            spec = concepts.apply(spec, key);
            matched++;
        }

        FilterSpec build() {
            return spec.withConstraints(spec.constraints().merge(constraints)).withSort(sort);
        }

        private Constraints priceOnly(NumRange r) { return new Constraints(null,null,null,null,null,r,null,null,null,null,null,null); }
        private Constraints kmOnly(NumRange r)    { return new Constraints(null,null,null,null,null,null,null,r,null,null,null,null); }
        private Constraints yearOnly(NumRange r)  { return new Constraints(null,null,null,null,null,null,null,null,r,null,null,null); }
        private Constraints seatsOnly(NumRange r) { return new Constraints(null,null,null,null,null,null,null,null,null,r,null,null); }
        private Constraints emiOnly(NumRange r)   { return new Constraints(null,null,null,null,null,null,r,null,null,null,null,null); }
    }
}
