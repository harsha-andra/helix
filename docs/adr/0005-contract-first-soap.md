# ADR 0005: Contract-first SOAP — XSD first, JAXB and WSDL generated

Date: 2026-08-26
Status: Accepted

## Context

HELIX still speaks SOAP because a partner system needs it to — the
architectural question is not whether to keep SOAP but how the contract
and the code relate to each other. The code-first alternative (JAX-WS
annotations on Java classes, with the WSDL derived from whatever those
classes happen to look like this week) means the contract reshapes itself
every time someone renames a field or refactors a type — silently, from
the partner's point of view, until their client breaks.

## Decision

**The schema is the artifact. The Java classes are a build output.**

[`claims.xsd`](../../helix-api-soap/src/main/resources/xsd/claims.xsd) is
hand-written and lives under version control — `ClaimStatus`, `Money`
(a `decimal` constrained to 15 total digits / 2 fraction digits, so a
partner's XML validator rejects a malformed amount before it ever reaches
HELIX), `Claim`, `ClaimSummary`, and the four request/response pairs
(`GetClaim`, `ListClaims`, `GetPolicy`) are all defined here first.

**JAXB classes are generated from the schema at build time, and are
*not* committed.** The `jaxb2-maven-plugin` binding in
`helix-api-soap/pom.xml` runs `xjc` against `claims.xsd` into
`target/generated-sources/jaxb` on every build
(`.gitignore`: "JAXB classes are generated from
src/main/resources/xsd/claims.xsd at build time. The schema is the
artifact under version control; the classes are a build output.") —
[`ClaimsEndpoint`](../../helix-api-soap/src/main/java/com/harshaandra/helix/api/soap/ClaimsEndpoint.java)
imports `com.harshaandra.helix.api.soap.generated.*`, classes that do not
exist until the schema has been compiled. There is no path by which
someone edits a generated `Claim.java` by hand and has that edit survive
the next build — the schema is the only place a contract change can be
made.

**The WSDL is generated from the schema at runtime, not hand-maintained.**
[`SoapWebServiceConfig`](../../helix-api-soap/src/main/java/com/harshaandra/helix/api/soap/SoapWebServiceConfig.java)
builds a `DefaultWsdl11Definition` from the same `claims.xsd`
(`SimpleXsdSchema`), published live at `/ws/claims.wsdl`. It cannot drift
from the schema classes were generated from, because it is generated from
the identical file at the moment it is served.

**The same service beans REST uses.**
[`ClaimsEndpoint`](../../helix-api-soap/src/main/java/com/harshaandra/helix/api/soap/ClaimsEndpoint.java)'s
own class comment states the intent plainly: "The point of this class is
what it does NOT contain: no business rules, no repository access, no
validation logic of its own. It translates XML into a service call and
the result back into XML." `getClaim`, `listClaims` and `getPolicy` each
call `ClaimService`/`PolicyService` — the identical beans
`ClaimController` calls — and map the DTO result onto the generated JAXB
types. See [ADR 0001](0001-multi-module-boundaries.md) for the module
boundary this rests on — `helix-api-soap` declares no dependency on
`helix-domain` and imports nothing from it beyond the plain `ClaimStatus`
enum, the same narrow exception REST makes — and `docs/ARCHITECTURE.md`
§1 for why this matters:
"There is exactly one implementation of 'what happens when a claim's
status changes', so the legacy SOAP channel and the modern REST channel
cannot give different answers."

## Consequences

- A breaking contract change is a deliberate edit to `claims.xsd`,
  reviewed the way any other change is reviewed — never an accidental
  side effect of renaming a Java field or refactoring a DTO.
- The generated JAXB sources needing regeneration on every clean build is
  a real cost (a `mvn clean` followed immediately by opening the project
  in an IDE briefly shows unresolved `generated.*` types until the build
  runs once) — accepted because the alternative is committed generated
  code silently drifting from the schema it was supposed to represent.
- `ClaimsEndpoint` adding a business rule of its own — instead of calling
  into `ClaimService` — would be the SOAP-side version of the same defect
  ADR 0001 discusses for REST: as ADR 0001 notes, `helix-domain` is
  present on `helix-api-soap`'s classpath transitively (via
  `helix-service`, Maven's compile-scope propagation), so nothing at the
  build level stops a future `@Endpoint` method from injecting a
  repository directly. Keeping this class a thin translation layer —
  today, verifiably true by grep, not merely by convention — is a
  discipline the module boundary does not by itself mechanically enforce;
  see ADR 0001's consequences for the ArchUnit-rule option if that needs
  to become a hard gate.
