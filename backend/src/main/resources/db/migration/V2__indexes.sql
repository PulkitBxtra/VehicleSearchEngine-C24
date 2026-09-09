-- At demo scale (~600 rows) the planner will seq-scan regardless of these; they
-- exist so the access paths are already correct when the catalogue grows.

CREATE INDEX idx_vehicles_search_text_trgm ON vehicles USING GIN (search_text gin_trgm_ops);

-- Nearly every query carries the availability guardrail, so lead with it.
CREATE INDEX idx_vehicles_status_city   ON vehicles (status, city);
CREATE INDEX idx_vehicles_status_price  ON vehicles (status, price_inr);
CREATE INDEX idx_vehicles_status_km     ON vehicles (status, km_driven);
CREATE INDEX idx_vehicles_body_fuel     ON vehicles (body_type, fuel_type, transmission);
CREATE INDEX idx_vehicles_listed_at     ON vehicles (listed_at DESC);
