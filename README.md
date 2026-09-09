# Vehicle Search

Natural-language search over a used-car catalogue. Type a sentence, get filtered
and ranked results — plus a visible account of exactly how the sentence was
interpreted.

```
"Diesel automatic cars below 80k km"
  → fuel = DIESEL
  → transmission ∈ {AMT, CVT, DCT, TORQUE_CONVERTER}
  → km_driven ≤ 80000
  → 86 matches in 9ms
```

## Status

Both paths are in. Queries resolve through a ladder:

```
explicit FilterSpec  →  EXPLICIT   (chip edits; no parsing at all)
rules parser         →  RULES      (formulaic queries; no model call)
Gemini               →  LLM        (the tail; cached, budgeted, timed out)
rules result         →  RULES      (floor, when the model cannot answer)
```

The `parser` field on every response says which one ran.

Graded against a real key. The baseline run was **rules 28/28, Gemini 24/28**;
three of the four model misses turned out to be defects in the golden set rather
than in the model. After fixing those, both parsers score **29/29**. Full
write-up in [EVALUATION.md](EVALUATION.md).

## Running it

Requires Docker and a JDK 21 on the path. Nothing else — Maven comes via the
wrapper.

```bash
docker compose up -d db          # Postgres 16 on 127.0.0.1:5432

cd backend && ./mvnw spring-boot:run
# Flyway migrates, then the seeder writes 600 vehicles. Ready on :8080.

# Optional: enable natural-language parsing beyond the rules.
# Without it every formulaic query still works; conversational ones do not.
export GEMINI_API_KEY=...      # from Google AI Studio
./mvnw spring-boot:run

cd frontend && npm install && npm run dev
# http://localhost:5173, proxying /api to :8080
```

If `./mvnw` fails with `class, interface, or enum expected`, your `JAVA_HOME`
points at a JDK older than 16:

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)   # macOS
```

### Tests

```bash
cd backend && ./mvnw test
```

`RulesParserGoldenTest` runs the 28-query golden set in
`src/test/resources/eval/queries.json` with no database and no network. The
context test uses Testcontainers, so nothing needs to be running.

### Evaluating the model

Live evaluations cost money and need a network, so they are excluded from the
default build. Run one deliberately:

```bash
GEMINI_API_KEY=... ./mvnw test -Dgroups=live -Dtest=GeminiLiveEvalTest
```

It grades Gemini against the **same** golden set the rules parser is graded on
and prints them side by side, so a prompt edit can be measured rather than
guessed at:

```
QUERY                                         RULES   LLM
--------------------------------------------------------------
Show SUVs under ₹15L                          pass    pass
7 seater diesel                               pass    FAIL
      llm: seatsMin: want 7, got 6
--------------------------------------------------------------
rules      28/28 (100%)
llm        24/28 (86%) overall, 24/28 (86%) of answered
unanswered 0  (quota 0, other 0)
```

*(baseline run — see EVALUATION.md for what each failure turned out to be)*

A full run is paced to stay inside the free tier and takes ~15 minutes. After a
prompt edit, re-check a few queries instead:

```bash
EVAL_ONLY='before 2018|newest cars' ./mvnw test -DexcludedGroups= -Dtest=GeminiLiveEvalTest
```

## Configuration

| Variable | Default | Purpose |
|---|---|---|
| `GEMINI_API_KEY` | *(empty)* | Absent means the LLM path is off, not broken |
| `GEMINI_MODEL` | `gemini-3.5-flash-lite` | Pinned, not aliased. Free quota is 20/day **per model** |
| `GEMINI_MAX_DAILY_CALLS` | `500` | Hard spend ceiling; past it, rules serve |
| `RATE_LIMIT_RPM` | `60` | Per-client limit on `/api/v1/search` |
| `SEED_COUNT` | `600` | Vehicles to generate |
| `DB_URL` / `DB_USER` / `DB_PASSWORD` | local dev values | |

## API

Base path `/api/v1`.

### `POST /search`

Two ways in, one contract. Send `query` for natural language, or `filters` to
run a `FilterSpec` directly — which is what the UI does when you remove a chip,
so refining a search costs no parse.

```jsonc
{
  "query": "Show SUVs under ₹15L",   // optional
  "filters": { /* FilterSpec */ },   // optional; wins if both are present
  "page": 0,                         // default 0
  "size": 20                         // default 20, max 100
}
```

Response:

| Field | Meaning |
|---|---|
| `interpretation.chips` | What was understood. `kind` is `CONSTRAINT` (removed vehicles) or `PREFERENCE` (only reordered them) |
| `interpretation.notes` | Human-readable caveats — active sort, contradictions, uninterpreted terms |
| `filters` | The `FilterSpec` that actually ran. Edit a field, resubmit as `filters` |
| `results[]` | Matching vehicles, each with the `score` that ordered it |
| `totalElements` | Total matches, ignoring pagination |
| `facets` | `dimension → value → count`, computed over the filtered set |
| `parser` | `RULES`, `EXPLICIT`, `LLM`, or `NONE` |
| `tookMs` | Server-side latency |
| `warnings[]` | Zero results, contradictory bounds, unparsed terms |
| `scoreSql` | The ranking expression used, so any ordering can be explained |

```bash
curl -s localhost:8080/api/v1/search -H 'Content-Type: application/json' \
  -d '{"query":"family cars with high safety ratings","size":5}'
```

### `GET /vehicles/{id}`

One vehicle, or `404`.

### `GET /v3/api-docs` and `/swagger-ui.html`

The OpenAPI document, generated by springdoc from the controller and its
records. The frontend's TypeScript types are generated from it with
`npm run types`, so the contract cannot drift: rename a field in a Java record
and the frontend build fails at the call site.

### `GET /schema`

Enum values, the concept vocabulary with each term's `kind`, actual min/max
ranges from live inventory, cities, makes, and sort options. The UI builds its
controls from this rather than hardcoding enums; it is also the vocabulary the
LLM prompt will be given.

### `GET /actuator/health`

## What the parser understands

| Input | Becomes |
|---|---|
| `under ₹15L`, `below 15 lakh`, `under 15,00,000` | `priceInr ≤ 1500000` |
| `below 80k km`, `under 50000 km` | `kmDriven ≤ 80000` |
| `between 8 and 12 lakh` | `priceInr ∈ [800000, 1200000]` |
| `emi under 15000`, `under 20k per month` | `emiMonthly ≤ …` |
| `after 2020`, `before 2018`, `2019 model` | `year` bounds |
| `7 seater`, `first owner` | `seats ≥ 7`, `maxOwners = 1` |
| `cheapest`, `newest` | explicit sort, overriding relevance |
| `automatic` | `transmission ∈ {AMT, CVT, DCT, TORQUE_CONVERTER}` |
| `family`, `high safety`, `fuel efficient` | ranking preferences, no filtering |
| anything left over | trigram match on make/model/variant |

Vocabulary lives in `backend/src/main/resources/concepts/concepts.yml`.

Anything the rules cannot fully consume — `"a reliable car for my office
commute in Bangalore"` — escalates to Gemini, which maps it onto the *same*
vocabulary. The model picks concept keys; `concepts.yml` decides what they mean.
It cannot emit column names, boosts, or SQL.

## Seed data

600 vehicles across 44 real models, generated by `SeedRunner` under the `seed`
profile. Price is derived from age, kilometres, owners, fuel and condition
rather than drawn independently, so no listing contradicts itself. The RNG is
seeded with a constant, so every clone produces identical inventory and the
golden expectations stay valid. Seeding is idempotent — it skips if the table is
non-empty.

## Deployment

See [`deploy/README.md`](deploy/README.md). Single VPS, Caddy terminating TLS,
app and database on the internal Docker network only.

## Design notes

See [`DESIGN.md`](DESIGN.md).
