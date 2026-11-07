# Agent and Contributor Guide

This file is the repository's internal entry point for AI agents and human contributors. Read it before changing code or documentation. The README is the recruiter-facing project overview; it is not the authority for implementation details.

## Start here

1. Read this file for contributor rules and the source-of-truth map.
2. Read [Current System Engineering Reference](docs/reference/engineering-reference.md) for the repository map, current layer contracts, API inventory, configuration, and test strategy.
3. Read the relevant focused document before editing:
   - [Role and JWT guide](docs/reference/security-roles-and-jwt-guide.md) for live role/loan behavior and JWT authentication rules.
   - [Loan implementation guide](docs/guides/loan.md) for the loan lifecycle, API, schema, and build flow.
   - [Development roadmap](docs/reference/ROADMAP.md) for outstanding work.
   - [API documentation guideline](docs/guides/api-documentation-guideline.md) for controller/OpenAPI documentation.
   - [Domain decisions](docs/guides/domain-decisions.md) for the physical-copy model and loan constraints.
   - [Implementation plan](docs/adr/IMPLEMENTATION_PLAN.md) for open problems and remaining work, split into parts.
   - [Architecture design rules](docs/architecture.md#design-rules) for the settled layered feature-package structure.

## Sources of truth

Keep facts in the artifact that owns them. If prose disagrees with running code, migrations, or tests, investigate and correct the stale prose instead of treating it as implemented behavior.

| Question | Source of truth |
| --- | --- |
| What an endpoint currently does | Controller mappings, DTOs, service behavior, generated OpenAPI, and API tests |
| What access is currently enforced | SecurityConfig, any method/service authorization checks, and security tests |
| What roles and columns the database accepts | Latest applied Flyway migrations; JPA mappings must match |
| Current entity and relationship model | JPA entities plus [Domain Decisions](docs/guides/domain-decisions.md) |
| API documentation conventions | [API Documentation Guideline](docs/guides/api-documentation-guideline.md) |
| JWT authentication and current role/loan policy | [Role and JWT guide](docs/reference/security-roles-and-jwt-guide.md) and [JWT Authentication Design](docs/reference/jwt-authentication-design.md); verify live behavior against code and tests |
| Remaining work | [Development roadmap](docs/reference/ROADMAP.md) |
| Settled architecture choices | [Architecture design rules](docs/architecture.md#design-rules) |
| Open work and next steps | [Implementation plan](docs/adr/IMPLEMENTATION_PLAN.md) |
| Recruiter-facing project description | [README](README.md) and the public-facing documents it links to |

## Current implementation facts

- The project is a single-module Java 25 / Spring Boot 4.1 REST API using Spring MVC, Spring Data JPA, Hibernate, PostgreSQL, Flyway, and Maven.
- Login at POST /app/auth/login authenticates through Spring Security, LibroUserDetailsService, and BCrypt, then issues a 15-minute HS256 JWT. Protected routes accept Bearer tokens only; HTTP Basic is removed.
- POST /app/users/signup, POST /app/auth/login, and GET /livez, GET /readyz are public. Other routes require authentication. The filter chain is stateless and CSRF is disabled for this Authorization-header-only API.
- Login and signup requests pass through a configurable per-IP fixed-window MVC interceptor after Spring Security and before body binding/controller invocation; over-limit requests return 429 without reaching the controller/service. Counters are per process and reset on restart. `SERVER_FORWARD_HEADERS_STRATEGY` defaults to `none`; use `native` only when the app port is reachable solely from the trusted OCI proxy. Use gateway throttling and private app ingress before scaling or public exposure.
- CORS is disabled by default. `LIBRO_CORS_ALLOWED_ORIGINS` accepts comma-separated exact UI origins; wildcard origins are rejected, cookies are disabled, and `Retry-After` is exposed to browser clients.
- JWT issuer/audience default to `libro-api`; `LIBRO_JWT_SECRET_BASE64` is required and must decode to at least 32 bytes. Tokens validate signature, issuer, single audience, time claims, subject, and exactly one known role. See [JWT Authentication Design](docs/reference/jwt-authentication-design.md).
- Passwords must satisfy the 8–72 character complexity policy and remain within BCrypt's 72 UTF-8 byte input limit. Signup, password changes, and login enforce the byte boundary.
- The live UserRole values are STUDENT, FACULTY, ADMINISTRATOR, and LIBRARIAN.
- STUDENT means a student patron; FACULTY means school faculty using the library as patrons; ADMINISTRATOR means a library assistant (such as fellow faculty, student assistants, or volunteers), not a system administrator; LIBRARIAN means the library's librarian.
- Book reads allow all four roles. Book creation, update, and deletion allow ADMINISTRATOR and LIBRARIAN.
- A Book includes an optional description and a positive `replacementCost`; the public API uses replacement-cost names for search, sort, filtering, and statistics. The old `price` API names are removed.
- Librarians create faculty and administrator/library-assistant accounts. Administrators can list/read/manage student and faculty accounts and delete those accounts; librarians may also manage/delete administrator accounts. Students and faculty can delete their own accounts. Staff cannot manage another librarian account; administrator and librarian accounts cannot delete themselves.
- User account ownership and target-role rules are enforced in UserService as well as by route authorization. A role/password change or account deletion does not revoke an issued token before its expiry plus 30 seconds of clock skew.
- `GET /app/users/me` returns the authenticated account's current public profile; a deleted account returns 404. Library staff can list role-scoped loans with `GET /app/loans`, filtering by status, overdue state, and borrower before pagination.
- No librarian account is seeded by migration. An opt-in startup runner can create the first librarian from five environment variables, including a BCrypt hash; the values are all-or-nothing, repeat starts are idempotent, and logs omit account data and the hash. The historical V2 administrator bootstrap remains in place.
- Spring Boot Actuator runs on a private management port (`MANAGEMENT_SERVER_PORT`, default 9091, never published) exposing `health` and `prometheus`. Public liveness and readiness probes are also served on the application port as `/livez` and `/readyz` with hidden details; Docker and Compose probe `/readyz`.
- Every successful response uses the `ApiResponse<T>` envelope (`success`, `message`, `data`, `timestamp`), including login and pages (`ApiResponse<Page<T>>`); errors use `ErrorResponse`, which also carries `requestId`. Filter-chain 401/403 and 429 responses use the same JSON error body.
- `RequestIdFilter` assigns an `X-Request-Id` (a safe inbound value or a UUID) to every request, the log pattern and error bodies. All book list endpoints are paginated (default size 12, maximum 100).
- `docker compose --profile proxy up` adds a Caddy TLS reverse proxy (`infra/caddy/Caddyfile`); metrics `libro.rate_limit.rejections` and `libro.auth.logins` are exported on the private management port. JWT revocation is deliberately not implemented (accepted 15-minute window).
- Stored `LocalDateTime` values use Asia/Manila through `LibraryTime` and the loan `Clock`. Swagger UI and `/v3/api-docs` are off unless `LIBRO_API_DOCS_ENABLED=true`.
- Deployment to the OCI VM is operated by `scripts/` (`deploy.sh`, `smoke.sh`, `backup.sh`, `restore.sh`, `seed-demo.sh`) on top of `infra/docker-compose.prod.yml`; see the [Operations Runbook](docs/devops/runbook.md). `.github/workflows/cd.yml` builds, scans, and publishes the ARM64 image after CI passes on `main`; its deploy job runs only when the repository variable `OCI_DEPLOY_ENABLED` is `true` and the `production` environment approves.
- The environment template is `.env.example`. CI (`.github/workflows/ci.yml`) runs `unit-test`, `integration-test` (`-Pintegration -DskipUTs`, so unit tests run once) and `security-scan` (Trivy via `scripts/ci/trivy-scan.sh`, shared with CD) jobs; the build enforces JDK 25 and an 80% line-coverage floor.
- Loans are implemented under JWT Bearer authentication. Each Book row represents one physical copy; V4 adds loan history and copy status. V5 adds optional book descriptions and renames the stored monetary value to `replacement_cost` without changing existing values. See [Loan Implementation Guide](docs/guides/loan.md).
- Flyway owns schema changes; Hibernate uses ddl-auto=validate. V5 is the current latest migration.
- Packages: every feature (`auth`, `book`, `loan`, `user`) uses the same sub-packages (`controller`, `service`, `repository`, `entity`, `dto`, `mapper`, `exception`, plus `config` where needed); shared code is in `global` (`exception`, `response`, `logging`, `openapi`, `time`). Services accept request DTOs and return response DTOs; entities never reach controllers. See [Architecture design rules](docs/architecture.md#design-rules).
- Errors: a missing resource throws a `ResourceNotFoundException` subclass (404); a broken business rule throws `InvalidRequestException` or a subclass (400). A bare `IllegalArgumentException` is a server fault (500, generic message). Account owner/role checks live in `UserAccessPolicy`.
- Tests mirror the main packages under `src/test/java/app/`. `*Test` classes run in Surefire (`./mvnw verify`), `*IT` classes in Failsafe (`-Pintegration`); `-DskipUTs` turns off Surefire only. Spring tests use `@ActiveProfiles("test")`, and `application-test.properties` layers only test overrides on top of the real `application.properties`.
- Configuration defaults live only in `application.properties`. `docker-compose.yml` passes optional variables only when set, and `.env.example` keeps optional values commented out.

These are a snapshot, not a substitute for checking the code when a task changes the relevant area.

## Do

- Keep HTTP handling in controllers, business rules and transactions in services, persistence behind repositories, and entity/DTO mapping explicit.
- Use request and response DTOs at API boundaries. Never accept a JPA entity from a client or return one directly.
- For Java methods you add or materially change, write method-level JavaDocs and line comments that explain the business rules and meaningful implementation steps.
- Keep secrets and real personal data out of source, migrations, docs, tests, logs, and examples. The historical administrator and optional librarian bootstrap accept BCrypt hashes only; no account password or live hash belongs in source control. The one exception is the public demo patron password in [Demo Accounts](docs/guides/demo-accounts.md), which is published on purpose for patron-only accounts with synthetic data; staff credentials stay private.
- Add forward-only Flyway migrations for schema changes once a migration may have been applied to a persistent database. Keep SQL and JPA mappings aligned.
- Preserve the physical-book-copy domain decision and enforce loan ownership from the authenticated principal.
- Update the README, this guide, the engineering reference, OpenAPI, and the roadmap only where their owned facts changed.
- Add focused tests for behavior changes at the appropriate service, MVC/security, or PostgreSQL persistence boundary, in the same package as the class under test.
- Add new not-found or validation failures as subclasses of `ResourceNotFoundException` / `InvalidRequestException` rather than new handler methods.

## Don't

- Don't add refresh tokens, token persistence, or a denylist as incidental JWT changes; those require a separate revocation decision.
- Don't claim JWT or Bearer authentication is live until code and tests enforce it. Loan service rules, route role gates, and authenticated identity have focused tests, and `LoanConcurrencyIT` covers concurrent checkout races on PostgreSQL.
- Don't let public signup choose a privileged role.
- Don't add repository access or domain workflows to controllers, and don't map entities in controllers.
- Don't throw a bare `IllegalArgumentException` for a client error; it maps to 500.
- Don't pass arbitrary client sort properties through to persistence.
- Don't edit an already-applied migration or destroy a database volume to make a migration pass.
- Don't expose password hashes, stack traces, SQL, database internals, or credentials in API responses.

## Security and migration notes

- Check SecurityConfig, controllers/services, and tests together before changing authentication or access behavior. Route gates and service ownership checks form separate boundaries.
- The filter chain is stateless and accepts credentials only from the Authorization header. If authentication later moves to cookies or sessions, revisit CSRF before changing transport.
- Flyway is the only schema authority. For each schema change, check the fresh-database and previous-version migration paths, then start with Hibernate schema validation enabled.
- Never run docker compose down -v unless the user explicitly requests destruction of that database data.

## Contribution workflow

1. Trace the existing request path and read the relevant source-of-truth docs.
2. Make the smallest cohesive change at the correct layer.
3. Update contract documentation and tests for behavior that changed.
4. Run the appropriate Maven checks described in [Current System Engineering Reference](docs/reference/engineering-reference.md), and report exactly which checks ran.

## Definition of done

- The requested behavior is implemented at its owning layer.
- Tests cover the changed contract and relevant failure/security cases.
- OpenAPI and recruiter-facing docs do not claim behavior that is absent from code.
- Flyway, JPA mappings, and persistence tests agree for schema changes.
- No secret or sensitive user data is included in the diff.
