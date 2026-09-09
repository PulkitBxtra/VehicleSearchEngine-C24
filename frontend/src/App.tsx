import { useEffect, useState } from 'react'
import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { fetchSchema, search } from './api'
import type { Chip, Constraints, FilterSpec } from './types'
import { ChipStrip } from './components/ChipStrip'
import { VehicleCard } from './components/VehicleCard'
import { FacetPanel } from './components/FacetPanel'
import { Pagination, SortSelect } from './components/ResultControls'

const PAGE_SIZE = 24

const EXAMPLES = [
  'SUVs under ₹15L',
  'Diesel automatic cars below 80k km',
  'Family cars with high safety ratings',
  '7 seater under 12 lakh first owner',
  'cars with emi under 15000',
]

export default function App() {
  const [draft, setDraft] = useState(() => new URLSearchParams(location.search).get('q') ?? '')
  const [query, setQuery] = useState(draft)
  // Any refinement — a removed chip, a sort change, a page turn — produces a
  // spec directly. Holding it here means refining a search never re-parses the
  // sentence, so it costs no model call however many times it is adjusted.
  const [spec, setSpec] = useState<FilterSpec | null>(null)
  const [page, setPage] = useState(0)

  // The catalogue describes its own filterable surface; controls are built from
  // that rather than from hardcoded enums.
  const { data: schema } = useQuery({
    queryKey: ['schema'],
    queryFn: fetchSchema,
    staleTime: Infinity,
  })

  useEffect(() => {
    const url = new URL(location.href)
    query ? url.searchParams.set('q', query) : url.searchParams.delete('q')
    history.replaceState(null, '', url)
  }, [query])

  const { data, isFetching, error } = useQuery({
    queryKey: ['search', query, spec, page],
    queryFn: () =>
      search(spec ? { filters: spec, page, size: PAGE_SIZE } : { query, page, size: PAGE_SIZE }),
    enabled: query.trim().length > 0 || spec !== null,
    // Keep the current page visible while the next one loads, so turning a page
    // does not collapse the layout and jump the scroll position.
    placeholderData: keepPreviousData,
  })

  function runQuery(next: string) {
    setDraft(next); setQuery(next); setSpec(null); setPage(0)
  }

  function removeChip(chip: Chip) {
    if (!data) return
    setSpec(withoutChip(data.filters, chip)); setPage(0)
  }

  function changeSort(next: string) {
    if (!data) return
    setSpec({ ...(spec ?? data.filters), sort: next as FilterSpec['sort'] })
    setPage(0)
  }

  function goToPage(next: number) {
    // Pin the parsed spec before turning the page. Without this, page two would
    // re-submit the original sentence and parse it again — a second model call
    // for a result the first one already produced.
    if (!spec && data) setSpec(data.filters)
    setPage(next)
    window.scrollTo({ top: 0, behavior: 'smooth' })
  }

  return (
    <div className="min-h-screen">
      <header className="border-b border-rule">
        <div className="mx-auto flex max-w-5xl items-baseline justify-between px-5 py-3">
          <span className="font-display text-[15px] font-bold tracking-tight">
            vehicle<span className="text-pine">search</span>
          </span>
          <span className="text-[11px] text-muted">Used car catalogue · India</span>
        </div>
      </header>

      <main className="mx-auto max-w-5xl px-5 py-8">
        <h1 className="font-display text-[26px] leading-tight font-medium sm:text-[32px]">
          Describe the car you want.
        </h1>
        <p className="mt-1.5 max-w-xl text-[14px] text-muted">
          Plain sentences work. Every filter it applies is shown below, and you can take any of
          them back.
        </p>

        <form
          className="mt-5 flex gap-2"
          onSubmit={(e) => { e.preventDefault(); runQuery(draft) }}
        >
          <input
            value={draft}
            onChange={(e) => setDraft(e.target.value)}
            placeholder="diesel automatic under 80k km"
            aria-label="Search the catalogue"
            className="tabular w-full rounded-md border border-rule bg-card px-3.5 py-2.5 text-[14px]
                       placeholder:text-muted/60 focus:border-ink focus:outline-none"
          />
          <button
            type="submit"
            className="shrink-0 rounded-md bg-ink px-5 text-[14px] font-medium text-white
                       transition-colors hover:bg-ink/85 focus:outline-none focus-visible:ring-2
                       focus-visible:ring-pine focus-visible:ring-offset-2"
          >
            Search
          </button>
        </form>

        {!query && !spec && (
          <div className="mt-3 flex flex-wrap items-center gap-x-2 gap-y-1.5 text-[12.5px]">
            <span className="text-muted">Try</span>
            {EXAMPLES.map((ex) => (
              <button
                key={ex}
                onClick={() => runQuery(ex)}
                className="rounded border border-rule bg-card px-2 py-0.5 text-muted
                           transition-colors hover:border-ink hover:text-ink"
              >
                {ex}
              </button>
            ))}
          </div>
        )}

        {error && (
          <p className="mt-6 rounded-md border border-rust/40 bg-rust/5 px-3.5 py-2.5 text-[13px] text-rust">
            {(error as Error).message}. Check that the API is running on port 8080.
          </p>
        )}

        {data && (
          <>
            <section className="mt-7 border-t border-rule pt-5">
              <h2 className="mb-2.5 text-[11px] font-semibold tracking-[0.08em] text-muted uppercase">
                Understood as
              </h2>
              {data.interpretation.chips.length > 0 ? (
                <ChipStrip chips={data.interpretation.chips} onRemove={removeChip} />
              ) : (
                <p className="text-[13px] text-muted">
                  No filters were extracted — showing the whole catalogue.
                </p>
              )}

              <div className="mt-3 flex flex-wrap items-center gap-x-2 text-[12px] text-muted">
                {/* Solid narrowed, dashed reordered — the legend for the chips above. */}
                <span className="inline-flex items-center gap-1.5">
                  <span className="h-2.5 w-2.5 rounded-full bg-ink" /> narrowed results
                </span>
                <span className="inline-flex items-center gap-1.5">
                  <span className="h-2.5 w-2.5 rounded-full border border-dashed border-pine" />
                  changed the order only
                </span>
              </div>

              {data.interpretation.notes.map((n) => (
                <p key={n} className="mt-2 text-[12.5px] text-muted">{n}</p>
              ))}
              {data.warnings.map((w) => (
                <p key={w} className="mt-2 text-[12.5px] text-rust">{w}</p>
              ))}
            </section>

            <div className="mt-5 flex flex-wrap items-center justify-between gap-3">
              <p className="text-[12.5px] text-muted">
                <span className="tabular font-medium text-ink">
                  {data.totalElements.toLocaleString('en-IN')}
                </span>{' '}
                {data.totalElements === 1 ? 'match' : 'matches'} ·{' '}
                <span className="tabular">{data.tookMs}ms</span> ·{' '}
                {data.parser.toLowerCase()} parser
                {isFetching && ' · updating…'}
              </p>
              {data.totalElements > 0 && (
                <SortSelect
                  sorts={schema?.sorts ?? []}
                  value={data.filters.sort ?? 'RELEVANCE'}
                  onChange={changeSort}
                />
              )}
            </div>

            <div className="mt-5 grid gap-8 md:grid-cols-[160px_1fr]">
              <FacetPanel facets={data.facets} />
              {data.results.length > 0 ? (
                <div>
                  <div className="grid gap-3 sm:grid-cols-2">
                    {data.results.map((v) => <VehicleCard key={v.id} v={v} />)}
                  </div>
                  <Pagination
                    page={data.page}
                    size={data.size}
                    total={data.totalElements}
                    onPage={goToPage}
                  />
                </div>
              ) : (
                <p className="text-[14px] text-muted">
                  Nothing matched. Remove a filter above, or widen the price or distance limit.
                </p>
              )}
            </div>
          </>
        )}
      </main>
    </div>
  )
}

/**
 * Clearing one chip is a mechanical edit to the spec, which is only possible
 * because the chip carries the field path it came from.
 */
function withoutChip(spec: FilterSpec, chip: Chip): FilterSpec {
  const next: FilterSpec = structuredClone(spec)

  if (chip.field === 'freeText') { next.freeText = undefined; return next }

  if (chip.kind === 'PREFERENCE') {
    next.preferences = (next.preferences ?? []).filter((p) => p.source !== chip.source)
    next.appliedConcepts = (next.appliedConcepts ?? []).filter((c) => c !== chip.source)
    return next
  }

  if (!next.constraints) return next
  const key = chip.field.replace('constraints.', '') as keyof Constraints
  const current = next.constraints[key]
  // Categorical constraints reset to an empty list, scalar and range ones to null.
  ;(next.constraints as Record<string, unknown>)[key] = Array.isArray(current) ? [] : null
  return next
}
