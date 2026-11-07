# Implementation Plan: open problems and remaining work

This is the single execution plan for work that is **not yet done**. It contains only existing problems and open implementations. When a part is finished, delete it (or its finished tasks) in the same pull request; git history keeps the record. Settled facts live in the code, the [Engineering Reference](../reference/engineering-reference.md), and the [Roadmap](../reference/ROADMAP.md), not here.

Last verified against `main` after PR #20 (branch protection live, `GET /app/users/me` and `GET /app/loans` merged, V3-to-V4 upgrade test merged).

## How to use this plan

**For the maintainer:** parts are independent work packages. Run them in the order of the [dependency table](#order-and-dependencies). Tasks marked **Human** need your decision or an environment only you have; tasks marked **Agent** can be handed to an AI agent as written.

**For an AI agent executing a part:**

1. Read [AGENTS.md](../../AGENTS.md), then the documents the part links to. Do not trust prose over code; verify the "Problem" section against the current code before editing.
2. Work on a new branch. `main` is protected: one pull request per part, and the `unit-test`, `integration-test`, and `security-scan` checks must pass.
3. Make the smallest change at the owning layer. Follow the Do and Don't lists in AGENTS.md (DTOs at the boundary, no new bare `IllegalArgumentException`, no edits to applied Flyway migrations, no secrets in source).
4. Run `./mvnw verify` (and `./mvnw -Pintegration -DskipUTs -Djacoco.haltOnFailure=false verify` when persistence or migrations change; it needs Docker and a test-only `LIBRO_ADMIN_PASSWORD_HASH`). Report exactly which checks ran.
5. Meet every item in the part's **Done when** list, update the docs the part names, and remove the finished tasks from this file.
6. Stop and ask when a task says **Human** or when a decision is not covered here.

## Decisions (final)

Every open choice has been decided. An agent must implement these as written and must not reopen them; change one only by editing this table in a pull request first.

| # | Topic | Decision | Reason |
| --- | --- | --- | --- |
| D1 | Token expiry | Keep the 15-minute access token. The UI keeps the token in memory only; on a 401 it returns the user to login and then back to the page they were on. No refresh tokens, no denylist. | Needs no backend change, honors the "no incidental refresh or revocation" rule, and keeps the token away from XSS-readable storage. Revisit only after real staff use. |
| D2 | UI to API routing | Production: one domain. Caddy serves the built UI at `/` and proxies `/app/*` to the API, so there is no CORS. Local development: Vite dev server on `http://localhost:5173` with `LIBRO_CORS_ALLOWED_ORIGINS=http://localhost:5173`. | One origin avoids CORS in production and uses the existing Caddy profile. |
| D3 | UI scope | Patron screens first (Phase A), then staff (Phase B), then polish (Phase C). | Patron endpoints are stable today. |
| D4 | UI stack and location | A `ui/` folder in this repository: React, TypeScript, Vite, React Router, TanStack Query, and an `openapi-typescript` generated client. Plain CSS modules; no UI framework. | One repository means one PR, one CI, and one deploy for a single maintainer, while the Maven build stays untouched. |
| D5 | Forgotten password | Staff-assisted reset (Part 4) with a forced change at next login. | No email service exists; a library is staffed in person. |
| D6 | Fines, reservations, cover images | Out of scope for v1. | None blocks a usable library desk. Revisit after launch. |
| D7 | Refresh tokens, revocation | Not for v1 (see D1). | Needs its own design decision. |
| D8 | OCI Vault | Not for v1; keep a `0600` `.env` on the VM. | Plaintext on a single private VM is an accepted risk for a demo. |
| D9 | SBOM | Add an SBOM to the CD workflow (Part 9). Image signing deferred. | An SBOM is cheap; signing needs key management. |
| D10 | Host name | DuckDNS name set as `LIBRO_DOMAIN`. | Free, stable, works with Let's Encrypt. |
| D11 | Alerting and uptime | UptimeRobot (free) probing `https://<domain>/readyz`. Login-failure and rate-limit alerts via Grafana Cloud free tier fed from the private Prometheus endpoint. | Free tier; no self-hosted monitoring to maintain. |
| D12 | Throttling at the gateway | Stay on one instance for v1, so the in-app limiter is enough. Add gateway throttling only before a second instance. | The per-process limiter is exact with one process. |
| D13 | Security review | Run a structured self-review first (the `security-review` skill plus an OWASP ASVS Level 1 checklist) after the UI exists. Commission an external review only if real patron personal data is loaded. | Matches the demo's risk; synthetic data does not justify paid review. |
| D14 | Idle reclaim | Upgrade the tenancy to Pay As You Go with a US$1 budget alert. | Prevents reclaim of the A1 VM. |

## Order and dependencies

| Part | Title | Who | Depends on | Blocks |
| --- | --- | --- | --- | --- |
| 1 | OpenAPI contract fixes | Agent | none | 3, 5 |
| 2 | End-to-end smoke check with demo data | Agent plus Human (Docker) | none | 5 |
| 3 | UI foundation (scaffold, routing, CORS, CI) | Agent | 1 | 5 |
| 4 | Account recovery (staff-assisted reset) | Agent | none | public launch |
| 5 | UI build (patron first, then staff) | Agent plus Human | 1, 2, 3 | 6 |
| 6 | Deployment readiness (OCI) | Human plus Agent | 2 (earlier for scripts) | 7 |
| 7 | Public-exposure gates | Human | 6 | public launch |
| 8 | Deferred (decided: not for v1) | none | none | none |
| 9 | Hardening backlog | Agent | 6 | none |

Parts 1, 2, and 4 can run in parallel. Do not start Part 5 before Parts 1 to 3 are closed, because the UI client is generated from the OpenAPI document and depends on the decisions.

---

## Part 1: OpenAPI contract fixes

**Who:** Agent. **Size:** M. **Depends on:** nothing.

**Problem.** The live review in [openapi-browser-review.md](../reference/openapi-browser-review.md) found six mismatches between the generated OpenAPI document and runtime behavior. A generated UI client would inherit every one of them.

**Why it matters.** The document is the endpoint contract (see AGENTS.md sources of truth). Missing success schemas mean generated clients cannot type responses, and misleading examples cause integration mistakes.

**Tasks.**

1. Add explicit success response codes and schemas to the 17 operations that list only errors (user by-ID routes, book by-ID routes, `GET /app/users`, `GET /app/loans/me`, `GET /app/loans/users/{universityId}`, and the book list routes `all`, `sorted`, `search`, `replacement-cost`, `budget`). Follow the [API Documentation Guideline](../guides/api-documentation-guideline.md).
2. Declare a 401 response on all 28 protected operations that omit it (prefer one shared declaration over 28 copies).
3. Make `ErrorResponse` examples status-correct: remove the replacement-cost field-error example from the shared schema, or use per-status examples, so 401, 403, 404, 409, and 429 do not show a 400 validation body.
4. Replace the login and signup request examples with valid illustrative bodies (the login example must not be a usable credential; the signup example must satisfy the password complexity rule).
5. Fix the loan response example to one coherent state, and mark `returnedAt` and `lostAt` as nullable in the schema. Fix the page example so totals, content, and sort flags agree.
6. Add `maximum: 100` to the page-size schema of the eight paged GET operations that lack it, matching the service clamp.

**Done when.**

- `OpenApiMvcTest` asserts, at minimum: a success schema on a representative sample of the 17 operations, 401 on a protected operation, the page-size maximum, and a valid login example.
- A fresh browser pass at `/swagger-ui.html` (with `LIBRO_API_DOCS_ENABLED=true`) shows none of the six issues.
- `openapi-browser-review.md` is deleted, and the matching Roadmap line is updated.

**Verify.** `./mvnw verify`.

---

## Part 2: End-to-end smoke check with demo data

**Who:** Agent plus Human (needs Docker and a local `.env`). **Size:** S. **Depends on:** nothing.

**Problem.** Each piece is unit, MVC, or integration tested, but nothing proves the whole user journey works together against a running stack with seeded data. The UI will exercise exactly this chain on day one.

**Tasks.**

1. Start the local stack (`docker compose up --build`) with a test `.env`; seed with `bash scripts/seed-demo.sh`.
2. Walk the journey with `curl` or the existing `scripts/smoke.sh` pattern: login, `GET /app/users/me`, book list and search, borrow, `GET /app/loans/me`, return; then as library staff `GET /app/loans?overdue=true`, and mark a loan lost.
3. Record anything that fails or is awkward for a UI (for example a missing field in a response) as a new problem in the relevant part of this file.
4. If the journey is worth keeping, extend `scripts/smoke.sh` or add a documented script so it can be rerun before deploys.

**Done when.** Every step above returns the documented status and body shape on a clean database, and any defect found is fixed or recorded here.

**Verify.** Command output captured in the pull request description.

---

## Part 3: UI foundation

**Who:** Agent. **Size:** M. **Depends on:** Part 1.

**Problem.** There is no `ui/` project and nothing serves a UI. Decisions D1 to D4 define how to start it.

**Tasks.**

1. Create `ui/` with Vite, React, TypeScript, React Router, and TanStack Query. Add an `npm run gen:api` script that runs `openapi-typescript` against a running API's `/v3/api-docs` and writes `ui/src/api/schema.d.ts`; commit the generated file.
2. Write a small API client that unwraps `ApiResponse<T>`, throws a typed error carrying `ErrorResponse` (status, message, `fieldErrors`, `requestId`), adds the `Authorization: Bearer` header from an in-memory auth store, and on 401 clears the store and redirects to `/login` with the current path as `returnTo` (D1).
3. Configure the Vite dev server on port 5173 and document `LIBRO_CORS_ALLOWED_ORIGINS=http://localhost:5173` in `.env.example` (commented) and `docs/guides/local-setup.md` (D2).
4. Add a UI build stage and update `infra/caddy/Caddyfile` plus the Compose proxy profile so Caddy serves `ui/dist` at `/` (with SPA fallback to `index.html`) and proxies `/app/*` to the API (D2). Keep `/livez`, `/readyz`, and the private management port untouched.
5. Add a CI job `ui-build` (install, lint, type-check, build). Do **not** add it to the required checks until it is stable on `main`; then add it in a follow-up and update the branch-protection section of the CI/CD doc.
6. Update the README repo map and Engineering Reference with the `ui/` folder.

**Done when.** `npm run dev` shows a login page that can call the local API through CORS; `docker compose --profile proxy up` serves the UI and API on one origin; `ui-build` passes in CI.

---

## Part 4: Account recovery (staff-assisted reset)

**Who:** Agent. **Size:** M. **Depends on:** nothing.

**Problem.** `PUT /app/users/{universityId}/password` requires `currentPassword` (`PasswordChangeRequestDTO`), so a user who forgot their password has no recovery path, and the project has no email delivery.

**Decision (D5).** Staff set a temporary password for an account they may already manage; the user must change it at next login.

**Design.**

- Endpoint: `PUT /app/users/{universityId}/password/reset`, request `{ "temporaryPassword": "..." }`, response `ApiResponse<Void>`. Route gate: `ADMINISTRATOR` and `LIBRARIAN`.
- Authorization in `UserService` via `UserAccessPolicy` (the same target-role rules as account management): assistants reset students and faculty; librarians also reset assistants; nobody resets a librarian, and nobody resets their own account through this route.
- Validation: the existing 8 to 72 character complexity rule and the 72-byte BCrypt limit. Hash with BCrypt. Never log or return the password.
- Schema: forward-only Flyway migration **V6** adds `must_change_password boolean not null default false` to `users`; update the JPA entity (Hibernate stays on `validate`). The reset sets it to `true`; the existing change-password operation sets it to `false`.
- Login response (`TokenResponseDTO`) and `GET /app/users/me` gain `mustChangePassword`. The UI forces the password-change screen while it is true. Server-side enforcement beyond that flag is out of scope (accepted risk: a client that ignores the flag still holds a valid token for the account the staff member chose to reset).
- A locked-out librarian has no API path by design. Document an operator procedure in the runbook: generate a BCrypt hash and update the account's row on the VM, with a backup first.
- Add a counter metric `libro.auth.password_resets` on the private management port.

**Tasks.** Implement the above. Add service tests (full allowed and denied role matrix), MVC/security tests (each role, no token), and a PostgreSQL test for the V5-to-V6 upgrade with existing rows. Update OpenAPI, the Engineering Reference API inventory, the Roles and JWT guide, the README API table, and `docs/devops/runbook.md`.

**Done when.** A student who forgot their password can be reset by a library assistant, can log in with the temporary password, is forced to choose a new one, and the matrix test shows no role can reset an account outside its scope.

---

## Part 5: UI build

**Who:** Agent (the maintainer reviews each phase). **Size:** L. **Depends on:** Parts 1 to 4.

**Problem.** There is no UI. The API supports these capabilities today: login, own profile, catalog browse and search, borrow, return, own loan history, staff catalog management, staff loan list with filters, per-borrower history, lost-copy resolution, and role-scoped account management.

**Tasks (execute in phases; one pull request per phase).**

- **Setup.** Done in Part 3 (stack and location per D4). Use the generated client, show `ErrorResponse.fieldErrors` next to form fields, and show `requestId` in unexpected-error messages so users can quote it.
- **Phase A: patron.** Sign in and sign up, role-aware landing page using `GET /app/users/me`, catalog browse and search with pagination, book detail, borrow, return, my loans (with overdue indicator), change password.
- **Phase B: staff.** Catalog create, edit, delete; overdue and active loan desk using `GET /app/loans` filters; borrower history; mark a loan lost; account management within role scope; librarian creates faculty and assistant accounts.
- **Phase C: polish.** Empty, loading, and error states; accessibility pass; responsive layout.

**Done when.** A student can sign up, find a book, borrow, view, and return it; a library assistant can see an overdue loan and resolve it; a librarian can create an assistant account. Role-restricted screens are hidden for roles that cannot use them, and the API still enforces the rules.

**Notes.** Never trust the UI for authorization; the API gates remain the authority. Keep the token in memory only (D1); do not use `localStorage`, `sessionStorage`, or cookies (AGENTS.md: revisit CSRF if transport moves to cookies). Handle `mustChangePassword` from Part 4 in Phase A.

---

## Part 6: Deployment readiness (OCI)

**Who:** Human plus Agent. **Size:** L. **Depends on:** Part 2.

**Problem.** The deployment tooling (`scripts/`, `infra/docker-compose.prod.yml`, `.github/workflows/cd.yml`) was rehearsed against a local Docker stack only. No VM exists, so the deploy job is switched off (`OCI_DEPLOY_ENABLED` is not `true`). The first real provisioning is the real test.

**Tasks.**

1. **Human:** Verify OCI Always Free eligibility and region; upgrade to Pay As You Go with a US$1 budget alert (D14); retry across availability domains if A1 capacity is unavailable.
2. **Human:** Provision the VM, block volume, network rules, and a DuckDNS host name (D10), following the [Operations Runbook](../devops/runbook.md). Set `LIBRO_DOMAIN`.
3. **Human:** Set strong, unique secrets on the VM only: database password, `LIBRO_JWT_SECRET_BASE64`, a unique hash for the historical Flyway V2 administrator `0000-0001`, and the first-librarian bootstrap values. Staff credentials are never published.
4. **Human + Agent:** Run `scripts/deploy.sh` by hand; then rehearse **backup, restore, and rollback** on the live host. Fix any script or runbook defect found, and record the fix.
5. **Human:** Add the four `production` environment secrets and required reviewers, then set `OCI_DEPLOY_ENABLED=true` **only after** the rollback has been rehearsed on the live host.
6. **Agent:** After the first real deploy, correct the docs that say the service is "not deployed" (README, CI/CD doc, runbook, Roadmap).

**Done when.** `/readyz` is reachable over TLS on the real domain, the smoke test passes against it, a backup and restore and a rollback have each been run successfully on the VM, and automated deploys are enabled.

**Risks.** Oracle may reclaim an idle A1 VM or lack capacity; the scripts have never run on OCI; the V2 administrator exists in every database and needs a unique production hash.

---

## Part 7: Public-exposure gates

**Who:** Human. **Size:** M. **Depends on:** Part 6.

**Problem.** These gates are recorded in the Roadmap and must close before the public sees the service.

**Tasks.**

1. Real-domain TLS in production (the Caddy proxy profile; confirm certificates issue and renew).
2. Alert rules for `libro.auth.logins` failures and `libro.rate_limit.rejections` (Grafana Cloud free tier fed from the private port 9091) and an UptimeRobot probe of `https://<domain>/readyz` (D11). Nothing scrapes the metrics yet.
3. Gateway-level throttling is **not** needed for v1 (D12); record "single instance only" in the runbook and add throttling before any second instance.
4. Confirm `SERVER_FORWARD_HEADERS_STRATEGY=native` is set only when the app port is reachable solely from the trusted proxy.
5. After Part 5, run the structured self-review from D13 and fix findings; commission an external review only if real patron personal data will be loaded.

**Done when.** The Roadmap's two open Verification-queue items are checked, with the review findings tracked.

---

## Part 8: Deferred by decision (not for v1)

No work is scheduled here. These were decided against for the first release (D6, D7, D8) and stay listed so nobody builds them by accident.

| Feature | Current state | Decision |
| --- | --- | --- |
| Fines or charges for overdue or lost copies | `replacementCost` is stored; nothing is charged | Out of scope for v1; Domain Decisions lists penalties as future scope. |
| Reservations or holds | None | Out of scope for v1. |
| Cover images | None | Out of scope for v1; the UI shows a placeholder. |
| JWT refresh tokens or revocation | Deliberately not implemented | Not for v1; needs its own design decision. |
| OCI Vault for secrets | `0600` `.env` on the VM | Not for v1. |
| Image signing | None | Deferred; the SBOM is in Part 9. |

---

## Part 9: Hardening backlog

**Who:** Agent. **Size:** S to M each. **Depends on:** Part 6.

Verify each item against the current workflow files before starting; drop any that are already done.

- Add an SBOM to the CD workflow (D9), published with the image.
- Add a dependency-review step to CI (none was found in `.github/workflows`).
- Cache Docker layers and the Trivy database in CI/CD to cut run time.
- Group Dependabot updates to reduce PR noise (a `docker-compose` ecosystem entry already exists).
- Add a scheduled job that restores the latest backup into a scratch database and runs the smoke test, so backups are proven rather than assumed.
