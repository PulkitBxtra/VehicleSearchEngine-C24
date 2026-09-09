import type { Chip } from '../types'

/**
 * The thesis of the whole product.
 *
 * A solid chip removed vehicles from the results. A dashed chip only moved them
 * up the list. Users routinely assume a search for "high safety" excluded
 * everything unsafe; showing the difference is what stops the ranking from
 * feeling arbitrary — and each chip is removable, which re-runs the search from
 * the filter spec with no language parsing at all.
 */
export function ChipStrip({
  chips, onRemove,
}: {
  chips: Chip[]
  onRemove: (chip: Chip) => void
}) {
  if (chips.length === 0) return null

  return (
    <div className="flex flex-wrap items-center gap-2">
      {chips.map((chip, i) => {
        const narrowed = chip.kind === 'CONSTRAINT'
        return (
          <button
            key={`${chip.field}-${i}`}
            onClick={() => onRemove(chip)}
            style={{ animationDelay: `${i * 25}ms` }}
            className={[
              'chip-in group inline-flex items-center gap-1.5 rounded-full px-3 py-1',
              'text-[13px] transition-colors focus:outline-none focus-visible:ring-2',
              'focus-visible:ring-pine focus-visible:ring-offset-2 focus-visible:ring-offset-paper',
              narrowed
                ? 'bg-ink text-white hover:bg-ink/85'
                : 'border border-dashed border-pine text-pine hover:bg-pine/8',
            ].join(' ')}
            title={narrowed
              ? 'Narrowed the results. Click to remove.'
              : 'Only changed the order — nothing was excluded. Click to remove.'}
          >
            <span>{chip.label}</span>
            <span className="opacity-45 transition-opacity group-hover:opacity-100">×</span>
          </button>
        )
      })}
    </div>
  )
}
