import { titleCase } from '../api'

const HEADINGS: Record<string, string> = {
  bodyType: 'Body', fuelType: 'Fuel', transmission: 'Gearbox',
}

/** Counts describe the current results, not what each alternative would return. */
export function FacetPanel({ facets }: { facets: Record<string, Record<string, number>> }) {
  const groups = Object.entries(facets).filter(([, values]) => Object.keys(values).length > 0)
  if (groups.length === 0) return null

  return (
    <aside className="space-y-6">
      {groups.map(([dimension, values]) => (
        <section key={dimension}>
          <h2 className="mb-2 text-[11px] font-semibold tracking-[0.08em] text-muted uppercase">
            {HEADINGS[dimension] ?? dimension}
          </h2>
          <ul className="space-y-1">
            {Object.entries(values).map(([value, count]) => (
              <li key={value} className="flex items-baseline justify-between gap-3 text-[13px]">
                <span>{titleCase(value)}</span>
                <span className="tabular text-muted">{count}</span>
              </li>
            ))}
          </ul>
        </section>
      ))}
    </aside>
  )
}
