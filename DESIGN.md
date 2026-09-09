# Design

## The problem, restated

"Search a vehicle catalogue using natural language" looks like a retrieval
problem. It is mostly a **translation** problem with a **ranking** problem
hidden inside it, and the three example queries are chosen well enough to show
both:

| Query | What it actually asks for |
|---|---|
| `SUVs under ₹15L` | Two hard constraints. A ₹40L SUV is not a worse match, it is not a match |
| `Diesel automatic cars below 80k km` | Hard constraints, but `automatic` is a *class* of gearboxes, not a column value |
| `Family cars with high safety ratings` | Neither term is a filter. A five-seat five-star car should rank lower, not disappear |

Everything below follows from taking that third row seriously.

## Architecture

```
"diesel automatic under 80k km"
        │
   ┌────▼──────────────────────────┐
   │ QUERY UNDERSTANDING           │   rules → (LLM) → cache
   │ produces a FilterSpec         │
   └────┬──────────────────────────┘
        │  FilterSpec — the only contract in the system
   ┌────▼──────────────────────────┐
   │ QUERY COMPILER                │   constraints → WHERE
   │ + server-injected guardrails  │   preferences → ORDER BY
   └────┬──────────────────────────┘
        │
   ┌────▼──────────────────────────┐
   │ POSTGRES                      │   one seq scan, sub-millisecond
   └───────────────────────────────┘
```

## Decisions

### The LLM parses; it never retrieves

Gemini's only job is `text → FilterSpec`, emitted against a JSON schema. It does
not write SQL, does not see the database, and does not rank anything.

Two alternatives were considered and rejected:

- **NL → SQL.** An injection surface, non-deterministic, untestable without a
  live database, and prone to hallucinating columns.
- **Embed the catalogue and vector-search it.** Cosine similarity has no way to
  honour "under ₹15L". Numeric constraints are not a semantic problem.

Because the model's output is a small closed record, it can be validated by Bean
Validation and asserted against a golden set with the network mocked. Everything
after the parse is ordinary deterministic Java.

### `FilterSpec` is the only contract

Natural language and the facet UI produce the *same* object, and only that
object reaches the compiler. If the language path could express something the
facet path could not, there would be two search engines, one of them untested.

This also means removing a filter chip in the UI is a mechanical edit to a
record — no re-parse, no LLM call. `SearchRequest` accepts `filters` directly for
exactly this.

Every refinement takes that route, not just chips. Changing the sort or turning
a page resubmits the `FilterSpec` the last response returned rather than the
original sentence, so a user who pages through 200 results and reorders them
twice still costs exactly one parse. Without it, page two would re-parse the
query and — for a natural-language search — spend a second model call
reproducing an answer already in hand.

### Constraints filter, preferences rank

The single most consequential split in the codebase.

```java
constraints  →  WHERE clauses   // remove rows
preferences  →  ORDER BY terms  // reorder rows
```

`"family cars with high safety ratings"` therefore returns the entire available
catalogue, reordered, with seven-seat five-star SUVs on top. Compiling it to
`WHERE seats >= 6 AND ncap_stars >= 4` would be a subtle, silent, and very
common bug: the user sees plausible results and never learns what was hidden.

The ranking expression is deliberately readable, because "why is this car third?"
must have an answer:

```sql
ORDER BY ( 1.5 * deal_score
         + 0.5 * exp(-(CURRENT_DATE - listed_at) / 60.0)
         + 4.0 * (CASE WHEN ncap_stars >= 4 THEN 1 ELSE 0 END)
         + 3.0 * (CASE WHEN seats      >= 6 THEN 1 ELSE 0 END) ) DESC
```

It is returned to the client as `scoreSql`.

### Domain vocabulary is data, not code

`concepts.yml` maps how people talk to what the columns hold. The entry that
justifies the whole file:

```yaml
automatic:
  terms: [automatic, auto, self shifting, no clutch]
  constraints:
    transmissions: [AMT, CVT, DCT, TORQUE_CONVERTER]
```

The `transmission` column stores the physical gearbox. A user asking for an
"automatic" means any of four values, and matching a single literal
`AUTOMATIC` would silently drop most of the automatic inventory. Recall bugs of
this shape are invisible in testing — results still appear, they are just the
wrong ones.

Keeping this in a reviewed YAML file rather than in search-engine analyzer
config means it is diffable, unit-tested against the golden set, and editable by
someone who knows cars but not Java.

### Rules first, model second

The deterministic parser is the **primary** path, not a fallback. Marketplace
query distributions are Zipfian and formulaic, so a few dozen patterns cover
most traffic at zero latency and zero cost.

```
explicit spec → rules (confident?) → cache → Gemini → rules as floor
```

`RulesParser` reports `confident` when it consumed the whole query and left no
residual. In that case the model is never called: `"diesel automatic under 80k
km"` costs no token, no round trip, and no variance.

An outage, a timeout, an exhausted budget and a missing key are all the same
event to the caller — `Optional.empty()`, and the rules result ships instead.
The service degrades; it does not fail.

### What the model is allowed to say

`LlmFilterDraft` is deliberately narrower than `FilterSpec`. The model emits
**concept keys** from a closed enum — never preferences, boosts, column names or
sort expressions:

```jsonc
{ "concepts": ["family", "high_safety"],   // enum, from concepts.yml
  "priceMax": 1500000,
  "freeText": "creta",
  "unmapped": ["sunroof"] }
```

So the model decides *which vocabulary applies*, and `concepts.yml` decides
*what that vocabulary means*. Ranking semantics stay in a reviewed file under
test rather than in whatever the model felt like weighting on a given call. It
also means the schema and the prompt are both generated from the live dictionary
and enums, so adding a concept updates the contract without anyone remembering
to edit a prompt string.

`LlmParser` is the trust boundary: enum values are re-parsed defensively, and an
unknown concept key becomes a user-visible warning rather than a silent drop.

### The model replaces the rules result; it does not merge with it

When Gemini answers, its spec is used wholesale. Merging was considered and
rejected: two specs disagreeing about the same bound has no principled
resolution, and "whichever parser ran" is far easier to reproduce from a log
than "whichever half of each". The one exception is a defensive guard — a blank
model result never displaces a non-blank rules result.

### Caching parses, not results

```
normalise(query) → FilterSpec,  Caffeine, 6h TTL, 10k entries
```

Inventory changes hourly; the *meaning* of "diesel automatic under 80k km" does
not. Caching parses is therefore safe with a long TTL, while caching result sets
would go stale and eventually show a sold car.

One subtlety worth recording: Spring's cache abstraction unwraps `Optional`
return values, so `#result` in a `@Cacheable` condition is the unwrapped value
and is `null` for an empty `Optional`. The natural-looking
`unless = "#result.isEmpty()"` throws on every failed parse.

### Two independent cost ceilings

A public endpoint with a paid model behind it is a wallet-drain vector, and the
cheapest attack is a loop over unique query strings — every one a cache miss,
every one a call.

- `GeminiClient` enforces a **daily call budget**. Past it, rules serve.
- `RateLimitFilter` enforces a **per-client rate**, so one caller cannot exhaust
  the day's allowance before anyone else arrives.

The two are independent on purpose: the first caps total spend, the second caps
how fast any one party can consume it.

### Postgres, not Elasticsearch

Elasticsearch is what this runs on at marketplace scale — millions of listings,
aggregation-heavy faceting, relevance tuned by non-engineers. It was priced and
declined here:

- At ~600 documents, a sequential scan with a computed score completes faster
  than the network hop to a search cluster.
- It adds a second stateful service and a second failure domain to a demo that a
  reviewer opens once.
- `pg_trgm` covers typo tolerance (`word_similarity`, not `similarity` — the
  latter normalises over the whole string and scores a short query against a long
  title near zero). `COUNT(*) FILTER` covers faceting in one round trip.
- The one genuine loss, analyzer-level synonyms, is not a loss: the concept
  dictionary already does that job, earlier in the pipeline and under test.

**Migration trigger:** roughly 100k listings, or when faceting needs
per-dimension drill-down passes. `QueryCompiler` is the only class that turns a
`FilterSpec` into a query, so that migration replaces one file.

### The contract is generated, not transcribed

springdoc publishes the OpenAPI document from the controller and its records,
and `openapi-typescript` turns that into the frontend's types. Renaming a field
in a Java record breaks the frontend build at the call site rather than
surfacing as `undefined` inside a component at runtime.

That closes the loop on the claim above: the `FilterSpec` the language path
produces and the one the chip editor submits are the same type, checked by two
compilers, end to end.

`GET /api/v1/schema` serves the other half — enum values, live min/max ranges,
and the concept vocabulary. The UI builds its sort control from it instead of
hardcoding the enum, and the same concept list is what the model is given, which
is what keeps the prompt and the dictionary from drifting apart.

Generating the document also caught a leak: `isEmpty`, `isUnsatisfiable` and
`isBlank` were being serialised into every response as `empty`, `unsatisfiable`
and `blank`. Internal helpers had become part of the published contract without
anyone deciding they should be.

### JDBC, not JPA

One table, hand-written ranking SQL, no object graph. The search path would
bypass an ORM's mapping layer entirely, so it is not carrying its weight.

### Guardrails are not the parser's business

`status = 'AVAILABLE'` is injected server-side, after parsing, on every query —
rows, count, and facets alike, asserted by `QueryCompilerTest`. Nothing a user
types and nothing a model emits can widen it. Showing a sold car is the failure
that actually costs money; a mis-parsed price is merely annoying.

Every literal is a bound parameter and every column name comes from an enum, so
`PreferenceField` is the injection boundary: a model emits a name, Jackson either
resolves it to a constant or the request is rejected.

## Testing

`RulesParserGoldenTest` runs 28 natural-language queries against expected specs
with no database and no network. It is the regression net for vocabulary
changes, and when the LLM path lands it will be graded against the same file —
which is what makes the two parsers comparable rather than merely both present.

It earned its place immediately. Three real bugs it caught during the build:

1. **`"below 50000 km"` parsed as a ₹5 crore price bound.** The magnitude-suffix
   alternation claimed the `k` of `km` and left a stray `m`, so the distance
   bound became a price bound 1000× too large. Fixed with a `(?![a-z])`
   lookahead that forces backtracking to the no-suffix branch.
2. **`"between 8 and 12 lakh"` produced a lower bound of 8 rupees.** In Indian
   usage the magnitude is stated once, after the second number, and applies to
   both.
3. **`"2019 model creta"`** searched free-text for `"model creta"`.

All three produce plausible-looking output — empty or oddly-filtered result sets
that read as thin inventory rather than as parse failures. None would have been
caught by an integration test asserting HTTP 200.

## Grading both parsers against one file

`GoldenSet` grades the rules parser and Gemini with the same code against the
same expectations. That is not a testing convenience — it is what turns "the
model handles the tail better" into a number, and it is what caught a real bug
that neither parser would have exposed alone.

The measured baseline was rules 28/28, Gemini 24/28. Three of the model's four
misses were defects in the golden set, not the model: a concept that mapped
"6 seater" and "7 seater" to the same filter, a prompt that never said bounds
are inclusive, and a sort enum with two defensible readings of "newest". A suite
that only ever grades the parser it was written for cannot tell you your
specification is ambiguous; it can only confirm your code matches it.
Full analysis in [EVALUATION.md](EVALUATION.md).

### Free-tier quota shapes the design, not just the bill

The Gemini free tier allows **20 requests per day, per model**, and reports the
overflow as `503 "high demand"` for several minutes before admitting to
`429 RESOURCE_EXHAUSTED`. Read literally the first response blames the provider;
it means the client is over quota.

Two consequences are baked into the code. `GeminiClient` counts quota failures
separately from transport failures, so a billing wall is never mistaken for a
model that parses badly. And it retries a 503 but never a 429 — congestion
clears in milliseconds, a quota window does not, and the rules parser already
has an answer ready.

### Two bugs the live model found that no test did

Both were exposed only by pointing a real model at real data, and both had
passing tests either side of them.

**A city that does not exist.** The model parsed "…in bangalore" correctly and
the SQL was correct, but the catalogue stores `Bengaluru` and `IN` is
case-sensitive. Zero results — presented to the user as "no stock in your city"
rather than "we did not recognise your city", which is the worse of the two
because they leave believing the inventory is thin. `Cities` now canonicalises
at the compile step, covering the renamed cities half the country still uses
(Bombay, Calcutta, Gurgaon) and the airport codes people type. It sits in the
compiler rather than either parser so the chip-edit path is covered too.

**An electric car doing 48.9 km/l.** Seeding electrics with a "petrol
equivalent" kmpl produced numbers that are not merely wrong but obviously
absurd, and a reader who spots one stops trusting every other figure on the
page. The honest model is that the column does not apply: `mileage_kmpl` is now
nullable and null for electrics, which carry `range_km` instead, with a CHECK
constraint asserting exactly one of the two is set. The migration backfills
before adding that constraint, so it is safe against a database that already
holds rows rather than only against a fresh one.

Neither is the sort of thing a unit test finds, because every component was
individually correct. That is the argument for exercising the real path against
real data before calling something done.

### CORS is an allowlist, and preflight is not rate limited

A split deployment puts the frontend on a different origin from the API, so the
API has to grant access explicitly. Two details are load-bearing:

The allowlist is never a wildcard. This endpoint has a paid model and a spend
budget behind it, and `*` would mean any page on the internet may spend it from
a visitor's browser.

CORS preflight is exempt from rate limiting. A preflight reaches no model and
costs nothing, so counting it would halve every browser client's real budget —
and answering one with 429 fails the preflight itself, which the browser
surfaces as an opaque CORS error rather than as rate limiting. That failure mode
is very hard to diagnose from the client side.

## Known limitations
- **Facet counts are post-filter.** They describe the current result set, not
  what each alternative would return. Proper drill-down faceting needs one pass
  per dimension with that dimension's own predicate removed.
- **Chip edits are not in the URL.** The query string is shareable; a spec
  refined by removing chips is session state.
- **Ranking weights are hand-set**, not learned. At scale these come from
  booking outcomes via learning-to-rank; here they are tunable config.
- **Preferences score as binary steps**, not ramps. A four-star and a five-star
  car score identically on safety. Deliberate — it keeps the YAML and the SQL
  saying the same thing — but it is a real fidelity limit.
- **The rate limiter is in-memory and per-instance.** It resets on restart and
  keys on `X-Forwarded-For`, which is client-supplied and spoofable. Adequate for
  cost control on one box; not a security control.
- **The prompt is not versioned.** Editing it changes behaviour with no record
  beyond git history. At scale the prompt and its golden-set score belong
  together as a versioned artifact.
- **The golden set is curated, not sampled.** 29 hand-written queries are a
  regression net, not a picture of real demand. The production version is mined
  from query logs and weighted by frequency.
- **Only parsing is graded.** Whether the ranking is *good* needs click and
  booking data.
- **Generated frontend types are optional-everywhere.** springdoc marks record
  components optional because Java records carry no nullability metadata, and
  springdoc 3.1 does not read the JSpecify annotations Spring itself uses. The
  response envelope is narrowed once in `api.ts`; the proper fix is
  `@Schema(requiredMode = REQUIRED)` on the response records.

## What changes at scale

| Concern | Now | At 100k listings |
|---|---|---|
| Retrieval | Postgres seq scan | Elasticsearch, `filter` + `function_score` |
| Indexing | Seed on startup | CDC via Debezium → Kafka → indexer |
| Ranking | Hand-set weights | Learning-to-rank on booking events |
| Understanding | Rules + LLM tail | LLM distilled into a local NER model |
| Evaluation | Golden set | Null-result rate, CTR@k, online A/B |
