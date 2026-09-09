package com.c24.vehiclesearch.seed;

import com.c24.vehiclesearch.catalog.BodyType;
import com.c24.vehiclesearch.catalog.FuelType;
import com.c24.vehiclesearch.catalog.Transmission;

import java.util.List;

/**
 * A real model with plausible showroom economics.
 *
 * The point of seeding from templates rather than randomising every column is
 * internal consistency: a reviewer who spots a 2024 car with 200,000 km, or a
 * hatchback with a 2500cc engine, stops trusting every other number on the page.
 *
 * @param basePriceNew ex-showroom price when new, in rupees; depreciation works from here
 * @param ncapStars    null for models that predate Indian NCAP testing — the NULLs are
 *                     deliberate, so safety ranking has to handle missing data
 */
public record ModelTemplate(
        String make,
        String model,
        BodyType bodyType,
        int seats,
        long basePriceNew,
        List<FuelType> fuels,
        List<Transmission> transmissions,
        int engineCc,
        double baseMileage,
        Integer ncapStars,
        int bootLitres,
        List<String> variants) {

    static final List<ModelTemplate> CATALOGUE = List.of(
        // --- Maruti Suzuki ---------------------------------------------------
        t("Maruti Suzuki", "Alto K10",  BodyType.HATCHBACK, 5,  400_000, f(FuelType.PETROL, FuelType.CNG), tr(Transmission.MANUAL, Transmission.AMT), 998, 24.4, 2, 214, "STD", "LXi", "VXi", "VXi+"),
        t("Maruti Suzuki", "Wagon R",   BodyType.HATCHBACK, 5,  560_000, f(FuelType.PETROL, FuelType.CNG), tr(Transmission.MANUAL, Transmission.AMT), 1197, 23.6, 2, 341, "LXi", "VXi", "ZXi", "ZXi+"),
        t("Maruti Suzuki", "Swift",     BodyType.HATCHBACK, 5,  650_000, f(FuelType.PETROL, FuelType.CNG), tr(Transmission.MANUAL, Transmission.AMT), 1197, 22.4, 2, 268, "LXi", "VXi", "ZXi", "ZXi+"),
        t("Maruti Suzuki", "Baleno",    BodyType.HATCHBACK, 5,  700_000, f(FuelType.PETROL, FuelType.CNG), tr(Transmission.MANUAL, Transmission.AMT), 1197, 22.9, 1, 318, "Sigma", "Delta", "Zeta", "Alpha"),
        t("Maruti Suzuki", "Dzire",     BodyType.SEDAN,     5,  680_000, f(FuelType.PETROL, FuelType.CNG), tr(Transmission.MANUAL, Transmission.AMT), 1197, 22.6, 2, 378, "LXi", "VXi", "ZXi", "ZXi+"),
        t("Maruti Suzuki", "Brezza",    BodyType.COMPACT_SUV, 5, 840_000, f(FuelType.PETROL, FuelType.CNG), tr(Transmission.MANUAL, Transmission.TORQUE_CONVERTER), 1462, 19.8, 4, 328, "LXi", "VXi", "ZXi", "ZXi+"),
        t("Maruti Suzuki", "Ertiga",    BodyType.MPV,       7,  870_000, f(FuelType.PETROL, FuelType.CNG), tr(Transmission.MANUAL, Transmission.TORQUE_CONVERTER), 1462, 20.5, 3, 209, "LXi", "VXi", "ZXi", "ZXi+"),
        t("Maruti Suzuki", "XL6",       BodyType.MPV,       6, 1_120_000, f(FuelType.PETROL), tr(Transmission.MANUAL, Transmission.TORQUE_CONVERTER), 1462, 20.9, 3, 209, "Zeta", "Alpha", "Alpha+"),
        t("Maruti Suzuki", "Grand Vitara", BodyType.SUV,    5, 1_100_000, f(FuelType.PETROL, FuelType.HYBRID), tr(Transmission.MANUAL, Transmission.TORQUE_CONVERTER, Transmission.CVT), 1490, 21.1, 4, 373, "Sigma", "Delta", "Zeta", "Alpha"),

        // --- Hyundai ---------------------------------------------------------
        t("Hyundai", "Grand i10 Nios",  BodyType.HATCHBACK, 5,  580_000, f(FuelType.PETROL, FuelType.CNG), tr(Transmission.MANUAL, Transmission.AMT), 1197, 20.7, 2, 260, "Era", "Magna", "Sportz", "Asta"),
        t("Hyundai", "i20",             BodyType.HATCHBACK, 5,  720_000, f(FuelType.PETROL, FuelType.DIESEL), tr(Transmission.MANUAL, Transmission.CVT, Transmission.DCT), 1197, 20.3, 3, 311, "Magna", "Sportz", "Asta", "Asta(O)"),
        t("Hyundai", "Venue",           BodyType.COMPACT_SUV, 5, 790_000, f(FuelType.PETROL, FuelType.DIESEL), tr(Transmission.MANUAL, Transmission.DCT, Transmission.AMT), 1197, 18.2, 4, 350, "E", "S", "SX", "SX(O)"),
        t("Hyundai", "Creta",           BodyType.SUV,       5, 1_100_000, f(FuelType.PETROL, FuelType.DIESEL), tr(Transmission.MANUAL, Transmission.CVT, Transmission.TORQUE_CONVERTER), 1497, 17.4, 5, 433, "E", "EX", "S", "SX", "SX(O)"),
        t("Hyundai", "Verna",           BodyType.SEDAN,     5, 1_100_000, f(FuelType.PETROL, FuelType.DIESEL), tr(Transmission.MANUAL, Transmission.CVT, Transmission.DCT), 1497, 18.4, 5, 528, "EX", "S", "SX", "SX(O)"),
        t("Hyundai", "Alcazar",         BodyType.SUV,       7, 1_650_000, f(FuelType.PETROL, FuelType.DIESEL), tr(Transmission.MANUAL, Transmission.TORQUE_CONVERTER), 1493, 17.0, 5, 180, "Prestige", "Platinum", "Signature"),

        // --- Tata ------------------------------------------------------------
        t("Tata", "Tiago",   BodyType.HATCHBACK, 5,  570_000, f(FuelType.PETROL, FuelType.CNG, FuelType.ELECTRIC), tr(Transmission.MANUAL, Transmission.AMT), 1199, 19.0, 4, 242, "XE", "XM", "XT", "XZ+"),
        t("Tata", "Altroz",  BodyType.HATCHBACK, 5,  680_000, f(FuelType.PETROL, FuelType.DIESEL, FuelType.CNG), tr(Transmission.MANUAL, Transmission.DCT), 1199, 19.3, 5, 345, "XE", "XM", "XT", "XZ+"),
        t("Tata", "Punch",   BodyType.COMPACT_SUV, 5, 640_000, f(FuelType.PETROL, FuelType.CNG, FuelType.ELECTRIC), tr(Transmission.MANUAL, Transmission.AMT), 1199, 18.8, 5, 366, "Pure", "Adventure", "Accomplished", "Creative"),
        t("Tata", "Nexon",   BodyType.COMPACT_SUV, 5,  830_000, f(FuelType.PETROL, FuelType.DIESEL, FuelType.ELECTRIC), tr(Transmission.MANUAL, Transmission.AMT, Transmission.DCT), 1199, 17.4, 5, 382, "Smart", "Pure", "Creative", "Fearless"),
        t("Tata", "Harrier", BodyType.SUV,       5, 1_550_000, f(FuelType.DIESEL), tr(Transmission.MANUAL, Transmission.TORQUE_CONVERTER), 1956, 16.3, 5, 445, "Smart", "Pure", "Adventure", "Fearless"),
        t("Tata", "Safari",  BodyType.SUV,       7, 1_620_000, f(FuelType.DIESEL), tr(Transmission.MANUAL, Transmission.TORQUE_CONVERTER), 1956, 16.1, 5, 447, "Smart", "Pure", "Adventure", "Accomplished"),

        // --- Mahindra --------------------------------------------------------
        t("Mahindra", "XUV300",   BodyType.COMPACT_SUV, 5,  800_000, f(FuelType.PETROL, FuelType.DIESEL), tr(Transmission.MANUAL, Transmission.AMT), 1497, 17.0, 5, 257, "W4", "W6", "W8", "W8(O)"),
        t("Mahindra", "Thar",     BodyType.SUV,       4, 1_350_000, f(FuelType.PETROL, FuelType.DIESEL), tr(Transmission.MANUAL, Transmission.TORQUE_CONVERTER), 2184, 15.2, 4, 200, "AX(O)", "LX"),
        t("Mahindra", "Scorpio N",BodyType.SUV,       7, 1_400_000, f(FuelType.PETROL, FuelType.DIESEL), tr(Transmission.MANUAL, Transmission.TORQUE_CONVERTER), 2184, 15.4, 5, 460, "Z2", "Z4", "Z6", "Z8"),
        t("Mahindra", "XUV700",   BodyType.SUV,       7, 1_450_000, f(FuelType.PETROL, FuelType.DIESEL), tr(Transmission.MANUAL, Transmission.TORQUE_CONVERTER), 2184, 16.0, 5, 240, "MX", "AX3", "AX5", "AX7"),
        t("Mahindra", "Bolero",   BodyType.SUV,       7,  980_000, f(FuelType.DIESEL), tr(Transmission.MANUAL), 1493, 16.0, null, 384, "B4", "B6", "B6(O)"),

        // --- Kia -------------------------------------------------------------
        t("Kia", "Sonet",  BodyType.COMPACT_SUV, 5,  800_000, f(FuelType.PETROL, FuelType.DIESEL), tr(Transmission.MANUAL, Transmission.AMT, Transmission.DCT), 1197, 18.4, 3, 392, "HTE", "HTK", "HTX", "GTX+"),
        t("Kia", "Seltos", BodyType.SUV,        5, 1_090_000, f(FuelType.PETROL, FuelType.DIESEL), tr(Transmission.MANUAL, Transmission.CVT, Transmission.DCT), 1497, 17.0, 3, 433, "HTE", "HTK", "HTX", "GTX+"),
        t("Kia", "Carens", BodyType.MPV,        7, 1_050_000, f(FuelType.PETROL, FuelType.DIESEL), tr(Transmission.MANUAL, Transmission.DCT, Transmission.TORQUE_CONVERTER), 1493, 16.5, 3, 216, "Premium", "Prestige", "Luxury", "Luxury Plus"),

        // --- Honda / Toyota --------------------------------------------------
        t("Honda", "Amaze",   BodyType.SEDAN, 5,  740_000, f(FuelType.PETROL), tr(Transmission.MANUAL, Transmission.CVT), 1199, 18.6, 2, 420, "E", "S", "VX"),
        t("Honda", "City",    BodyType.SEDAN, 5, 1_180_000, f(FuelType.PETROL, FuelType.HYBRID), tr(Transmission.MANUAL, Transmission.CVT), 1498, 18.4, 5, 506, "SV", "V", "VX", "ZX"),
        t("Honda", "Elevate", BodyType.SUV,   5, 1_130_000, f(FuelType.PETROL), tr(Transmission.MANUAL, Transmission.CVT), 1498, 16.9, 5, 458, "SV", "V", "VX", "ZX"),
        t("Toyota", "Glanza",        BodyType.HATCHBACK, 5,  690_000, f(FuelType.PETROL, FuelType.CNG), tr(Transmission.MANUAL, Transmission.AMT), 1197, 22.9, 1, 318, "E", "S", "G", "V"),
        t("Toyota", "Innova Crysta", BodyType.MPV, 7, 1_950_000, f(FuelType.DIESEL), tr(Transmission.MANUAL, Transmission.TORQUE_CONVERTER), 2393, 14.2, null, 300, "GX", "VX", "ZX"),
        t("Toyota", "Fortuner",      BodyType.LUXURY, 7, 3_400_000, f(FuelType.DIESEL), tr(Transmission.MANUAL, Transmission.TORQUE_CONVERTER), 2755, 12.4, null, 296, "4x2", "4x4", "Legender"),

        // --- VW / Skoda / others ---------------------------------------------
        t("Volkswagen", "Virtus", BodyType.SEDAN, 5, 1_150_000, f(FuelType.PETROL), tr(Transmission.MANUAL, Transmission.TORQUE_CONVERTER, Transmission.DCT), 1498, 18.7, 5, 521, "Comfortline", "Highline", "Topline", "GT"),
        t("Volkswagen", "Taigun", BodyType.SUV,   5, 1_180_000, f(FuelType.PETROL), tr(Transmission.MANUAL, Transmission.TORQUE_CONVERTER, Transmission.DCT), 1498, 18.0, 5, 385, "Comfortline", "Highline", "Topline", "GT Plus"),
        t("Skoda", "Slavia",      BodyType.SEDAN, 5, 1_140_000, f(FuelType.PETROL), tr(Transmission.MANUAL, Transmission.TORQUE_CONVERTER, Transmission.DCT), 1498, 18.7, 5, 521, "Active", "Ambition", "Style"),
        t("Skoda", "Kushaq",      BodyType.SUV,   5, 1_190_000, f(FuelType.PETROL), tr(Transmission.MANUAL, Transmission.TORQUE_CONVERTER, Transmission.DCT), 1498, 18.1, 5, 385, "Active", "Ambition", "Style"),
        t("Renault", "Kwid",      BodyType.HATCHBACK, 5, 460_000, f(FuelType.PETROL), tr(Transmission.MANUAL, Transmission.AMT), 999, 22.0, 1, 279, "RXE", "RXL", "RXT", "Climber"),
        t("Renault", "Triber",    BodyType.MPV,   7,  620_000, f(FuelType.PETROL), tr(Transmission.MANUAL, Transmission.AMT), 999, 19.0, 4, 84, "RXE", "RXL", "RXT", "RXZ"),
        t("Nissan", "Magnite",    BodyType.COMPACT_SUV, 5, 640_000, f(FuelType.PETROL), tr(Transmission.MANUAL, Transmission.CVT), 999, 18.8, 4, 336, "XE", "XL", "XV", "XV Premium"),
        t("MG", "Hector",         BodyType.SUV,   5, 1_500_000, f(FuelType.PETROL, FuelType.DIESEL), tr(Transmission.MANUAL, Transmission.CVT, Transmission.DCT), 1451, 15.6, null, 587, "Style", "Shine", "Smart", "Sharp"),
        t("MG", "Astor",          BodyType.SUV,   5, 1_100_000, f(FuelType.PETROL), tr(Transmission.MANUAL, Transmission.CVT, Transmission.TORQUE_CONVERTER), 1498, 15.4, 5, 488, "Style", "Shine", "Smart", "Sharp")
    );

    private static ModelTemplate t(String make, String model, BodyType body, int seats, long price,
                                   List<FuelType> fuels, List<Transmission> trans, int cc,
                                   double mileage, Integer ncap, int boot, String... variants) {
        return new ModelTemplate(make, model, body, seats, price, fuels, trans, cc, mileage, ncap, boot, List.of(variants));
    }

    private static List<FuelType> f(FuelType... v) { return List.of(v); }
    private static List<Transmission> tr(Transmission... v) { return List.of(v); }
}
