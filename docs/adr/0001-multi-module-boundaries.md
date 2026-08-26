# ADR 0001: Five modules — keeping protocol adapters out of persistence

Date: 2026-08-26
Status: Accepted

## Context

HELIX serves the same claims domain over two protocols — REST for the
Angular client, SOAP for partner systems that have not moved off it — plus
a bootstrap concern (Flyway migrations, Spring Security, the
`HelixApplication` entry point). A single-module Spring Boot app would
work today and would not stop anyone from doing the thing that causes the
most damage over a project's life: a controller reaching past the service
layer to run its own query.

## Decision

Split into five Maven modules with a one-directional dependency graph:

```
helix-domain     entities, repositories, projections        depends on: nothing but JPA
helix-service    business rules, transaction boundaries     depends on: domain
helix-api-rest   @RestController, OpenAPI, RFC 7807          depends on: service
helix-api-soap   contract-first JAX-WS endpoints             depends on: service
helix-app        bootstrap, security, Flyway migrations      depends on: both API modules
```

(`helix-domain/pom.xml`, `helix-service/pom.xml`, `helix-api-rest/pom.xml`,
`helix-api-soap/pom.xml`, `helix-app/pom.xml`.)

Neither `helix-api-rest` nor `helix-api-soap` *declares* a dependency on
`helix-domain` — each names only `helix-service` (plus its own
web/security/OpenAPI or JAX-WS concerns) as an internal dependency.

**Worth being precise about what that does and does not guarantee.**
Maven does not have Gradle's `api`/`implementation` split or the Java
Platform Module System's `requires`/`exports` — every compile-scope
dependency is transitive by default, so `helix-domain` (declared
`compile`-scope by `helix-service`, per `helix-service/pom.xml`) *is*
present on `helix-api-rest`'s and `helix-api-soap`'s resolved compile
classpath (`mvn -pl helix-api-rest -am dependency:tree` shows it). Nothing
in Maven's dependency model stops a controller from writing
`import com.harshaandra.helix.domain.repository.ClaimRepository;` and
having it compile. This is not a compiler-enforced wall the way it would
be with JPMS module boundaries, and it is worth saying so plainly rather
than claiming a guarantee the build does not actually provide.

What the module split *does* provide, and what actually holds the
boundary today, is narrower and just as real:

- **`helix-service`'s public surface is DTOs and commands, not
  entities.** `ClaimDtos`, `PolicyDtos`, `PartyDtos`, `ClaimCommands` —
  everything `ClaimController` and `ClaimsEndpoint` actually consume — are
  plain records with no persistence behaviour attached. There is nothing
  to usefully reach for on the domain classpath even though the jar is
  technically present: no repository is wired as a Spring bean into
  either API module (neither declares `@Autowired ClaimRepository`
  anywhere, and none is on the container's bean graph for that module to
  request one), and no controller or endpoint references
  `EntityManager`, `@Entity`, or `JpaRepository` — verified by grep
  against both modules' sources, not asserted from memory.
- **The one domain-package import that does exist is a plain
  enum.** Both `ClaimController` and `ClaimsEndpoint` import
  `com.harshaandra.helix.domain.model.ClaimStatus` directly, to filter and
  represent claim status. `ClaimStatus` carries no persistence behaviour
  (it is not `@Entity`-annotated, has no repository) — it is shared
  vocabulary, not a route into the database, and treating every domain
  package reference as equally dangerous would be the kind of false
  precision that makes people stop trusting an architecture document.
- **Each API module's own `pom.xml` never adds `helix-domain` as a direct
  dependency**, so the *intent* is visible in the build file a reviewer
  reads, even though Maven's transitive resolution means the enforcement
  is a matter of discipline and code review from that point on — the same
  as most conventions a Java codebase relies on that its build tool does
  not independently check.

The two API modules depend on `helix-service` and **neither depends on
the other.** `ClaimsEndpoint` (SOAP) and `ClaimController` (REST) both
call the same `ClaimService` bean — there is exactly one implementation of
"what happens when a claim's status changes," so the legacy SOAP channel
and the modern REST channel cannot give different answers to that
question. See [ADR 0005](0005-contract-first-soap.md) for the SOAP side of
this specifically.

`helix-app` sits on top and depends on both API modules, plus owns
`HelixApplication`, `SecurityConfig`, and the Flyway migration under
`src/main/resources/db/migration`. It is the only module that knows both
protocols exist.

## Consequences

- A protocol adapter reaching into persistence is something a reviewer
  would notice — a new `import com.harshaandra.helix.domain.repository.*`
  in `helix-api-rest`/`helix-api-soap` stands out precisely because
  nothing there today looks like it, not because the module graph makes
  it uncompilable. If that guarantee matters enough to enforce
  mechanically, the honest next step is a build-time check (e.g. an
  ArchUnit rule run in CI asserting neither API package depends on
  `..domain.repository..` or `..domain.model..` beyond an explicit
  allow-list) rather than trusting the module split alone to do it.
- Adding a third protocol (a gRPC façade, say) means one more module
  depending on `helix-service`, calling the same service beans — not a
  fork of the business logic.
- The cost is real regardless: five `pom.xml` files, five sets of module
  boundaries to keep straight, and a change that spans layers (a new
  field on `Claim` that needs to reach the REST DTO) touches three modules
  instead of one file. Accepted because a DTO-only service surface is a
  better boundary than a single module with everything reachable from
  everywhere, even without a compiler backstopping it.
- `helix-domain` has no dependency on Spring MVC, Spring Security, or
  either API module — it is JPA entities, repositories and projections,
  nothing else (`helix-domain/pom.xml`). It could be extracted and reused
  by a batch job or a second application with no API-layer baggage
  attached.
