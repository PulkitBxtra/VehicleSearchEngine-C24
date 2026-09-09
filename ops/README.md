# Keep-alive

`n8n-render-keepalive.json` — pings the deployed API every 5 minutes so the free
Render instance never sleeps.

Lives on the n8n instance as workflow id **`vehicle-search-keepalive`**.

## Import

n8n → **Workflows → Import from File**, then activate. Or from the host:

```bash
docker cp n8n-render-keepalive.json n8n-xc57-n8n-1:/tmp/wf.json
docker exec n8n-xc57-n8n-1 n8n import:workflow --input=/tmp/wf.json
docker exec n8n-xc57-n8n-1 n8n update:workflow --id=vehicle-search-keepalive --active=true
docker restart n8n-xc57-n8n-1     # CLI activation needs a reload to register the trigger
```

## Shape

`Every 5 Minutes` → `Ping Health Endpoint` → `Record Result`

## Why it is built this way

**`/health`, not `/actuator/health`.** The actuator path validates a database
connection, so pinging it would wake Neon on every run and spend its compute
hours to solve a Render problem. `/health` touches nothing.

**90-second timeout.** A cold JVM start is 40–60s; a shorter timeout aborts
before the wake completes and the ping achieves nothing.

**24/7 is affordable.** A 31-day month is 744 instance-hours against the 750 a
free workspace gets, and the allowance resets on the 1st rather than on a
rolling window. Two caveats: the margin is ~6 hours, and the 750 is shared
across *every* free web service in the workspace — a second one would exceed it,
and Render then suspends all of them until the next month. Static sites do not
count, so the frontend is free and needs no keep-alive: it has no instance to
stop and never sleeps.

**No cross-node references in the Code node.** `$('Some Node')` hangs
indefinitely when the Code node runs in an external task runner, which is how
this instance is configured. It reads `$input` only.

**`saveDataSuccessExecution: "all"`.** With `"none"`, n8n never writes the
terminal state and every successful run sits at `running` forever — the
executions look stuck when they are not.

## Related

`n8n-ORIGINAL-keep-render-app-awake.recovered.json` is a recovered copy of the
pre-existing `render-keepalive-wf` workflow, which pings a different service
(`ap-zamp`). Kept because it was briefly overwritten by an import that reused
its id; restored from n8n's own execution history. Nothing here should ever use
that id.
