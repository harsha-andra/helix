# HELIX — Security

Each OWASP Top 10 (2021) category below names the concrete mitigation and the file it lives in.
The point of the table is that every row can be opened and checked; a claim with no file next to
it is a claim nobody can verify.

---

## OWASP Top 10 mapping

| # | Risk | Mitigation in HELIX | Where |
|---|---|---|---|
| **A01** | Broken Access Control | `@PreAuthorize` on every controller method, checked against roles derived from the token. Default is `denyAll()` — a new endpoint is unreachable until someone authorises it deliberately. Actuator write endpoints require `SUPERVISOR`; `/actuator/prometheus` requires `MONITORING`. | [`SecurityConfig`](../helix-app/src/main/java/com/harshaandra/helix/config/SecurityConfig.java), [`ClaimController`](../helix-api-rest/src/main/java/com/harshaandra/helix/api/rest/ClaimController.java) |
| **A02** | Cryptographic Failures | No password is ever handled: HELIX is an OAuth2 resource server and validates a JWT against the issuer's published JWKS. No shared secret, no credential in the repository. HSTS with `includeSubDomains`, one year. TLS terminates at the ingress. Database credentials come from Azure Key Vault via workload identity at pod start. | [`SecurityConfig`](../helix-app/src/main/java/com/harshaandra/helix/config/SecurityConfig.java), [`values-prod.yaml`](../helm/values-prod.yaml) |
| **A03** | Injection | Every query is JPQL with bound parameters or a Spring Data derived method. No string concatenation into SQL anywhere. Output encoding is Angular's contextual escaping; `innerHTML` with unsanitised input is not used. Bean Validation constrains every inbound field before it reaches a service. | [`ClaimRepository`](../helix-domain/src/main/java/com/harshaandra/helix/domain/repository/ClaimRepository.java), [`ClaimCommands`](../helix-service/src/main/java/com/harshaandra/helix/service/command/ClaimCommands.java) |
| **A04** | Insecure Design | The claim lifecycle is an explicit state machine — illegal transitions are rejected by the domain, not merely hidden in the UI. Optimistic locking prevents lost updates by design. The audit trail is append-only by construction (no setters). Typeahead results are capped server-side because a client is not a trust boundary. | [`ClaimStatus`](../helix-domain/src/main/java/com/harshaandra/helix/domain/model/ClaimStatus.java), [`AuditEvent`](../helix-domain/src/main/java/com/harshaandra/helix/domain/model/AuditEvent.java), [`DirectoryService`](../helix-service/src/main/java/com/harshaandra/helix/service/DirectoryService.java) |
| **A05** | Security Misconfiguration | CSP without `unsafe-eval`, `frame-ancestors 'none'`, `X-Frame-Options: DENY`, `Referrer-Policy`, `Permissions-Policy`. `server.error.include-stacktrace: never` and `include-message: never`. Swagger UI is unreachable under the `prod` profile. `flyway.clean-disabled: true` in production makes a destructive clean impossible rather than merely discouraged. Stateless sessions. CORS lists explicit origins, never `*`. | [`SecurityConfig`](../helix-app/src/main/java/com/harshaandra/helix/config/SecurityConfig.java), [`application-prod.yml`](../helix-app/src/main/resources/application-prod.yml) |
| **A06** | Vulnerable Components | OWASP Dependency-Check runs in CI and **fails the build at CVSS ≥ 7**. Trivy scans the built image for OS and library CVEs. Both are blocking, not advisory. | [`ci.yml`](../.github/workflows/ci.yml) |
| **A07** | Identification & Authentication Failures | Authentication is delegated entirely to the identity provider (Keycloak locally, Entra ID in Azure). Tokens are validated against the issuer's JWKS, including signature, issuer and expiry. Sessions are stateless, so there is no session-fixation surface and no session store to steal. | [`SecurityConfig`](../helix-app/src/main/java/com/harshaandra/helix/config/SecurityConfig.java) |
| **A08** | Software & Data Integrity Failures | Container images are built from a pinned digest base and scanned before deploy. Flyway validates migration checksums on start, so an already-applied migration cannot be edited underneath a running system. CI publishes from a workflow with least-privilege OIDC federation rather than a long-lived key. | [`Dockerfile`](../Dockerfile), [`ci.yml`](../.github/workflows/ci.yml) |
| **A09** | Logging & Monitoring Failures | Every state change writes an append-only `audit_event` naming the actor, the action and the time. Unhandled exceptions are logged with a correlation id that is the only detail returned to the caller. Prometheus metrics and health probes are exposed and authorised separately. | [`ClaimService`](../helix-service/src/main/java/com/harshaandra/helix/service/ClaimService.java), [`GlobalExceptionHandler`](../helix-api-rest/src/main/java/com/harshaandra/helix/api/rest/GlobalExceptionHandler.java) |
| **A10** | Server-Side Request Forgery | HELIX makes no outbound HTTP calls driven by user input. The only egress is to the database, the identity provider's JWKS endpoint and Service Bus, all of them fixed configuration. Kubernetes `NetworkPolicy` is default-deny with explicit allows. | [`helm/templates/networkpolicy.yaml`](../helm/templates/networkpolicy.yaml) |

---

## CSRF: why it is configured the way it is

CSRF protection is **enabled**, with `/api/**` excluded. That exclusion is deliberate and worth
explaining, because "we disabled CSRF" is usually a smell.

A CSRF attack works because the browser attaches ambient credentials — cookies — to a cross-site
request automatically. The JSON API is authenticated by an `Authorization: Bearer` header, which a
cross-site form or image tag cannot set. There is no ambient credential to abuse, so a CSRF token on
those routes protects against nothing while adding a round trip.

Protection is kept where a cookie *is* the credential: the SOAP endpoint and any
cookie-authenticated, state-changing route. Those use the double-submit cookie pattern
(`CookieCsrfTokenRepository.withHttpOnlyFalse()`), which the Angular client reads and echoes as
`X-XSRF-TOKEN`.

If the API ever moves to cookie-based sessions, the exclusion must be removed in the same commit.

---

## XSS

Two layers, neither relied on alone:

1. **Angular's contextual output encoding.** Interpolation escapes by construction. `innerHTML` with
   unsanitised input does not appear in the codebase.
2. **Content-Security-Policy.** `default-src 'self'`, `object-src 'none'`, `base-uri 'self'`,
   `frame-ancestors 'none'`.

`style-src` permits `'unsafe-inline'` — an honest gap. Angular injects component styles as inline
`<style>` elements, so removing it breaks all styling. Closing it properly requires CSP nonces
threaded through the build. `script-src` does **not** permit `unsafe-inline` or `unsafe-eval`, which
is the directive that actually stops script injection.

---

## Secrets

No secret is committed. Locally, `docker-compose.yml` uses obvious throwaway values. In Azure, the
pod reads database credentials and signing keys from Key Vault using **workload identity** — a
federated token exchanged for a Key Vault token at startup.

The property of workload identity worth stating: **there is no secret in the manifest at all.** Not
an encrypted one, not a sealed one, not a reference to a `Secret` object. The pod's service account
is federated with an Azure identity, and the platform issues short-lived credentials. Nothing to
leak in a ConfigMap, nothing to rotate by hand, nothing left behind in `kubectl describe`.

CI authenticates to the cloud the same way — GitHub OIDC federation, no long-lived key in repository
secrets.

---

## What is deliberately not claimed

- **No penetration test has been performed.** The controls above are implemented and unit-tested
  where testable; they have not been adversarially validated.
- **Rate limiting is not implemented in the application.** It belongs at the ingress, and the
  ingress config here does not set it. The typeahead cap is a result-size bound, not a rate limit.
- **`style-src 'unsafe-inline'` is open**, as described above.
- **No secrets scanning pre-commit hook** is configured; CI-side scanning only.
