# Keep-alive

`n8n-render-keepalive.json` pings the deployed API often enough that a free
Render instance never sleeps during waking hours.

## Import

n8n → **Workflows → Import from File** (or paste the JSON onto an empty
canvas), then **Activate**.

Check the instance timezone under **Settings → Timezone** first; the schedule is
expressed in it.

## What it does

| Node | |
|---|---|
| Schedule | every 10 minutes, 07:00–23:59 |
| Wake API | `GET /health` on the API, 90s timeout |
| Check UI | `GET /` on the static site, 30s timeout |
| Report | fails the execution if either is unreachable |

## Why it is built this way

**`/health`, not `/actuator/health`.** The actuator endpoint validates a
database connection, so pinging it would wake Neon on every run and spend its
compute hours to solve a Render problem. `/health` touches nothing.

**90-second timeout.** A cold JVM start is 40–60s. A short timeout aborts the
request before the wake finishes, so the ping costs a request and achieves
nothing.

**Not 24/7.** This is the part that keeps it free. Render allows ~750
instance-hours a month; always-on is ~730, which exhausts the allowance and
suspends the service before month end. A 17-hour window is ~510 hours and leaves
240 to spare.

**The UI check is monitoring, not keep-alive.** Render static sites have no
instance to stop, so they never sleep. That request only tells you if the site
stopped serving.

## Adjusting

- Different hours: edit the cron, `*/10 7-23 * * *`.
- Only during a review window: activate it then, deactivate afterwards. Nothing
  accrues while it is off.
- Different URLs: the two `url` fields.
