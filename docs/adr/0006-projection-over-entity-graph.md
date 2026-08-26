# ADR 0006: Scalar projection over entity graph for the claims list

Date: 2026-08-26
Status: Accepted

## Context

`ClaimController#list` renders a page of claim summaries — claim number,
policy number, claimant name, status, amount, incident date, submitted-at,
assigned adjuster, and a line count. Every association on `Claim` is
`LAZY` (correct — see the comment on `Claim#policy`), and the first
version of this endpoint mapped each row to a DTO by reading
`claim.getPolicy().getPolicyNumber()`, `claim.getClaimant().getFullName()`,
`claim.getAdjuster().getName()` and `claim.getLines().size()`. Each of
those four dereferences initialises a lazy proxy. The mapper looks
completely innocent — it is just reading fields — which is exactly why
this survives code review and is invisible in the Java. It is only
visible in the SQL log, or in a query-count assertion.

## Decision

**Measure, don't estimate.**
[`ClaimFetchStrategyTest`](../../helix-app/src/test/java/com/harshaandra/helix/ClaimFetchStrategyTest.java)
asks Hibernate's own `Statistics` how many statements it prepared to
render one page of 100 claims, for three fetch strategies, with Hikari's
`default_batch_fetch_size` deliberately forced to `-1` (Hibernate's own
default) rather than the runtime value of 50 — see the test's own comment:
leaving batching on "would measure the mitigation instead of the defect,
and would let someone delete the `@EntityGraph` without the test
noticing." The numbers below are asserted by that test, not eyeballed
from a log.

| Fetch strategy | SQL statements | Scales with page size? |
|---|---:|---|
| Lazy associations + a DTO mapper that reads them (`ClaimRepository#findAllNaive`) | **244** | Yes — linear |
| `@EntityGraph` on the three to-one associations (`ClaimRepository#findAllForListing`) | **102** | Yes — still linear |
| Scalar projection into `ClaimListRow` (`ClaimRepository#findListRows`) | **2** | **No — constant** |

**244 → 2. A 122× reduction**, and the shipped implementation
(`ClaimService#list` calls `findListRows`) is the third row.

### Why `@EntityGraph` was only half a fix

The standard first answer to an N+1 is `@EntityGraph`, and it genuinely
helped: naming `policy`, `claimant` and `adjuster` on
`findAllForListing` collapses those three to-one lookups into a single
join, and the count drops from 244 to 102.

**102, not 2.** The remaining statements are the `lines` collection: the
summary needs a line count, `claim.getLines().size()` initialises the
`OneToMany`, and that is one more select per row. The entity graph fixed
the associations it named and left the one it did not — and nothing about
the Java changed shape when that happened; only counting the queries
caught it. "Add an entity graph" felt like the finish line. It was not,
and there was no signal in the code that said so.

### Why the fix is not "add `lines` to the entity graph" too

Joining a `OneToMany` into the same query as pagination looks like the
obvious next step and is the wrong one. A join against a to-many
association multiplies each parent row by however many children it has —
a cartesian product — and once that join exists, the database is no
longer capable of applying `LIMIT`/`OFFSET` at the row level the query
actually needs it applied at: Hibernate can only honour the requested page
boundaries by pulling the *entire* joined result set into memory and
slicing it there itself. On a claims table sized for production rather
than a demo, that is an out-of-memory failure waiting for a claim with an
unusually long line list, and it fails silently until it doesn't — the
query still returns *a* page, just not cheaply, right up until it
doesn't return one at all.

`hibernate.query.fail_on_pagination_over_collection_fetch: true`
(`helix-app/src/main/resources/application.yml`) exists specifically to
convert that silent memory blow-up into a loud startup-time/query-time
failure instead — a wrong query fails fast during development, rather
than an environment-dependent OOM in production on whichever page happens
to hit a claim with enough lines.

### What actually fixed it: stop loading entities

A list screen does not need managed entities — it needs twelve scalars
per row. `ClaimRepository#findListRows` selects directly into
[`ClaimListRow`](../../helix-domain/src/main/java/com/harshaandra/helix/domain/projection/ClaimListRow.java),
a plain record: the to-one associations become SQL joins, and the line
count (`size(c.lines)`) becomes a correlated subquery Postgres evaluates
per row against the index on `claim_line.claim_id` — cheap for the
planner, and it never touches the `lines` collection as a Hibernate-managed
association at all. Two statements total (the page, and the count),
constant regardless of page size — because there are no proxies left to
initialise, no persistence context full of entities nobody will mutate,
and no dirty-checking pass over them at flush time.

Two further details the test and the repository comments call out, worth
keeping visible because both cost real debugging time the first time:

- **Batch fetching (`default_batch_fetch_size: 50`, set at runtime) is a
  genuinely good mitigation and it also disguises the defect.** It
  collapses per-row lazy loads into a handful of batched selects, which
  is why `ClaimFetchStrategyTest` forces it to `-1` — otherwise the test
  would measure the mitigation instead of the underlying query shape, and
  someone deleting the entity graph or the projection would not get
  caught.
- **An untyped SQL `null` gets inferred as `bytea` by PostgreSQL.** The
  first version of the projection's search predicate used
  `(:term is null or lower(...) like ...)`; with no search term, the
  driver sent an untyped JDBC `null`, Postgres inferred its type as
  `bytea`, and the query failed with `function lower(bytea) does not
  exist`. The fix is that the search term is normalised to an empty
  string rather than `null` before it reaches the query
  (`ClaimService#normaliseTerm`), turning the predicate into `LIKE '%%'`
  — matches everything, and keeps one query plan for both the filtered
  and unfiltered cases instead of two.

## Consequences

- The claims list holds a flat, 2-statement cost at any page size —
  verified by `ClaimFetchStrategyTest`, which fails the build if a future
  change reintroduces either the entity-graph-only shape (102) or the
  fully naive one (244).
- `findAllNaive` and `findAllForListing` stay in `ClaimRepository`
  deliberately, not as dead code but as the fixture the regression test
  compares against — deleting them would remove the ability to prove the
  fix is still a fix.
- Any future list-style endpoint that carries a `size()` or a nested
  collection read in its DTO mapping is a candidate for the same
  investigation: count the statements first, before reaching for
  `@EntityGraph` and assuming the count is now flat.
- `hibernate.query.fail_on_pagination_over_collection_fetch: true` is a
  standing guardrail against a specific, tempting wrong turn (join-fetch
  a collection into a paginated query), not just a note about one time it
  happened.
