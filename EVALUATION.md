# Evaluation

Both parsers are graded by the same code against the same file
(`backend/src/test/resources/eval/queries.json`), so "the model is better than
the rules" is a measurement rather than an opinion.

```bash
GEMINI_API_KEY=... GEMINI_MODEL=gemini-3.5-flash-lite \
  ./mvnw test -DexcludedGroups= -Dtest=GeminiLiveEvalTest

# After a prompt edit, re-check a few queries instead of paying for all of them:
EVAL_ONLY='before 2018|newest cars' ./mvnw test -DexcludedGroups= -Dtest=GeminiLiveEvalTest
```

## Run 1 — baseline

`gemini-3.5-flash-lite`, 28 queries, 2026-09-09.

| Parser | Score |
|---|---|
| Rules | **28/28 (100%)** |
| Gemini | **24/28 (86%)** |
| Unanswered | 0 |

The rules parser scoring 100% is not the flex it looks like — the golden set was
written alongside it, so it encodes its conventions. That bias is exactly what
made the model's four "failures" worth reading, because three of them turned out
to be defects in the specification rather than in the model.

### The four disagreements

**1. `"7 seater diesel"` — model said `seats ≥ 6`, expected `≥ 7`. My bug.**

One concept, `seven_seater`, listed both `7 seater` and `6 seater` as terms and
mapped both to `seats >= 6`. Two different user intents collapsed into one
filter. The rules parser hid this behind a separate numeric `(\d) seater`
pattern that produced 7 and won the merge, so the ambiguity was invisible until
a second parser read the same vocabulary and reached a different answer.

Fixed by splitting `seven_seater` (`>= 7`) from `six_seater` (`>= 6`).

**2. `"cars before 2018"` — model said `year ≤ 2017`, expected `≤ 2018`. Prompt gap.**

The model read "before" as exclusive, which is defensible English. Every bound
in `NumRange` is inclusive, and the prompt never said so. Fixed by stating it,
with a worked example.

**3. `"newest cars first"` — model said `YEAR_DESC`, expected `NEWEST_LISTED`. Ambiguous.**

"Newest" can mean newest model year or most recently listed. Both are valid
readings and both are in the sort enum; the golden set simply asserted one. The
prompt now defines what each sort value means rather than leaving the model to
guess which sense a marketplace intends.

**4. `"purple flying spaceship"` — model returned `freeText: null`. The model was right.**

The rules parser dumps whatever it could not consume into `freeText`, which then
runs a trigram search for a model called "purple flying spaceship". The model
declined and put the words in `unmapped` instead, which is the better behaviour:
it produces an honest "we did not understand this" rather than an empty result
set that looks like missing inventory.

The expectation was relaxed, and the prompt now states that `freeText` is for
make and model names only. The two parsers still differ here, deliberately —
see Known divergences.

## Run 2 — after the fixes

Targeted re-check of the five affected queries (`EVAL_ONLY`):

| Query | Rules | Gemini |
|---|---|---|
| `7 seater diesel` | pass | pass |
| `6 seater mpv` *(new case)* | pass | pass |
| `cars before 2018` | pass | pass |
| `newest cars first` | pass | pass |
| `purple flying spaceship` | pass | pass |

Then a full graded re-run to confirm the prompt edits regressed nothing — the
real risk with prompt changes, and the reason the whole set is worth re-running
rather than only the queries you touched:

| Parser | Score |
|---|---|
| Rules | **29/29 (100%)** |
| Gemini | **29/29 (100%)** |
| Unanswered | 0 |

Read that with the caveat it deserves. 29 curated queries is a regression net,
not evidence of general accuracy, and the set was written alongside the rules
parser. What the run establishes is narrower and still useful: the four fixes
work, nothing else broke, and the two parsers now agree on every case they are
both asked about.

## What this exercise actually bought

The score is the least interesting output. The run produced:

- **One real product bug** (#1) that no amount of clicking around would have
  surfaced, because the rules parser masked it.
- **Two specification gaps** (#2, #3) where the prompt was underspecified and I
  had mistaken my own convention for an obvious one.
- **One case where the model out-designed the golden set** (#4).

That is the argument for grading both paths against one file. A test suite that
only ever checks the parser it was written for cannot tell you that your
specification is ambiguous — it can only tell you that your code matches it.

## Known divergences

These are accepted, not bugs:

- **Unrecognised wording.** The rules parser routes leftovers to `freeText`; the
  model routes them to `unmapped`. The model's behaviour is better, but the
  rules parser has no way to tell a misspelled model name from nonsense, and
  guessing wrong in the other direction — dropping "hundai creta" — is worse.
- **Concept coverage.** The rules parser matches concept terms literally. The
  model generalises ("something for the family" → `family`), which is the whole
  reason the tail escalates to it.

## Operational findings

- **The free tier is 20 requests per day, per model.** A 28-query burst
  exhausts it immediately, and the API reports the overflow as
  `503 "high demand"` for some minutes before it admits to
  `429 RESOURCE_EXHAUSTED`. Read literally, the first response says the model is
  busy; it means your client is over quota. This is why the client counts quota
  failures separately from transport failures — otherwise a billing problem
  reads as a bad model.
- **Quota is per-model**, so exhausting `gemini-3.8-flash` leaves
  `gemini-3.5-flash-lite` usable. The default is the lite model: this task is
  extraction, not reasoning, and it scored the same.
- **Pacing dominates runtime.** A full graded run is ~15 minutes at 3–4s
  spacing. `EVAL_ONLY` exists because a prompt edit should not cost 15 minutes
  to check.

## What is not measured here

- **Ranking quality.** The golden set grades parsing only. Whether the returned
  order is *good* needs click and booking data, which a take-home does not have.
- **Latency and cost per query** under real traffic, where the cache hit rate
  would dominate both.
- **Tail queries.** 29 curated queries are a regression net, not a sample of
  real demand. The real version of this file is mined from production query
  logs, weighted by frequency, and refreshed as the vocabulary drifts.
