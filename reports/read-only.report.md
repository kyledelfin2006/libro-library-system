# Read-Only Security Audit Report (First Pass)

**Audit date:** 2026-10-31  
**Scope:** Static review of application security and authorization code, request validation, migrations, runtime configuration, Docker/Compose files, and security documentation.  
**Audit mode:** Read-only. No application files were changed. Tests and dependency/container vulnerability scans were not run.

## Executive summary

No confirmed unauthenticated privileged route or obvious broken access-control path was identified in the reviewed code. The security design uses stateless JWT Bearer authentication, explicit role gates, and service-level ownership/target-role checks. The most important gaps are deployment controls for public endpoints and HTTP ingress, Compose database exposure, root container execution, and a missing PostgreSQL concurrency test. No CI/CD configuration was found in the repository.

## Prioritized findings

### High before public deployment: public login and signup lack abuse controls

`POST /app/auth/login` and `POST /app/users/signup` are public, but this repository contains no rate limiting or abuse monitoring for either route. Repeated login attempts can support password guessing, while automated signup can create accounts and consume application/database resources. The roadmap already identifies gateway throttling and monitoring for login before public exposure; signup should be covered as well.

**Evidence:** [`SecurityConfig.java`](../src/main/java/app/auth/SecurityConfig.java#L53), [`TODO.md`](../guiding-docs/TODO.md#L72), [`jwt-authentication-design.md`](../guiding-docs/jwt-authentication-design.md#L149)

**Recommendation:** Before public exposure, apply throttling and monitoring to both routes at the gateway or application boundary. Restrict direct access to the app so clients cannot bypass those controls.

### High before public deployment: HTTPS termination is not represented in this repository

The Compose file publishes the application on port 8080 without TLS configuration. If clients reach that port directly, login passwords and Bearer tokens travel over plain HTTP. The JWT guide says HTTPS is required outside local development, but the ingress that would provide TLS is not present here.

**Evidence:** [`docker-compose.yml`](../docker-compose.yml#L59), [`jwt-authentication-design.md`](../guiding-docs/jwt-authentication-design.md#L149)

**Recommendation:** Require TLS at a reverse proxy or load balancer and restrict direct access to the app port.

### Medium: Compose publishes PostgreSQL on all host interfaces

The database mapping is `5432:5432`. Docker Compose normally publishes that port on all host interfaces, despite the comment describing local-machine access. Running this configuration on a reachable host may expose PostgreSQL beyond localhost.

**Evidence:** [`docker-compose.yml`](../docker-compose.yml#L20)

**Recommendation:** Bind the development mapping to `127.0.0.1`; omit the published database port in deployment configuration.

### Medium: the application container runs as root

The Dockerfile uses a JDK image and does not select an unprivileged user, so the application process runs as root in the container. This increases the impact of a container compromise.

**Evidence:** [`Dockerfile`](../Dockerfile#L8)

**Recommendation:** Run the process as a non-root user and use a runtime JRE image.

### Medium assurance gap: concurrent checkout limit lacks PostgreSQL coverage

Checkout locks the borrower row before checking the three-active-loan limit, which is intended to serialize concurrent requests. The roadmap and engineering reference still call for a dedicated PostgreSQL concurrency test. This is a coverage gap; the static review did not establish a bypass.

**Evidence:** [`LoanService.java`](../src/main/java/app/loan/service/LoanService.java#L60), [`TODO.md`](../guiding-docs/TODO.md#L43)

**Recommendation:** Add a PostgreSQL integration test that issues concurrent checkouts for one borrower and proves the active-loan limit holds. Run the integration profile in the release pipeline.

### Accepted residual: account and role changes do not immediately revoke issued JWTs

A password change, role change, or account deletion does not invalidate an already-issued token. The documented maximum token lifetime is 15 minutes, with up to 30 seconds of clock skew. A token for a privileged account can therefore retain its authority during that window.

**Evidence:** [`jwt-authentication-design.md`](../guiding-docs/jwt-authentication-design.md#L104), [`jwt-authentication-design.md`](../guiding-docs/jwt-authentication-design.md#L148)

**Recommendation:** Decide whether this delay is acceptable for deployment and record the policy. Treat revocation as a separate design decision rather than adding a denylist incidentally.

## Recommendation before CI/CD setup

1. Define the public ingress contract: TLS, throttling and monitoring for both public endpoints, and no direct app-port bypass.
2. Make Compose database access localhost-only and run the app container as non-root.
3. Add the PostgreSQL concurrency test for the borrower loan limit.
4. Then configure CI to run Maven verification and the PostgreSQL integration profile, plus dependency, secret, and container-image scans. These scans were not part of this audit, so no claim is made about current dependency CVEs.

## Existing controls observed

- Public access is limited to the exact signup and login POST routes; unmatched routes require authentication.
- JWT validation pins HS256 and checks the configured issuer/audience, timestamp claims, nonblank subject, and one known role.
- User and loan services enforce ownership or target-role constraints in addition to route authorization.
- Passwords are BCrypt-hashed, the JWT key is externally supplied and must decode to at least 32 bytes, and request/response DTOs keep password hashes out of API responses.
- The root `.env` is ignored by Git and excluded from the Docker build context. Its contents were not inspected.

## Audit limitations

This was a static first pass. No tests, runtime deployment, gateway/ingress configuration, dependency vulnerability database, or container image scan were evaluated. External infrastructure may already provide TLS and throttling; those controls should be verified before public exposure.

