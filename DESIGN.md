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
most traffic at zero latency and zero cost. The plan is:

```
rules → cache → LLM (tail only) → rules result as floor on failure
```

An LLM outage degrades the service rather than stopping it. This also keeps a
public deployment from being a wallet-drain vector.

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

## Known limitations

- **The LLM path is not implemented.** Language queries the rules do not cover
  fall through to a trigram search on make/model. `parser` reports `RULES`.
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
- **`mileage_kmpl` for electric vehicles** is a petrol-equivalent figure, since
  the column has no meaning for an EV.
- **No rate limiting yet.** Required before the LLM path is exposed publicly.

## What changes at scale

| Concern | Now | At 100k listings |
|---|---|---|
| Retrieval | Postgres seq scan | Elasticsearch, `filter` + `function_score` |
| Indexing | Seed on startup | CDC via Debezium → Kafka → indexer |
| Ranking | Hand-set weights | Learning-to-rank on booking events |
| Understanding | Rules + LLM tail | LLM distilled into a local NER model |
| Evaluation | Golden set | Null-result rate, CTR@k, online A/B |
