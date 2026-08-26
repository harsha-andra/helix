# ADR 0004: Flyway owns the schema; expand/contract for zero-downtime changes

Date: 2026-08-26
Status: Accepted

## Context

Hibernate can generate DDL from entity mappings (`ddl-auto: update`), and
it will happily do it against a production database. It will also happily
fail to do the things a real schema change needs: it cannot drop a column
safely, cannot rename one without losing data, cannot add a `NOT NULL`
constraint to a column with existing rows without a backfill plan, and
whatever it decides to run has not been reviewed by anyone before it
executes.

## Decision

**Flyway owns the schema. `spring.jpa.hibernate.ddl-auto` is `validate`,
never `update`**
(`helix-app/src/main/resources/application.yml`). Hibernate is allowed to
check, on every boot, that the entity mappings agree with whatever schema
is actually there — and to fail startup loudly if they do not — but it is
never allowed to change that schema itself.

`V1__init.sql` (`helix-app/src/main/resources/db/migration/V1__init.sql`)
is hand-written: 24 tables, reviewed the way any other code change is
reviewed, applied by Flyway on application startup
(`spring.flyway.enabled: true`, `validate-on-migrate: true`). Every schema
change after V1 ships as a new versioned migration
(`V2__*.sql`, `V3__*.sql`, ...), never an edit to `V1__init.sql` itself —
Flyway validates migration checksums on startup specifically so an
already-applied migration cannot be silently edited underneath a running
system (docs/SECURITY.md, A08). `flyway.clean-disabled: true` in
`application-prod.yml` makes a destructive `flyway clean` impossible
against a deployed environment, not merely discouraged by convention.

### Why this needs no downtime: expand/contract

A schema change that both adds a constraint and is read by the *next*
release's code, deployed as a single migration, breaks the moment a
rolling deploy has old and new application instances running side by
side against the same database — which is the normal state of a
Kubernetes rollout for as long as it takes new pods to become ready. The
fix is to never make a single migration do both jobs. Split any schema
change that could break a currently-running instance into small,
independently-safe steps: **expand** the schema additively, **migrate**
the behaviour across a release boundary, then **contract** by removing
what is no longer needed — with every intermediate state valid for
*both* the old and the new application code.

### A worked example: normalising `claim.loss_type`

`claim.loss_type` is a free-text `varchar(40)` today (`V1__init.sql`,
`Claim#lossType`) — whatever string a caller sends. Suppose that needs to
become a normalised foreign key into a proper reference table, without a
release ever taking claims intake down to do it.

| Step | Migration | What it does | Why the release deployed against it is safe |
|---|---|---|---|
| **1. Expand** | `V2__add_loss_type_reference.sql` | `CREATE TABLE loss_type (...)`; `ALTER TABLE claim ADD COLUMN loss_type_id uuid NULL REFERENCES loss_type(id)` | Nullable, so every row that exists is already valid. The *previous* release's code, which has never heard of this column, keeps working unmodified — it simply never populates it. |
| **2. Backfill** | `V3__backfill_loss_type_id.sql` | A data migration matching existing `loss_type` string values to the new reference rows and populating `loss_type_id` for every existing claim | Runs once, touches only existing rows, adds nothing the running application reads yet. |
| **3. Dual-write** | *(application code, not a migration)* | The release that follows writes **both** `loss_type` (string) and `loss_type_id` (FK) on every create/update, and can read from either | This is the release boundary: while it is rolling out, some pods run the old code (reads/writes only the string) and some run the new code (writes both) — both are correct against the *same* schema, because nothing has been removed yet. |
| **4. Make non-null** | `V4__loss_type_id_not_null.sql` | `ALTER TABLE claim ALTER COLUMN loss_type_id SET NOT NULL` | Safe only once step 3 has been running for a full release cycle with no gaps — every row written since then has both columns populated, and the backfill (step 2) covered everything older. |
| **5. Contract** | `V5__drop_loss_type_string.sql` | `ALTER TABLE claim DROP COLUMN loss_type` | Safe only once no deployed code reads the string column any more — a second application release, after step 4, that stops reading/writing it first. |

Five small, reviewable migrations plus one application release boundary,
instead of one migration that renames/retypes a column in place — which
would be correct for exactly one version of the application and wrong for
every other version running at the same moment during a rollout.

## Consequences

- A schema change is never a single risky migration; it is a sequence of
  additive, individually-safe ones, each valid for whatever mix of old
  and new application code a rolling deploy has running at the same time.
- This is slower than `ddl-auto: update` — several small migrations and
  (for anything that needs step 3) a coordinated application release,
  instead of one. That cost is the point: it converts "did this schema
  change break the currently-running old pods" from a question nobody
  asked into a question the migration sequence answers by construction.
- `docs/ARCHITECTURE.md` §5 and §6 describe the same constraint from the
  testing side: integration tests boot the application against a real
  PostgreSQL and let Flyway apply every migration plus Hibernate's
  `validate` pass, so a mapping drifting from the schema fails CI rather
  than surfacing in production on a rarely-hit code path.
