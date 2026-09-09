# Deploying to Render + Neon

Two services on Render — a static frontend and a Dockerised API — with Postgres
on Neon. See [`README.md`](README.md) for the single-VPS alternative.

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
health check `/actuator/health`.

**Put it in the same region as Neon.** A cross-region hop costs more than every
query this service runs — against Singapore from outside the region, searches
measured 270–383ms versus 10–30ms locally.

Environment: the three database variables above, plus `GEMINI_API_KEY` and
`CORS_ALLOWED_ORIGINS`.

## 3. Static site

Root `frontend`, build `npm install && npm run build`, publish `dist`, and a
rewrite of `/*` to `/index.html` so client-side routing works.

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

## The cold start

Render's free web services sleep after ~15 minutes idle, and a JVM waking in a
cold container takes 40–60 seconds. A reviewer who opens the link once will sit
on a blank page long enough to assume it is broken. The Starter tier removes
this, and is the reason `plan: starter` is in the blueprint.

Neon also scales to zero but wakes in well under a second, so it is not the
problem here.
