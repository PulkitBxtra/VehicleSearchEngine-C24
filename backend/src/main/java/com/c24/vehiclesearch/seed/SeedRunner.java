package com.c24.vehiclesearch.seed;

import com.c24.vehiclesearch.catalog.FuelType;
import com.c24.vehiclesearch.catalog.Transmission;
import com.c24.vehiclesearch.catalog.VehicleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Generates the demo catalogue.
 *
 * Two properties matter more than volume:
 *
 *  - Internal consistency. Price falls out of age, kilometres, owners and
 *    condition rather than being drawn independently, so no listing contradicts
 *    itself. Random columns produce 2024 cars with 200,000 km, and a reviewer
 *    who sees one stops believing the rest of the page.
 *
 *  - Reproducibility. The RNG is seeded with a constant, so every clone of this
 *    repo produces byte-identical inventory and the golden query expectations
 *    in the eval set stay valid.
 */
@Component
@ConditionalOnProperty(name = "search.seed.enabled", havingValue = "true", matchIfMissing = true)
public class SeedRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SeedRunner.class);
    private static final long RNG_SEED = 20260909L;

    private static final List<String> CITIES = List.of(
            "Mumbai", "Delhi", "Bengaluru", "Pune", "Hyderabad",
            "Chennai", "Kolkata", "Ahmedabad", "Jaipur", "Chandigarh");
    private static final List<String> RTO = List.of(
            "MH01", "DL03", "KA05", "MH12", "TS09", "TN10", "WB02", "GJ01", "RJ14", "CH01");
    private static final List<String> COLOURS = List.of(
            "White", "Silver", "Grey", "Black", "Red", "Blue", "Brown", "Bronze");
    private static final List<String> FEATURE_POOL = List.of(
            "Sunroof", "Reverse Camera", "Cruise Control", "Alloy Wheels", "Touchscreen",
            "Android Auto", "Apple CarPlay", "Push Button Start", "Ventilated Seats",
            "Wireless Charging", "360 Camera", "ADAS", "Leather Seats", "Rain Sensing Wipers");

    private final JdbcTemplate jdbc;
    private final VehicleRepository repository;
    private final int targetCount;

    public SeedRunner(JdbcTemplate jdbc, VehicleRepository repository,
                      @Value("${search.seed.count:600}") int targetCount) {
        this.jdbc = jdbc;
        this.repository = repository;
        this.targetCount = targetCount;
    }

    @Override
    public void run(ApplicationArguments args) {
        // Idempotent: a redeploy must not duplicate the catalogue.
        long existing = repository.countAll();
        if (existing > 0) {
            log.info("Catalogue already holds {} vehicles; skipping seed.", existing);
            return;
        }

        var rng = new Random(RNG_SEED);
        var rows = new ArrayList<Object[]>(targetCount);
        int currentYear = LocalDate.now().getYear();

        for (int i = 0; i < targetCount; i++) {
            rows.add(listing(rng, currentYear, i));
        }

        jdbc.batchUpdate("""
                INSERT INTO vehicles (
                    registration, make, model, variant, year, body_type, fuel_type, transmission,
                    price_inr, km_driven, owners, seats, engine_cc, mileage_kmpl, range_km,
                    boot_litres, ncap_stars, city, hub, colour, status, listed_at,
                    inspection_score, features)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?::jsonb)
                """, rows);

        int scored = computeDealScores();
        log.info("Seeded {} vehicles across {} models; deal scores computed for {} rows.",
                rows.size(), ModelTemplate.CATALOGUE.size(), scored);
    }

    private Object[] listing(Random rng, int currentYear, int index) {
        var t = pick(rng, ModelTemplate.CATALOGUE);

        // Skew towards recent stock, the way real used inventory sits.
        int age = (int) Math.min(9, Math.abs(rng.nextGaussian()) * 2.6);
        int year = currentYear - age;

        FuelType fuel = pick(rng, t.fuels());
        Transmission transmission = pickTransmission(rng, t, age);

        // Roughly 11k km a year with real spread, floored so nothing is implausible.
        int km = (int) Math.max(1_200, (age * 11_000 + rng.nextGaussian() * 9_000) * (0.6 + rng.nextDouble() * 0.8));
        int owners = age <= 2 ? 1 : 1 + (int) Math.min(3, Math.abs(rng.nextGaussian() * 0.9));
        double inspection = Math.min(9.9, Math.max(5.5, 9.4 - age * 0.22 + rng.nextGaussian() * 0.5));

        long price = price(t, age, km, owners, inspection, fuel, rng);
        boolean electric = fuel == FuelType.ELECTRIC;
        Double mileage = electric ? null : mileage(t, fuel, transmission);
        Integer rangeKm = electric ? 240 + rng.nextInt(130) : null;

        int cityIdx = rng.nextInt(CITIES.size());
        LocalDate listedAt = LocalDate.now().minusDays(rng.nextInt(120));

        // Older cars predate Indian NCAP testing; leaving these null keeps the
        // safety ranking honest about missing data instead of inventing zeroes.
        Integer ncap = (year < 2016) ? null : t.ncapStars();

        return new Object[]{
                registration(rng, cityIdx, index),
                t.make(), t.model(), pick(rng, t.variants()), year,
                t.bodyType().name(), fuel.name(), transmission.name(),
                price, km, owners, t.seats(), t.engineCc(),
                mileage == null ? null : Math.round(mileage * 10) / 10.0,
                rangeKm, t.bootLitres(), ncap,
                CITIES.get(cityIdx), CITIES.get(cityIdx) + " Hub", pick(rng, COLOURS),
                "AVAILABLE", java.sql.Date.valueOf(listedAt),
                Math.round(inspection * 10) / 10.0, features(rng, t)
        };
    }

    /** Automatics are rarer on older, cheaper stock — matches how the market actually looks. */
    private Transmission pickTransmission(Random rng, ModelTemplate t, int age) {
        boolean preferManual = rng.nextDouble() < (0.45 + age * 0.04);
        if (preferManual && t.transmissions().contains(Transmission.MANUAL)) return Transmission.MANUAL;
        var autos = t.transmissions().stream().filter(x -> x != Transmission.MANUAL).toList();
        return autos.isEmpty() ? Transmission.MANUAL : pick(rng, autos);
    }

    private long price(ModelTemplate t, int age, int km, int owners, double inspection,
                       FuelType fuel, Random rng) {
        // Fuel choice moves the showroom price before any depreciation applies;
        // without this an electric Nexon lists at the price of a petrol one.
        double v = t.basePriceNew() * switch (fuel) {
            case ELECTRIC -> 1.55;
            case HYBRID   -> 1.25;
            case DIESEL   -> 1.12;
            case CNG      -> 1.06;
            case PETROL   -> 1.00;
        };
        v *= Math.pow(0.87, age);                                   // straight depreciation
        v *= Math.max(0.75, 1 - Math.max(0, km - age * 11_000) / 600_000.0);  // penalty for above-average use
        v *= 1 - 0.045 * (owners - 1);                              // each extra owner costs
        v *= 0.95 + (inspection - 5.5) / 44.0;                      // condition premium
        v *= 0.93 + rng.nextDouble() * 0.14;                        // dealer-to-dealer spread
        v = Math.max(v, t.basePriceNew() * 0.22);                   // scrap floor
        return Math.round(v / 5_000) * 5_000;                       // listings price to round numbers
    }

    private double mileage(ModelTemplate t, FuelType fuel, Transmission transmission) {
        double m = t.baseMileage();
        m *= switch (fuel) {
            case DIESEL   -> 1.15;
            case CNG      -> 1.35;
            case HYBRID   -> 1.40;
            // Never reached: electrics take range_km and skip this entirely.
            case ELECTRIC -> 1.00;
            case PETROL   -> 1.00;
        };
        if (transmission != Transmission.MANUAL) m *= 0.93;
        return m;
    }

    private String features(Random rng, ModelTemplate t) {
        int n = 2 + rng.nextInt(5);
        var pool = new ArrayList<>(FEATURE_POOL);
        var chosen = new ArrayList<String>();
        for (int i = 0; i < n && !pool.isEmpty(); i++) chosen.add(pool.remove(rng.nextInt(pool.size())));
        return chosen.stream().map(s -> "\"" + s + "\"")
                .reduce((a, b) -> a + "," + b).map(s -> "[" + s + "]").orElse("[]");
    }

    private String registration(Random rng, int cityIdx, int index) {
        char a = (char) ('A' + rng.nextInt(26));
        char b = (char) ('A' + rng.nextInt(26));
        return "%s%c%c%04d".formatted(RTO.get(cityIdx), a, b, index % 10_000);
    }

    /**
     * Deal score: how far below the cohort median this car is priced, where a
     * cohort is (model, year). It is the signal behind "great deal" badges, and
     * it only ever ranks — a poorly priced car is still a valid result.
     */
    private int computeDealScores() {
        return jdbc.update("""
                WITH cohort AS (
                    SELECT model, year,
                           PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY price_inr) AS median_price
                    FROM vehicles
                    GROUP BY model, year
                    HAVING COUNT(*) >= 2
                )
                UPDATE vehicles v
                SET deal_score = GREATEST(-0.999, LEAST(0.999,
                        ((c.median_price - v.price_inr) / NULLIF(c.median_price, 0))::numeric))
                FROM cohort c
                WHERE v.model = c.model AND v.year = c.year
                """);
    }

    private static <T> T pick(Random rng, List<T> from) {
        return from.get(rng.nextInt(from.size()));
    }
}
