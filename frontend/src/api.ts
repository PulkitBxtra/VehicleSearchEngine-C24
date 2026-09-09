import type { FilterSpec, SearchResponse, SearchResult } from './types'

export async function search(body: {
  query?: string
  filters?: FilterSpec
  page?: number
  size?: number
}): Promise<SearchResult> {
  const res = await fetch('/api/v1/search', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })
  if (res.status === 429) {
    throw new Error('Too many searches in a short time. Wait a moment and try again')
  }
  if (!res.ok) throw new Error(`Search failed (${res.status})`)

  return narrow(await res.json())
}

/**
 * The one place the generated-optional response becomes the shape the UI relies
 * on. Defaults are applied rather than asserted, so a genuinely missing field
 * renders an empty section instead of throwing inside a component.
 */
function narrow(raw: SearchResponse): SearchResult {
  return {
    interpretation: {
      chips: (raw.interpretation?.chips ?? []) as SearchResult['interpretation']['chips'],
      notes: raw.interpretation?.notes ?? [],
    },
    filters: raw.filters ?? {},
    results: raw.results ?? [],
    totalElements: raw.totalElements ?? 0,
    page: raw.page ?? 0,
    size: raw.size ?? 0,
    facets: raw.facets ?? {},
    parser: raw.parser ?? 'UNKNOWN',
    tookMs: raw.tookMs ?? 0,
    warnings: raw.warnings ?? [],
    scoreSql: raw.scoreSql,
  }
}

/** ₹ in lakh and crore, the way Indian listings are written. */
export function rupees(n: number): string {
  if (n >= 10_000_000) return `₹${(n / 10_000_000).toFixed(2).replace(/\.?0+$/, '')} Cr`
  if (n >= 100_000) return `₹${(n / 100_000).toFixed(2).replace(/\.?0+$/, '')}L`
  return `₹${n.toLocaleString('en-IN')}`
}

export function titleCase(s: string): string {
  return s.replace(/_/g, ' ').toLowerCase().replace(/\b\w/g, (c) => c.toUpperCase())
}
