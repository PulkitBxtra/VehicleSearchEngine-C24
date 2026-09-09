import type { Vehicle } from '../types'
import { rupees, titleCase } from '../api'

const GEARBOX: Record<string, string> = {
  MANUAL: 'Manual', AMT: 'AMT', CVT: 'CVT', DCT: 'DCT', TORQUE_CONVERTER: 'Automatic',
}

export function VehicleCard({ v }: { v: Vehicle }) {
  // Priced meaningfully below comparable stock of the same model and year.
  const goodDeal = (v.dealScore ?? 0) >= 0.06

  return (
    <article className="group rounded-lg border border-rule bg-card p-4 transition-colors hover:border-ink/35">
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <h3 className="font-display text-[15px] leading-tight font-medium">
            <span className="tabular text-muted">{v.year}</span>{' '}
            {v.make} {v.model}
          </h3>
          <p className="mt-0.5 truncate text-[13px] text-muted">{v.variant} · {v.colour}</p>
        </div>
        {/* Indian plates are their own visual object; treating one as a label
            makes each listing feel like a specific car rather than a row. */}
        <span className="tabular shrink-0 rounded border-2 border-ink px-1.5 py-0.5 text-[10px] font-semibold tracking-wide">
          {v.registration}
        </span>
      </div>

      <div className="mt-3 flex items-baseline gap-2.5">
        <span className="tabular text-[22px] font-semibold">{rupees(v.priceInr ?? 0)}</span>
        <span className="tabular text-[12px] text-muted">
          ₹{(v.emiMonthly ?? 0).toLocaleString('en-IN')}/mo
        </span>
        {goodDeal && (
          <span className="ml-auto rounded-sm bg-rust/10 px-1.5 py-0.5 text-[10px] font-semibold tracking-wide text-rust uppercase">
            Below market
          </span>
        )}
      </div>

      <dl className="mt-3 grid grid-cols-2 gap-x-4 gap-y-1 border-t border-rule pt-3 text-[12px]">
        <Row label="Driven" value={`${(v.kmDriven ?? 0).toLocaleString('en-IN')} km`} />
        <Row label="Fuel" value={titleCase(v.fuelType ?? '')} />
        <Row label="Gearbox" value={(v.transmission && GEARBOX[v.transmission]) || v.transmission || '—'} />
        <Row label="Owners" value={v.owners === 1 ? 'First' : `${v.owners ?? '—'}`} />
        <Row label="Safety" value={v.ncapStars ? `${v.ncapStars}★ NCAP` : 'Not rated'} />
        <Row label="Seats" value={`${v.seats ?? '—'}`} />
        {/* Electrics have no km/l. Showing range instead of a converted figure
            keeps the number meaningful rather than merely present. */}
        <Row label={v.rangeKm ? 'Range' : 'Mileage'} value={economy(v)} />
        <Row label="Boot" value={v.bootLitres ? `${v.bootLitres} L` : '—'} />
      </dl>

      <p className="mt-3 text-[11px] text-muted">
        {v.city} · Inspected <span className="tabular">{v.inspectionScore}</span>/10
      </p>
    </article>
  )
}

function economy(v: Vehicle): string {
  if (v.rangeKm) return `${v.rangeKm} km`
  return v.mileageKmpl ? `${v.mileageKmpl} kmpl` : '—'
}

function Row({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex items-baseline justify-between gap-2">
      <dt className="text-muted">{label}</dt>
      <dd className="tabular text-right">{value}</dd>
    </div>
  )
}
