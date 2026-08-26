# ADR 0003: Optimistic locking on `Claim`, and the 409 contract

Date: 2026-08-26
Status: Accepted

## Context

Two adjusters can open the same claim at the same time — nothing prevents
it, and on a shared team queue it is routine, not an edge case. Without a
concurrency guard, the second adjuster's save silently overwrites the
first's, and nobody finds out until a customer asks why their claim was
denied when an adjuster remembers approving it. That failure is silent
and after-the-fact by nature: a lost update leaves no error, no log line,
and no trace beyond "the data doesn't match what I did."

## Decision

`Claim` carries a `@Version` column
(`helix-domain/src/main/java/com/harshaandra/helix/domain/model/Claim.java`):

```java
@Version
@Column(name = "version", nullable = false)
private int version;
```

backed by `claim.version integer NOT NULL DEFAULT 0` in
`V1__init.sql`. It is the only `@Version` column in the schema — claim
status changes are the one write path in this domain with routine,
expected concurrent access from two humans; the rest of the schema does
not carry the same risk profile.

### Optimistic, not pessimistic

A pessimistic lock (`SELECT ... FOR UPDATE`, held for the transaction)
would also prevent the lost update, but at the cost of holding a row lock
for however long the transaction stays open. Claim review is a
long-lived, human-paced activity — an adjuster can have a claim's detail
view open for minutes while reading attached documents and deciding on a
status change. A pessimistic lock held for that long would serialise the
entire adjuster team on one claim at a time. Optimistic locking assumes
collisions are rare and cheap to retry — true here — and pays no cost
until one actually happens; pessimistic locking assumes collisions are
frequent enough that serialising every reader is worth it, which is the
wrong trade for a workflow where "reading" can take minutes and writing
takes milliseconds.

### The 409 contract

`ClaimService#changeStatus` (`helix-service/src/main/java/com/harshaandra/
helix/service/ClaimService.java`) compares the client-supplied version
against the currently persisted one **before** touching anything:

```java
if (claim.getVersion() != command.version()) {
    throw new StaleClaimException(command.version(), claim.getVersion());
}
```

`StaleClaimException` carries both version numbers and maps to **HTTP
409** with a `recoveryAction` of `RELOAD_AND_RETRY`
(`ClaimController#changeStatus`, `GlobalExceptionHandler`) — enough for
the UI to tell the user specifically what happened ("someone changed this
claim after you loaded it") instead of a generic failure.

**The pre-check is for the error message, not for the safety.** Without
it, a stale write still fails — Hibernate's own dirty-check at flush time
throws `ObjectOptimisticLockingFailureException` against the `@Version`
column regardless of whether the service ever compared anything. The
explicit check exists purely to turn that generic persistence exception
into a response that names which version was expected and which one is
current. Remove the check and the system is still safe; remove `@Version`
and it is not.

[`ConcurrentClaimEditTest`](../../helix-app/src/test/java/com/harshaandra/helix/ConcurrentClaimEditTest.java)
asserts both layers separately:

- `secondWriterIsRejected` — two adjusters read the same claim, the first
  saves and wins, the second's save (still holding the version it
  originally read) throws `StaleClaimException` with the version numbers
  the test expects, and the claim retains the first adjuster's decision.
- `versionColumnIsEnforcedAtTheDatabase` — bypasses `ClaimService`
  entirely, writing through `ClaimRepository` from two separate
  transactions holding two `Claim` instances read at the same version, and
  asserts the second `saveAndFlush` throws
  `ObjectOptimisticLockingFailureException`. This is the test that proves
  the database-level guard, independent of the service-layer check ever
  running at all.

## Consequences

- A concurrent edit surfaces as a specific, actionable 409 rather than a
  silent overwrite or an opaque 500.
- Retrying a stale write means reloading the claim and re-applying the
  intended change — cheap, and rare enough in practice (two adjusters
  landing on the same claim within seconds of each other) that this is
  the right place to pay the cost.
- Every write path that mutates a `Claim` must load it through JPA (not a
  bulk update query, which bypasses `@Version` entirely) for the
  guarantee to hold — a constraint worth remembering if a future
  bulk-status-change feature is tempted to reach for a JPQL `UPDATE`.
