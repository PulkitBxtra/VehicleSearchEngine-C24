const SORT_LABELS: Record<string, string> = {
  RELEVANCE: 'Best match',
  PRICE_ASC: 'Price: low to high',
  PRICE_DESC: 'Price: high to low',
  KM_ASC: 'Least driven',
  YEAR_DESC: 'Newest model year',
  NEWEST_LISTED: 'Recently listed',
}

/**
 * Sort options come from /api/v1/schema, not a hardcoded list, so a sort added
 * to the backend enum appears here without a frontend change.
 */
export function SortSelect({
  sorts, value, onChange,
}: {
  sorts: string[]
  value: string
  onChange: (next: string) => void
}) {
  if (sorts.length === 0) return null
  return (
    <label className="flex items-center gap-2 text-[12.5px] text-muted">
      Sort
      <select
        value={value}
        onChange={(e) => onChange(e.target.value)}
        className="rounded border border-rule bg-card px-2 py-1 text-[12.5px] text-ink
                   focus:border-ink focus:outline-none"
      >
        {sorts.map((s) => (
          <option key={s} value={s}>{SORT_LABELS[s] ?? s}</option>
        ))}
      </select>
    </label>
  )
}

export function Pagination({
  page, size, total, onPage,
}: {
  page: number
  size: number
  total: number
  onPage: (next: number) => void
}) {
  const pages = Math.ceil(total / size)
  if (pages <= 1) return null

  const first = page * size + 1
  const last = Math.min((page + 1) * size, total)

  return (
    <nav className="mt-6 flex items-center justify-between border-t border-rule pt-4"
         aria-label="Results pages">
      <p className="tabular text-[12.5px] text-muted">
        {first}–{last} of {total.toLocaleString('en-IN')}
      </p>
      <div className="flex items-center gap-2">
        <PageButton disabled={page === 0} onClick={() => onPage(page - 1)}>Previous</PageButton>
        <span className="tabular text-[12.5px] text-muted">
          {page + 1} / {pages}
        </span>
        <PageButton disabled={page + 1 >= pages} onClick={() => onPage(page + 1)}>Next</PageButton>
      </div>
    </nav>
  )
}

function PageButton({
  disabled, onClick, children,
}: {
  disabled: boolean
  onClick: () => void
  children: string
}) {
  return (
    <button
      onClick={onClick}
      disabled={disabled}
      className="rounded border border-rule bg-card px-2.5 py-1 text-[12.5px]
                 transition-colors enabled:hover:border-ink disabled:opacity-40
                 focus:outline-none focus-visible:ring-2 focus-visible:ring-pine"
    >
      {children}
    </button>
  )
}
