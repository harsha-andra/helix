# HELIX load test

`claims-load.js` is a [k6](https://k6.io) script that ramps concurrent
virtual users against `GET /api/v1/claims?size=100` (the endpoint
[`docs/ARCHITECTURE.md` §2](../docs/ARCHITECTURE.md#2-the-n1-found-measured-fixed-and-pinned)
is about), plus the claim-detail and dashboard-summary endpoints, and
enforces two thresholds: p95 latency under 500ms and a request-failure
rate under 1%.

## What this measures, and what it does not

[`ClaimFetchStrategyTest`](../helix-app/src/test/java/com/harshaandra/helix/ClaimFetchStrategyTest.java)
already proves the query-count fact: rendering one page of 100 claims
takes **244 SQL statements** with lazy associations, 102 with an
`@EntityGraph`, and **2** with the scalar projection HELIX actually ships
(`ClaimRepository#findListRows`). That is measured by asking Hibernate's
own `Statistics` how many statements it prepared — a fact about the query
plan, independent of load, and it does not need k6 to be true.

What query-counting *cannot* tell you is what that looks like under
concurrency: request latency and error rate as traffic ramps up. That is
what this script is for. **No latency or throughput number appears
anywhere in this repository**, because none has been measured in the
environment this was built in — run it yourself against your own
instance and the numbers in the summary k6 prints are real, not
transcribed from here.

If you do run it, the query-count fact above predicts the shape you
should see: a scalar projection's cost is flat per page regardless of how
many concurrent requests are asking for one, so p95 should hold roughly
steady as virtual users ramp from 20 to 50; a linear-in-page-size query
plan (the naive path the test also exercises, kept deliberately in
`ClaimRepository#findAllNaive`) would show p95 climbing with concurrency
instead. That comparison is the interesting thing to look at if you want
to see the fix's effect end-to-end rather than at the SQL layer alone —
see "Comparing against the naive path" below.

## Running it

Install k6 (https://k6.io/docs/get-started/installation/), then start
HELIX — the quickest path is the repo's own compose stack, which runs
`local-noauth` by default so no token is needed:

```bash
docker compose up --build
```

Then, from the repo root:

```bash
k6 run load-test/claims-load.js
```

### Options (environment variables)

| Variable | Default | Purpose |
|---|---|---|
| `BASE_URL` | `http://localhost:8080` | Target HELIX instance |
| `TOKEN` | *(unset)* | Bearer token, only needed against a deployment enforcing real OAuth2 (see `docker-compose.yml`'s comment on fetching one from Keycloak) |
| `PAGE_SIZE` | `100` | Page size for the claims-list request |

Example against a deployed environment with real auth:

```bash
k6 run -e BASE_URL=https://helix-dev.example.com -e TOKEN="$ACCESS_TOKEN" load-test/claims-load.js
```

### Reading the output

k6 prints a summary at the end with `http_req_duration` percentiles and
`http_req_failed` rate; a threshold that failed is marked accordingly and
k6 exits non-zero. `auth_failures` is a custom metric this script adds —
if it is non-zero, requests are getting 401/403, which means `TOKEN` is
missing or expired rather than the API being slow, and the latency
numbers above it are not meaningful until that is fixed.

## Comparing against the naive path (optional)

To see what the *un-fixed* query shape does under the same load, you
would need an endpoint backed by `ClaimRepository#findAllNaive` (kept in
the codebase specifically so `ClaimFetchStrategyTest` can assert the
difference — see the repository interface's own comment) — no such
endpoint is wired up today, because shipping the naive path anywhere
reachable would defeat the point of having fixed it. Reproducing the
before/after comparison at the HTTP layer would mean temporarily pointing
`ClaimController#list` at `findAllNaive` in a local branch, running this
script against both, and diffing the two summaries; not something to do
against a shared environment.
