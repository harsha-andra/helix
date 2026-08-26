# HELIX — Architecture

This document records decisions and the reasoning behind them. It is not a feature list; the
README covers what the system does and how to run it.

---

## 1. Why the module boundaries are where they are

```
helix-domain     entities, repositories, projections        depends on: nothing but JPA
helix-service    business rules, transaction boundaries     depends on: domain
helix-api-rest   @RestController, OpenAPI, RFC 7807         depends on: service
helix-api-soap   contract-first JAX-WS endpoints            depends on: service
helix-app        bootstrap, security, Flyway migrations     depends on: both API modules
```

The split exists to make one thing impossible: a protocol adapter reaching into persistence.
`helix-api-rest` has no compile-time access to `ClaimRepository`, so a controller cannot quietly
open its own query and drift from the rules the service enforces. That is a constraint the build
enforces, not a convention a reviewer has to police.

The two API modules both depend on `helix-service` and neither depends on the other. `ClaimsEndpoint`
(SOAP) and `ClaimController` (REST) call the *same* `ClaimService` bean. There is exactly one
implementation of "what happens when a claim's status changes", so the legacy SOAP channel and the
modern REST channel cannot give different answers. Two implementations behind two protocols is how
channels diverge, and the legacy one is always the one nobody notices is wrong.

---

## 2. The N+1: found, measured, fixed, and pinned

This is the part worth reading. The numbers below are produced by
[`ClaimFetchStrategyTest`](../helix-app/src/test/java/com/harshaandra/helix/ClaimFetchStrategyTest.java),
which asks Hibernate's own `Statistics` how many statements were prepared. They are measured, not
remembered, and the test fails if they regress.

### The measurement

Rendering **one page of 100 claims**:

| Fetch strategy | SQL statements | Scales with page size? |
|---|---:|---|
| Lazy associations + a DTO mapper that reads them | **244** | Yes — linear |
| `@EntityGraph` on the three to-one associations | **102** | Yes — still linear |
| Scalar projection into `ClaimListRow` | **2** | **No — constant** |

**244 → 2. A 122× reduction.**

### How it happened

Every association on `Claim` is `LAZY`, which is correct. The claims list then maps each row to a
summary DTO, and the mapper reads `claim.getPolicy().getPolicyNumber()`,
`claim.getClaimant().getFullName()`, `claim.getAdjuster().getName()` and `claim.getLines().size()`.

Each of those dereferences initialises a proxy. One query for the page, then several more per row.
The mapper looks completely innocent — it is just reading fields — which is precisely why this
survives code review. It is invisible in the Java and obvious only in the SQL log.

### Why the obvious fix was only half a fix

The standard answer is `@EntityGraph`, and it genuinely helped: the three to-one associations
collapsed into a single join and the count dropped from 244 to 102.

**102, not 2.** The remaining 100 statements were the `lines` collection. The summary carries a line
count, `getLines().size()` initialises the collection, and that is one select per row. The graph
fixed the associations it named and left the one it did not.

That gap is the interesting part. "Add an entity graph" felt like the finish line and was not, and
nothing about the code would have told anyone — only counting the queries did.

Adding `lines` to the entity graph is not the answer either. Join-fetching a collection alongside
pagination produces a cartesian product, and Hibernate can only honour the page boundaries by
loading the entire result set into memory and slicing it there. It does this silently. That is why
`hibernate.query.fail_on_pagination_over_collection_fetch: true` is set in `application.yml` — it
converts that silent memory blow-up into a loud failure.

### What actually fixed it

A list screen does not need entities. It needs twelve scalars per row. `findListRows` selects
straight into a [`ClaimListRow`](../helix-domain/src/main/java/com/harshaandra/helix/domain/projection/ClaimListRow.java)
record: the associations become SQL joins and the line count becomes a correlated subquery that
Postgres evaluates per row against the index on `claim_line.claim_id`.

Two statements — the page and the count — regardless of page size. No managed entities, so no lazy
loading, no persistence context full of objects nobody will mutate, and no dirty-checking pass over
all of them at flush time.

### Two details that cost real time

**Batch fetching masks the problem.** `hibernate.default_batch_fetch_size: 50` is set at runtime and
is a genuinely good mitigation — it collapses the lazy loads into a handful of batched selects. It
also disguises the defect. The test explicitly sets `default_batch_fetch_size=-1` (Hibernate's own
default) so it measures the defect rather than the mitigation, and so deleting the projection cannot
pass unnoticed.

**PostgreSQL types an untyped null as `bytea`.** The first version of the projection query used
`(:term is null or lower(...) like ...)`. With no search term, the driver sent an untyped null,
Postgres inferred `bytea`, and the statement failed with `function lower(bytea) does not exist`.
The search term is now an empty string rather than null: the predicate becomes `LIKE '%%'`, which
matches every row and keeps a single query plan for both the filtered and unfiltered cases.

---

## 3. Concurrency: two adjusters, one claim

`Claim` carries a `@Version` column. Two adjusters opening the same claim and both saving is the
scenario it exists for; without it the second write silently overwrites the first, and nobody finds
out until a customer asks why their claim was denied when an adjuster remembers approving it.

The service compares the client-supplied version before touching anything and throws
`StaleClaimException` → **HTTP 409** with both version numbers and a `recoveryAction` of
`RELOAD_AND_RETRY`, so the UI can tell the user what actually happened.

The pre-check is for the error message, not for the safety. The database round trip is what makes
it safe, and
[`ConcurrentClaimEditTest`](../helix-app/src/test/java/com/harshaandra/helix/ConcurrentClaimEditTest.java)
asserts both layers separately — including a case that bypasses the service check entirely and
still fails with `ObjectOptimisticLockingFailureException`.

Optimistic rather than pessimistic locking because claim review is a human, long-lived activity.
Holding a row lock for the minutes an adjuster spends reading a file would serialise the whole
department; collisions are rare and cheap to retry, contention is not.

---

## 4. Transaction boundaries, and the proxy trap

`@Transactional` appears on service classes only.

- Not on repositories: too fine-grained. Each call becomes its own transaction and a multi-step
  operation can half-commit.
- Not on controllers: too coarse. The transaction would stay open across HTTP serialisation.

`spring.jpa.open-in-view` is **false**. Leaving it on keeps a session open for the whole request,
which makes lazy loading work everywhere and thereby hides exactly the N+1 above.

[`SelfInvocationDemo`](../helix-service/src/main/java/com/harshaandra/helix/service/SelfInvocationDemo.java)
is a deliberate, tested demonstration of the proxy self-invocation trap: a method annotated
`@Transactional(REQUIRES_NEW)` that runs with no transaction at all because it is called from
another method of the same bean via `this`. No exception, no warning, no transaction. The test
asserts the annotation is ignored. Full reasoning in
[ADR 0002](adr/0002-transactional-boundaries.md).

---

## 5. Schema and migrations

24 tables. `V1__init.sql` is hand-written and Flyway owns the schema; `spring.jpa.hibernate.ddl-auto`
is **`validate`**, never `update`. Hibernate may check that the mappings agree with the database.
It may never change it.

`ddl-auto: update` cannot drop a column, cannot rename one safely, cannot add a constraint to
existing data, and produces a schema nobody has reviewed. Verified as part of the build: the
application boots against a real PostgreSQL, Flyway applies the migration, and Hibernate validates
every mapping against it. A mismatch fails startup rather than surfacing as a runtime error on a
rare code path.

Schema changes after V1 use expand/contract so a release never needs downtime — see
[ADR 0004](adr/0004-flyway-expand-contract.md).

Choices worth noting:

- **`audit_event` is append-only.** The entity has no setters and no updatable columns. Corrections
  are new compensating rows. `ClaimTest` asserts by reflection that no setter exists, so it stays
  that way.
- **`claim_status_history` is separate from `audit_event`.** Cycle-time analytics query transitions
  constantly and should not scan a polymorphic audit table to do it.
- **`insured_asset` is one table with a discriminator**, not joined-table inheritance. Vehicles and
  dwellings share most attributes and queries almost always filter across both, so the inheritance
  would buy nothing but joins.
- **`reserve` keeps history as rows** rather than mutating an amount, because a reserve is
  re-estimated over the life of a claim and the trail is what actuaries need.

---

## 6. Testing strategy

**Integration tests run against real PostgreSQL, never H2.** H2 in PostgreSQL-compatibility mode
does not reproduce Postgres' planner, its locking, its type coercion, or its behaviour under
concurrent updates — which is exactly what the optimistic-locking and N+1 tests are about. A test
that passes on H2 and fails on Postgres is worse than no test. The `bytea` null-typing bug in §2 is
a concrete example: H2 would not have caught it.

Tests are split so a clean clone stays green without a container runtime:

| | Runs by default | Needs Docker |
|---|---|---|
| Unit tests (state machine, entities, proxy trap) | ✅ | No |
| Integration tests (`@Tag("integration")`, `-Pintegration`) | No | Yes (Testcontainers) |

CI runs both. `mvn verify` on a laptop without Docker runs the first group and passes.

---

## 7. Security posture

Full OWASP Top 10 mapping in [SECURITY.md](SECURITY.md). The architectural points:

HELIX is an OAuth2 **resource server**. It never sees a password and never issues a token; it
validates a JWT against the issuer's published JWKS. There is no shared secret to leak and no
credential in this repository. Keycloak locally, Entra ID in Azure — the role-claim converter reads
both Keycloak's nested `realm_access.roles` and Entra's flat `roles`, so neither the controllers nor
the deployment need to know which is in front of them.

Authorisation is `@PreAuthorize` at the controller, checked against roles derived from the token.
The `local-noauth` profile that lets a reviewer explore without standing up an identity provider
grants the anonymous principal those roles rather than switching method security off — the
authorisation code stays on the real path, which is what makes it a demo and not a hole.

Errors are RFC 7807 problem documents. The handler deliberately returns no stack traces, no SQL, and
no exception text the caller does not already know; the catch-all logs the real cause against a
correlation id and returns only that id.

---

## 8. Known limitations

Worth stating plainly, because a reviewer will find them:

- **Claim numbers come from `ThreadLocalRandom`.** Readable and unique enough for a demo; two
  application instances could collide. Production wants a database sequence.
- **Documents are metadata only.** `claim_document` stores an object-store key; no upload path is
  implemented. Binary content in Postgres would be the wrong call, so the column is the contract
  and the storage integration is not built.
- **The async adjudication path is modelled but not wired to Azure Service Bus** in the local
  compose stack — `markAdjudicated` exists with `REQUIRES_NEW` propagation and is called by tests,
  not by a live subscription.
- **`activeClaims` on the adjuster DTO is computed per adjuster.** Fine for a five-person team,
  an N+1 of its own for a five-hundred-person one. It would become a single grouped query.
