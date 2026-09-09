-- Kilometres per litre has no meaning for an electric vehicle. Storing a
-- "petrol equivalent" there produced listings claiming 48.9 kmpl, which is the
-- kind of number that makes a reader distrust every other figure on the page.
--
-- The honest model is that the column does not apply, and that electrics carry
-- a different measure entirely.
ALTER TABLE vehicles ALTER COLUMN mileage_kmpl DROP NOT NULL;

ALTER TABLE vehicles ADD COLUMN range_km INT NULL
    CHECK (range_km IS NULL OR range_km BETWEEN 50 AND 1000);

COMMENT ON COLUMN vehicles.mileage_kmpl IS
    'Fuel economy in km/l. NULL for electrics, which use range_km instead.';
COMMENT ON COLUMN vehicles.range_km IS
    'Full-charge range in km. NULL for everything except electrics.';

-- Backfill before the constraint is added, so this migration is safe against a
-- database that already holds seeded rows rather than only against a fresh one.
-- A CHECK constraint validates existing rows at creation time; without this the
-- migration fails on every deployment that has ever run.
UPDATE vehicles
SET range_km     = 240 + (id % 130)::int,
    mileage_kmpl = NULL
WHERE fuel_type = 'ELECTRIC';

-- Exactly one of the two applies, always.
ALTER TABLE vehicles ADD CONSTRAINT economy_matches_fuel CHECK (
    (fuel_type = 'ELECTRIC' AND range_km IS NOT NULL AND mileage_kmpl IS NULL)
 OR (fuel_type <> 'ELECTRIC' AND mileage_kmpl IS NOT NULL AND range_km IS NULL)
);
