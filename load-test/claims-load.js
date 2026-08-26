/**
 * HELIX — claims list load test.
 *
 * WHAT THIS IS FOR
 * ----------------
 * docs/ARCHITECTURE.md §2 and ClaimFetchStrategyTest measure the N+1 fix in
 * SQL statements: 244 -> 102 (@EntityGraph) -> 2 (scalar projection) to
 * render one page of 100 claims. That is a query-count fact, proven by a
 * JUnit assertion against Hibernate's own Statistics, and it is real
 * regardless of load.
 *
 * This script does NOT reproduce that number — it measures something
 * query-counting cannot: request latency and error rate for
 * GET /api/v1/claims?size=100 (and two supporting endpoints) under
 * concurrent load, against whatever HELIX instance you point it at. No
 * latency or throughput figure is written down anywhere in this repository
 * because none has been measured here — this script exists so YOU can
 * produce one, against your own environment, and see the constant-query
 * projection hold its p95 as concurrency ramps up rather than degrading
 * the way a linear-in-page-size query plan would. See load-test/README.md
 * for how to run it and how to read the result.
 *
 * ENDPOINTS EXERCISED
 * --------------------
 *   GET /api/v1/claims?size=100        the endpoint the 244 -> 2 fix is about
 *   GET /api/v1/claims/{id}             detail view (a different query shape — see ClaimRepository#findWithDetailById)
 *   GET /api/v1/dashboard/summary       aggregate query, unrelated to the list page
 */

import http from "k6/http";
import { check, group, sleep } from "k6";
import { Rate } from "k6/metrics";

const BASE_URL = __ENV.BASE_URL || "http://localhost:8080";

// Leave TOKEN unset to run against the docker-compose stack, which is
// `local-noauth` by default (see docker-compose.yml) — every endpoint
// below is reachable with no Authorization header at all. Set TOKEN to a
// real bearer token (see docker-compose.yml's comment on fetching one
// from Keycloak, or your own IdP) to load-test a deployment running with
// real OAuth2 enforcement.
const TOKEN = __ENV.TOKEN || "";

const PAGE_SIZE = __ENV.PAGE_SIZE || "100";

const headers = TOKEN
  ? { Authorization: `Bearer ${TOKEN}` }
  : {};

const authFailureRate = new Rate("auth_failures");

export const options = {
  // Ramps up, holds, ramps back down — the shape that shows whether p95
  // degrades as concurrency increases (a linear-in-page-size query plan
  // would visibly worsen here; a flat 2-statement projection should not).
  stages: [
    { duration: "30s", target: 20 },
    { duration: "1m", target: 20 },
    { duration: "30s", target: 50 },
    { duration: "2m", target: 50 },
    { duration: "30s", target: 0 },
  ],
  thresholds: {
    http_req_duration: ["p(95)<500"],
    http_req_failed: ["rate<0.01"],
    auth_failures: ["rate<0.01"],
  },
};

export function setup() {
  // Grabs one real claim id up front so every VU can exercise the detail
  // endpoint against a claim that actually exists, instead of every
  // iteration hitting a 404 (which would pollute http_req_failed and tell
  // you nothing about the detail query's real latency).
  const res = http.get(`${BASE_URL}/api/v1/claims?size=1`, { headers });

  if (res.status !== 200) {
    console.warn(
      `setup: GET /api/v1/claims?size=1 returned ${res.status} — ` +
        `the claim-detail requests below will be skipped. If this is a ` +
        `401/403, set the TOKEN environment variable (see the comment at ` +
        `the top of this file).`
    );
    return { claimId: null };
  }

  const body = JSON.parse(res.body);
  const claimId = body.content && body.content.length > 0 ? body.content[0].id : null;
  return { claimId };
}

export default function (data) {
  group("list claims (the 244 -> 2 endpoint)", function () {
    const res = http.get(`${BASE_URL}/api/v1/claims?size=${PAGE_SIZE}`, { headers });
    check(res, {
      "status is 200": (r) => r.status === 200,
    });
    authFailureRate.add(res.status === 401 || res.status === 403);
  });

  if (data.claimId) {
    group("claim detail", function () {
      const res = http.get(`${BASE_URL}/api/v1/claims/${data.claimId}`, { headers });
      check(res, {
        "status is 200": (r) => r.status === 200,
      });
      authFailureRate.add(res.status === 401 || res.status === 403);
    });
  }

  group("dashboard summary", function () {
    const res = http.get(`${BASE_URL}/api/v1/dashboard/summary`, { headers });
    check(res, {
      "status is 200": (r) => r.status === 200,
    });
    authFailureRate.add(res.status === 401 || res.status === 403);
  });

  sleep(1);
}
