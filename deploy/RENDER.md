# Deploying to Render + Neon

Two services on Render — a static frontend and a Dockerised API — with Postgres
on Neon. See [`README.md`](README.md) for the single-VPS alternative.

`render.yaml` at the repository root declares both. Point Render at the repo as
a Blueprint and it creates them together, prompting for each secret marked
`sync: false`.

## 1. Neon

Create a project and copy the connection string. It arrives in libpq form:

```
postgresql://USER:PASSWORD@ep-xxx.region.aws.neon.tech/neondb?sslmode=require&channel_binding=require
```

Split it into the three variables Spring expects, and **drop
`channel_binding`** — that is a libpq option and the PostgreSQL JDBC driver
rejects it:

```
DB_URL=jdbc:postgresql://ep-xxx.region.aws.neon.tech/neondb?sslmode=require
DB_USER=USER
DB_PASSWORD=PASSWORD
```

Nothing else is needed. Flyway creates the schema on first boot, including the
`pg_trgm` extension, and the seeder writes 600 vehicles once and skips
thereafter.

## 2. API service

Docker runtime, `dockerfilePath: ./backend/Dockerfile`, context `./backend`,
health check `/actuator/health`. Both paths are relative to the repository
root.

**Put it in the same region as Neon.** A cross-region hop costs more than every
query this service runs — against Singapore from outside the region, searches
measured 270–383ms versus 10–30ms locally.

Environment: the three database variables above, plus `GEMINI_API_KEY` and
`CORS_ALLOWED_ORIGINS`.

## 3. Static site

Build `cd frontend && npm install && npm run build`, publish
`./frontend/dist`, and rewrite `/*` to `/index.html` so client-side routing
works.

Note the build cds into `frontend` rather than setting `rootDir`.
`staticPublishPath` is resolved relative to the **repository root**, so mixing
the two makes the build directory and the publish directory relative to
different places — and the site deploys empty.

Set `VITE_API_BASE_URL` to the API service's URL. Vite inlines it at build
time, so changing it requires a **rebuild**, not a restart.

## 4. Point the two at each other

They reference each other's hostnames, so this is a second pass after the first
deploy assigns URLs:

- API: `CORS_ALLOWED_ORIGINS=https://vehicle-search.onrender.com`
- Static: `VITE_API_BASE_URL=https://vehicle-search-api.onrender.com`

The origin must match exactly — scheme and host, no trailing slash. A mismatch
returns 403 on preflight, which the browser reports as an opaque CORS failure
rather than as a configuration error.

## Verifying

```bash
API=https://vehicle-search-api.onrender.com
SITE=https://vehicle-search.onrender.com

curl -s $API/actuator/health

# Preflight must be granted for your site and refused for anything else.
curl -si -X OPTIONS $API/api/v1/search \
  -H "Origin: $SITE" -H 'Access-Control-Request-Method: POST' \
  | grep -i 'access-control-allow-origin'

curl -si -X OPTIONS $API/api/v1/search \
  -H 'Origin: https://not-your-site.example' -H 'Access-Control-Request-Method: POST' \
  | head -1        # expect 403
```

## URLs after deploying

Hostnames depend on name availability — Render appends a suffix if the name is
taken — but the paths are fixed.

**Static site** `https://vehicle-search.onrender.com`

| Path | |
|---|---|
| `/` | the app |

**API service** `https://vehicle-search-api.onrender.com`

| Path | |
|---|---|
| `POST /api/v1/search` | the product |
| `GET /api/v1/schema` | enums, ranges, concept vocabulary |
| `GET /api/v1/vehicles/{id}` | one vehicle |
| `GET /swagger-ui.html` | browsable API docs |
| `GET /v3/api-docs` | the OpenAPI document the frontend types are generated from |
| `GET /actuator/health` | Render's health check; validates the database |
| `GET /actuator/health/liveness` | keep-alive target; does not touch the database |

## Health endpoints, and keeping the service awake

| Endpoint | Touches the database | Use for |
|---|---|---|
| `/actuator/health` | **yes** | Render's `healthCheckPath` — it should fail when the database is unreachable |
| `/actuator/health/liveness` | no | keep-alive pings |
| `/actuator/health/readiness` | no | — |

None of them are rate limited; only `/api/v1/search` is.

**Only the API can sleep.** Free *web services* spin down after ~15 minutes
without inbound traffic; static sites do not — they have no instance to stop.
So the frontend needs no keep-alive and has no health path, and a cron should
target exactly one URL.

Render's health check does **not** stop a service sleeping — it verifies
deploys and triggers restarts. On `starter` the service never sleeps and no
keep-alive is needed at all.

If you do ping a free instance from an external cron, use **`/liveness`**. The
main health endpoint validates a database connection on every call, so pinging
it keeps Neon awake as well — spending Neon compute-hours to solve a Render
problem.

And check the arithmetic before relying on it: Render free is ~750
instance-hours a month, while 24/7 uptime is ~730. Pinging turns a
scale-to-zero free tier into an always-on one that runs out partway through the
month, on both services at once.

## The cold start

Render's free web services sleep after ~15 minutes idle, and a JVM waking in a
cold container takes 40–60 seconds. A reviewer who opens the link once will sit
on a blank page long enough to assume it is broken. The Starter tier removes
this, and is the reason `plan: starter` is in the blueprint.

Neon also scales to zero but wakes in well under a second, so it is not the
problem here.
