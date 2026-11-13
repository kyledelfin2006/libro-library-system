# Internal Engineering Roadmap

This is the working implementation roadmap. It is not the public project overview or an API contract. Live role/JWT rules are in [Security Roles and JWT Guide](security-roles-and-jwt-guide.md) and the [JWT Authentication Design](jwt-authentication-design.md); this checklist tracks implementation status.

The pre-UI implementation plan is complete; its three parts are preserved in Git history. This roadmap tracks the remaining work.

## Current state

| Area | Implemented now |
| --- | --- |
| Books | CRUD, optional description, replacement-cost tracking, search, pagination, sorting, filtering, statistics, advisory availability |
| Accounts | Student signup; librarian-created faculty/library assistants; role-scoped searchable directory, profile/password management, and deletion |
| Roles in Java and the current schema | STUDENT, FACULTY, ADMINISTRATOR, LIBRARIAN (Flyway V3) |
| Authentication | Local HS256 JWT login and stateless Bearer validation; issuer/audience are configured, maximum token lifetime is 900 seconds, and clock skew is 30 seconds |
| Authorization | Book reads for all roles; ADMINISTRATOR/LIBRARIAN book CRUD; librarian creates faculty/library assistants; role-scoped service account checks |
| Loans | Implemented in application and V4 migration; service/MVC rules, PostgreSQL invariants and concurrent checkout races covered |
| LIBRARIAN role and target owner/directory rules | Implemented with JWT Bearer authentication |
| UI backend additions | Stable business error codes, staff build info, audit events (V7), and scoped weekly/top-title loan aggregates |

See the permissions matrix in [Security Roles and JWT Guide](security-roles-and-jwt-guide.md). Administrator means library assistant, not system administrator. The paginated directory and profile/password operations follow role and target-account boundaries; students/faculty may delete their own accounts. Loan policy and build guidance are in [Loan Implementation Guide](../guides/loan.md).

## Recommended implementation order

### 1. Role and account boundaries (implemented)

- [x] Add LIBRARIAN to the Java role enum and database constraint using Flyway V3.
- [x] Keep public signup pinned to STUDENT.
- [x] Restrict faculty and library-assistant creation to LIBRARIAN; pin public signup to STUDENT.
- [x] Limit administrators to student/faculty account management; allow librarians to manage administrator accounts too; protect librarian accounts from peer management.
- [x] Allow students/faculty to delete their own accounts and staff to delete only their permitted target roles.
- [x] Restrict the paginated directory and cross-account profile reads by caller and target roles.
- [x] Allow ADMINISTRATOR and LIBRARIAN to add, update, and delete books.
- [x] Add authorization tests for allowed and denied requests, including cross-account profile/password attempts.
- [x] Add opt-in, all-or-nothing startup provisioning for the first librarian using an operator-supplied BCrypt hash; no librarian is seeded by migration.

### 2. Verify and harden loans

- [x] Agree a 14-calendar-day term, Asia/Manila clock for offset-free `LocalDateTime` values, 3 active-loan limit, overdue checkout block, and student/faculty-only borrowing.
- [x] Add loan lifecycle, ownership checks, staff history and lost-copy resolution.
- [x] Add forward-only V4 schema with copy status, state constraints, and one-active-loan-per-copy index.
- [x] Preserve book and user history by returning HTTP 409 when referenced records are deleted.
- [x] Cover the PostgreSQL race where a catalog edit competes with a lost-loan resolution and must preserve `LOST` copy status.
- [x] Add focused loan service tests for borrower rules, availability, returns, lost-resolution scope, and history pagination.
- [x] Add loan MVC/security tests for route roles, authenticated identity, and stateless requests without CSRF enforcement.
- [x] Add PostgreSQL tests for returned history, the one-active-loan index, and protected deletion.
- [x] Add a concurrent checkout test proving the three-active-loan borrower limit and one-borrower-per-copy rule hold (`LoanConcurrencyIT`).
- [x] Verify fresh V1-to-V5 startup through the PostgreSQL integration profile.
- [x] Verify fresh V1-to-V6 startup and the V5-to-V6 account upgrade.
- [x] Verify the V4-to-V5 upgrade path preserves existing replacement-cost values and schema validation.
- [x] Run the V3-to-V4 PostgreSQL upgrade test, including V4 Hibernate validation and data preservation.
- [x] Add `GET /app/users/me` for the authenticated profile and a role-scoped, filtered `GET /app/loans` staff list.

### 3. Replace HTTP Basic with local JWT authentication (implemented)

- [x] Add public POST /app/auth/login using Spring Security AuthenticationManager, UserDetailsService, and BCrypt.
- [x] Issue short-lived HS256 access tokens and validate Bearer tokens with Spring Security OAuth2 Resource Server/JWT support.
- [x] Require an external Base64 signing key, validate issuer/audience/time/subject/role claims, and use stateless header-only request security.
- [x] Mark signup and login public in the filter chain and OpenAPI; remove HTTP Basic.
- [x] Add signed-token MVC coverage for absent, malformed, expired, wrong-signature/key/algorithm, wrong-issuer/audience, invalid claims, Basic rejection, transport restrictions, and role denials.
- [x] Enforce BCrypt's 72 UTF-8 byte limit consistently at signup, password change, and login.

### 4. Keep the public and internal docs aligned

- [x] Update README status, endpoint summary, and historical administrator bootstrap configuration.
- [x] Update AGENTS.md and Current System Engineering Reference with the new current facts and contributor rules.
- [x] Update OpenAPI operation descriptions to match role boundaries.
- [x] Add generated OpenAPI MVC assertions for Bearer security, public signup/login, and the login contract.
- [x] Serve Swagger UI and `/v3/api-docs` publicly only when `LIBRO_API_DOCS_ENABLED=true` (off by default).
- [x] Return every successful response in the `ApiResponse` envelope.
- [x] Review and correct the generated OpenAPI contract, including success schemas, Bearer errors, pagination, and examples.

## Database migration rule

Before changing a migration, establish whether that version has been applied to a persistent database. If it has, add the next forward-only migration. Keep Flyway SQL, JPA mappings, and integration tests aligned. Do not destroy a database volume to resolve migration drift.

## Verification queue

- [x] Run the PostgreSQL-backed integration profile with Docker and a test-only BCrypt hash.
- [x] Add configurable per-IP application throttling for public login and signup routes, standard 429 responses, and warning logs when a client reaches its quota.
- [x] Provide a TLS reverse-proxy reference (`docker compose --profile proxy`) and export login-outcome and rate-limit-rejection metrics on the private management port.
- [ ] Deploy behind TLS with a real domain, alert on the login-failure and rate-limit metrics, and add gateway throttling before scaling beyond one instance.
- [ ] Complete an independent security review before public internet exposure.

## Structure and delivery cleanup

- [x] Apply the same sub-packages in every feature, mirror them in tests, and record the layering rules in [Architecture](../architecture.md#design-rules).
- [x] Map entities to DTOs inside services only; default services to read-only transactions.
- [x] Replace per-feature exception handlers with `ResourceNotFoundException` and `InvalidRequestException`; stop treating library `IllegalArgumentException`s as client errors.
- [x] Move account owner/role checks into `UserAccessPolicy` with a matrix test.
- [x] Layer a `test` profile over the real configuration instead of shadowing it.
- [x] Keep configuration defaults in one place; simplify the Compose environment and `.env.example`.
- [x] Layered Docker image with a single health check; one shared Trivy script for CI and CD; CI jobs `unit-test`, `integration-test`, `security-scan`, `ui-test`, and `e2e-test`, with backend unit tests running once.
- [x] Protect `main`: require PRs and all five CI checks, including `ui-test` and `e2e-test`; block force-pushes and deletion; administrators may bypass (see [CI/CD and DevOps](../devops/ci-cd.md#branch-protection)).

## Web UI

The web UI MVP is built and documented in the [Web UI guide](../guides/web-ui.md); its execution plan has been replaced by that guide.

- [x] Sign-in, registration, catalog, borrow and return, My Account, staff dashboard, loans, inventory, accounts, audit log, status, version, and release history.
- [x] CD builds and ships `ui/dist`; the Playwright journey runs in CI; the optional `metrics` Compose profile adds Prometheus and Grafana.
- [ ] Run the 5-second cover scan test with real people (see the guide's open items).

## Source documents

- [Security Roles and JWT Guide](security-roles-and-jwt-guide.md): live role/loan policy and JWT summary.
- [JWT Authentication Design](jwt-authentication-design.md): as-built boundaries, classes, flows, rules, exceptions, configuration, and validation matrix.
- [Current System Engineering Reference](engineering-reference.md): current repository facts and technical conventions.
- [API Documentation Guideline](../guides/api-documentation-guideline.md): OpenAPI/controller documentation rules.
- [Domain Decisions](../guides/domain-decisions.md): physical-copy model and loan invariant.
- [Loan Implementation Guide](../guides/loan.md): live loan behavior, endpoints, schema, and build flow.
