-- Trigram matching powers typo-tolerant make/model search ("hundai creta").
CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE TABLE vehicles (
    id              BIGSERIAL PRIMARY KEY,
    registration    VARCHAR(16)  NOT NULL UNIQUE,

    make            VARCHAR(40)  NOT NULL,
    model           VARCHAR(60)  NOT NULL,
    variant         VARCHAR(60)  NOT NULL,
    year            INT          NOT NULL CHECK (year BETWEEN 1990 AND 2100),

    body_type       VARCHAR(20)  NOT NULL
        CHECK (body_type IN ('HATCHBACK','SEDAN','COMPACT_SUV','SUV','MPV','LUXURY')),
    fuel_type       VARCHAR(20)  NOT NULL
        CHECK (fuel_type IN ('PETROL','DIESEL','CNG','ELECTRIC','HYBRID')),

    -- Stored as the physical gearbox, never as the user-facing word "automatic".
    -- A user asking for an automatic means any of AMT/CVT/DCT/TORQUE_CONVERTER;
    -- that expansion lives in concepts.yml, not in this column.
    transmission    VARCHAR(20)  NOT NULL
        CHECK (transmission IN ('MANUAL','AMT','CVT','DCT','TORQUE_CONVERTER')),

    price_inr       BIGINT       NOT NULL CHECK (price_inr > 0),
    km_driven       INT          NOT NULL CHECK (km_driven >= 0),
    owners          INT          NOT NULL CHECK (owners BETWEEN 1 AND 6),
    seats           INT          NOT NULL CHECK (seats BETWEEN 2 AND 9),
    engine_cc       INT          NOT NULL,
    mileage_kmpl    NUMERIC(4,1) NOT NULL,
    boot_litres     INT          NOT NULL,
    ncap_stars      INT              NULL CHECK (ncap_stars BETWEEN 0 AND 5),

    city            VARCHAR(40)  NOT NULL,
    hub             VARCHAR(60)  NOT NULL,
    colour          VARCHAR(30)  NOT NULL,

    status          VARCHAR(20)  NOT NULL DEFAULT 'AVAILABLE'
        CHECK (status IN ('AVAILABLE','RESERVED','SOLD')),
    listed_at       DATE         NOT NULL,

    inspection_score NUMERIC(3,1) NOT NULL CHECK (inspection_score BETWEEN 0 AND 10),

    -- How far below the cohort (model, year, km-band) median this car is priced.
    -- Positive == cheaper than peers == a better deal. Computed by the seeder.
    deal_score      NUMERIC(4,3) NOT NULL DEFAULT 0,

    features        JSONB        NOT NULL DEFAULT '[]'::jsonb,

    -- Indian buyers shop by monthly outgo, not sticker price. Fixed assumptions:
    -- 85% loan-to-value, 9.5% p.a. reducing balance, 60 months.
    emi_monthly     INT GENERATED ALWAYS AS (
        CEIL(
            (price_inr * 0.85 * (0.095/12) * POWER(1 + 0.095/12, 60))
            / (POWER(1 + 0.095/12, 60) - 1)
        )::INT
    ) STORED,

    search_text     TEXT GENERATED ALWAYS AS (
        make || ' ' || model || ' ' || variant
    ) STORED
);

COMMENT ON COLUMN vehicles.deal_score IS
    'Fractional discount vs cohort median price; drives ranking, not filtering.';
