# ADR 0002: `@Transactional` on services only, and the proxy self-invocation trap

Date: 2026-08-26
Status: Accepted

## Context

`@Transactional` can legally go on a repository method, a service method,
or a controller method — Spring will accept the annotation in all three
places. Only one of those is correct, and Spring will not tell you if you
pick wrong: an annotation in the wrong place, or in a place where it is
silently ignored, produces no error and no warning. It just does not do
what the annotation says it does.

## Decision

`@Transactional` appears on `helix-service` classes only —
`ClaimService`, `PolicyService`, `DashboardService`, `DirectoryService` —
and nowhere else in the codebase.

**Not on repositories.** A `@Transactional` per repository method is too
fine-grained: each call becomes its own transaction, and a multi-step
operation (create a claim, add its lines, record the initial audit event
and status history — see `ClaimService#create`) could half-commit if a
later step fails.

**Not on controllers.** A `@Transactional` at the controller is too
coarse: the transaction would stay open across HTTP response
serialisation, holding a database connection for however long that takes.

**`spring.jpa.open-in-view` is `false`** (`helix-app/src/main/resources/
application.yml`). Leaving it on keeps a Hibernate session open for the
entire request, which makes lazy loading work from anywhere — including
the view/serialisation layer — and that convenience is exactly what hides
an N+1 query pattern instead of surfacing it at the service boundary
where it belongs. See [ADR 0006](0006-projection-over-entity-graph.md).

### The proxy self-invocation trap

`@Transactional` is implemented as a proxy: Spring wraps the bean, the
proxy opens (or joins) a transaction, and only then delegates to the real
object. A call that arrives through the proxy is advised. A call that
does not — because it went straight from one method of a bean to another
method of the *same* bean via a plain `this.method()` — never touches the
proxy, and the annotation on the callee is silently ignored. No exception,
no warning, no transaction. This is the single most common cause of "my
`@Transactional` isn't working," and it is invisible in code review
because the annotation is sitting right there on the method.

[`SelfInvocationDemo`](../../helix-service/src/main/java/com/harshaandra/helix/service/SelfInvocationDemo.java)
is a deliberate, permanent demonstration of exactly this, proved rather
than asserted in a comment by
[`SelfInvocationDemoTest`](../../helix-service/src/test/java/com/harshaandra/helix/service/SelfInvocationDemoTest.java):

- `callsAnnotatedMethodOnSelf()` calls `annotatedButBypassed()` — which
  carries `@Transactional(REQUIRES_NEW)` — via `this`, from inside the
  same bean. The test asserts `TransactionSynchronizationManager
  .isActualTransactionActive()` is **`false`** inside the "annotated"
  method: the annotation did nothing.
- `calledThroughProxy()` — the identical method body and annotation,
  called from the test (a different bean) — asserts **`true`**: reached
  through the proxy, it works exactly as documented.
- A third test asserts `AopUtils.isAopProxy(demo)` is true, so the first
  two assertions are actually testing the trap and not a false positive
  from an unproxied bean.

### The three ways out, and why one was chosen

1. **Self-injection.** A bean autowires itself (via
   `@Lazy` to break the circular-dependency chicken-and-egg) and calls the
   annotated method through that injected reference instead of `this`.
   Works, but the field only exists to route around the bean's own proxy —
   an implementation detail leaking into the class's own field list.
2. **AspectJ compile-time or load-time weaving.** Rewrites the bytecode so
   the transaction advice applies regardless of how the method is called,
   self-invocation included. Genuinely fixes the trap at its root, at the
   cost of a build-time weaving step (or a javaagent) most Spring Boot
   projects do not otherwise need, and a failure mode (misconfigured
   weaving) that is just as silent as the trap it replaces.
3. **A separate collaborator.** Move the method that needs its own
   transaction boundary into a different bean, and call it from the
   first bean the ordinary way — a call between two different beans
   always goes through the proxy, because there is no "self" to bypass.

HELIX uses **(3)**. `ClaimService#markAdjudicated` is
`@Transactional(propagation = REQUIRES_NEW)` and is called by the (not
yet wired — see `docs/ARCHITECTURE.md` §8) Service Bus listener, a
different bean, so it is never at risk of the self-invocation trap in the
first place. `ClaimService#recordAudit` and `#recordStatusChange` are
deliberately **not** annotated and carry a comment explaining why:
annotating them would be a lie, since every caller today is another
method of the same `ClaimService` instance, and the annotation would be
silently ignored exactly as `SelfInvocationDemo` demonstrates. They run
inside whichever transaction the calling public method (`create`,
`changeStatus`) already opened, which is what the code actually needs.

Self-injection and AspectJ weaving both solve the general problem; a
separate collaborator solves the specific problem HELIX actually has, with
no new field, no build step, and no new failure mode to reason about. The
rule this leaves behind is simple enough to hold without tooling: if a
method needs its own transaction boundary, it needs to live on a bean
something *else* calls into.

## Consequences

- A reviewer checking "does this need `@Transactional`" only ever needs to
  ask it about `helix-service` classes — repositories and controllers are
  categorically not candidates.
- Any future method that seems to want its own nested transaction inside
  `ClaimService` is a prompt to extract a collaborator, not to add an
  annotation that would be silently ignored.
- `SelfInvocationDemo` and its test cost nothing at runtime (no database,
  no Spring Boot context — see the test's own comment) and stay in the
  codebase as a live regression check that the trap still behaves the way
  this ADR describes, not just documentation asserting that it does.
