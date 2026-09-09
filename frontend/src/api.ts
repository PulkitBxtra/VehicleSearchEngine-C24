import type { FilterSpec, SearchResponse } from './types'

export async function search(body: {
  query?: string
  filters?: FilterSpec
  page?: number
  size?: number
}): Promise<SearchResponse> {
  const res = await fetch('/api/v1/search', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })
  if (!res.ok) throw new Error(`Search failed (${res.status})`)
  return res.json()
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
