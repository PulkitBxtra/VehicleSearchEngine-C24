/**
 * Mirrors the backend contract. In the finished repo these are generated from
 * springdoc's /v3/api-docs with openapi-typescript, so the two sides cannot
 * drift; they are handwritten here only until that step is wired up.
 */
export type NumRange = { gte: number | null; lte: number | null }

export type Constraints = {
  makes: string[]
  bodyTypes: string[]
  fuelTypes: string[]
  transmissions: string[]
  cities: string[]
  priceInr: NumRange | null
  emiMonthly: NumRange | null
  kmDriven: NumRange | null
  year: NumRange | null
  seats: NumRange | null
  maxOwners: number | null
  minNcapStars: number | null
}

export type Preference = {
  field: string; op: string; value: number; boost: number; source: string | null
}

export type FilterSpec = {
  constraints: Constraints
  preferences: Preference[]
  freeText: string | null
  sort: string
  appliedConcepts: string[]
  unmapped: string[]
}

/** CONSTRAINT chips removed vehicles; PREFERENCE chips only changed the order. */
export type Chip = {
  kind: 'CONSTRAINT' | 'PREFERENCE'
  field: string
  label: string
  source: string | null
}

export type Vehicle = {
  id: number; registration: string
  make: string; model: string; variant: string; year: number
  bodyType: string; fuelType: string; transmission: string
  priceInr: number; emiMonthly: number; kmDriven: number
  owners: number; seats: number; engineCc: number
  mileageKmpl: number; bootLitres: number; ncapStars: number | null
  city: string; hub: string; colour: string
  status: string; listedAt: string
  inspectionScore: number; dealScore: number
  features: string[]; score: number | null
}

export type SearchResponse = {
  interpretation: { chips: Chip[]; notes: string[] }
  filters: FilterSpec
  results: Vehicle[]
  totalElements: number
  page: number
  size: number
  facets: Record<string, Record<string, number>>
  parser: string
  tookMs: number
  warnings: string[]
  scoreSql: string | null
}
