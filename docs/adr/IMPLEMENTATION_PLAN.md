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

## Order and dependencies

| Part | Title | Who | Depends on | Blocks |
| --- | --- | --- | --- | --- |
| 1 | OpenAPI contract fixes | Agent | none | 3, 5 |
| 2 | End-to-end smoke check with demo data | Agent plus Human (Docker) | none | 5 |
| 3 | UI decisions | Human | 1 (for the client choice) | 5 |
| 4 | Account recovery (forgotten password) | Human decision, then Agent | none | public launch |
| 5 | UI build (patron first, then staff) | Agent plus Human | 1, 2, 3 | 6 |
| 6 | Deployment readiness (OCI) | Human plus Agent | 2 (earlier for scripts) | 7 |
| 7 | Public-exposure gates | Human | 6 | public launch |
| 8 | Deferred product features | Human decision | none | none |
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

## Part 3: UI decisions

**Who:** Human. **Size:** S. **Depends on:** Part 1 for the client choice.

**Problem.** Four choices are unmade, and each changes what the UI and API need. Recommendations are given with reasons.

| # | Decision | Recommendation | Why |
| --- | --- | --- | --- |
| 1 | Token expiry | **Re-login on 401** (no backend change) | Tokens last 900 seconds with no refresh or revocation by design. Adding refresh tokens or a denylist needs its own revocation decision (AGENTS.md), so avoid it until real staff complain. |
| 2 | How the UI reaches the API | **Same domain behind the Caddy proxy** in production; set `LIBRO_CORS_ALLOWED_ORIGINS` to the dev-server origin locally | CORS is off by default and requires exact origins. One domain avoids CORS in production. |
| 3 | First UI scope | **Patron screens first**, then staff | Every patron endpoint exists and is stable; the staff screens can follow Part 1. |
| 4 | Stack and location | **Separate folder or repository**, with a typed client generated from OpenAPI | Keeps frontend tooling out of the Maven build and keeps types aligned with the API. |

**Tasks.** Confirm or change each row, then record the outcome in this file's Part 5 (stack, repository, scope). If decision 1 becomes "add refresh tokens", open a separate design document first; do not fold it into UI work.

**Done when.** All four decisions are recorded in Part 5.

---

## Part 4: Account recovery (forgotten password)

**Who:** Human decision, then Agent. **Size:** M. **Depends on:** nothing.

**Problem.** `PUT /app/users/{universityId}/password` requires `currentPassword` (see `PasswordChangeRequestDTO`). A patron or staff member who forgets their password has no recovery path at all, and the project has no email delivery.

**Why it matters.** A real library will hit this in the first week, and the only fix today is direct database access.

**Decision needed (Human).**

| Option | How | Tradeoff |
| --- | --- | --- |
| A. Staff-assisted reset (recommended) | Library staff set a temporary password for a patron they are allowed to manage; the user must change it at next login | No email infrastructure; fits an in-person library. Needs a "must change password" flag (a Flyway migration) or an equivalent. A librarian-forgotten password still needs an operator procedure. |
| B. Email reset link | Time-limited, single-use reset token sent by email | Self-service, but needs an email provider, token storage, and abuse controls. |

**Tasks (for the chosen option, Agent).**

1. Reuse `UserAccessPolicy` so staff can only reset accounts they may already manage (assistants: students and faculty; librarians: also assistants; nobody resets a librarian).
2. Enforce the 8 to 72 character, 72-byte password policy; hash with BCrypt; never log or return the password.
3. If a forced-change flag is added, add a forward-only Flyway migration (next after V5), update the JPA mapping, and extend login/profile responses so the UI can prompt.
4. Add rate limiting consistent with existing public-route limits if any route becomes public.
5. Tests: service (policy matrix), MVC/security (each role allowed and denied), persistence if a column is added. Update OpenAPI, the Engineering Reference API inventory, and the Roles and JWT guide.

**Done when.** A student who forgot their password can regain access through the chosen flow, and the matrix test shows no role can reset an account outside its scope.

---

## Part 5: UI build

**Who:** Agent plus Human. **Size:** L. **Depends on:** Parts 1, 2, and 3.

**Problem.** There is no UI. The API supports these capabilities today: login, own profile, catalog browse and search, borrow, return, own loan history, staff catalog management, staff loan list with filters, per-borrower history, lost-copy resolution, and role-scoped account management.

**Tasks (fill the blanks from Part 3, then execute in phases).**

- **Setup.** Stack: _to be decided_. Location: _to be decided_. Generate a typed client from `/v3/api-docs`. Handle `ApiResponse` envelopes and `ErrorResponse.fieldErrors` centrally. On 401, return the user to login and back to where they were.
- **Phase A: patron.** Sign in and sign up, role-aware landing page using `GET /app/users/me`, catalog browse and search with pagination, book detail, borrow, return, my loans (with overdue indicator), change password.
- **Phase B: staff.** Catalog create, edit, delete; overdue and active loan desk using `GET /app/loans` filters; borrower history; mark a loan lost; account management within role scope; librarian creates faculty and assistant accounts.
- **Phase C: polish.** Empty, loading, and error states; accessibility pass; responsive layout.

**Done when.** A student can sign up, find a book, borrow, view, and return it; a library assistant can see an overdue loan and resolve it; a librarian can create an assistant account. Role-restricted screens are hidden for roles that cannot use them, and the API still enforces the rules.

**Notes.** Never trust the UI for authorization; the API gates remain the authority. Do not store tokens in `localStorage` without weighing XSS exposure; prefer in-memory storage given the stateless, header-only design (AGENTS.md: revisit CSRF if transport moves to cookies).

---

## Part 6: Deployment readiness (OCI)

**Who:** Human plus Agent. **Size:** L. **Depends on:** Part 2.

**Problem.** The deployment tooling (`scripts/`, `infra/docker-compose.prod.yml`, `.github/workflows/cd.yml`) was rehearsed against a local Docker stack only. No VM exists, so the deploy job is switched off (`OCI_DEPLOY_ENABLED` is not `true`). The first real provisioning is the real test.

**Tasks.**

1. **Human:** Verify OCI Always Free eligibility and region; upgrade to Pay As You Go with a budget alert to prevent idle reclaim; retry across availability domains if A1 capacity is unavailable.
2. **Human:** Provision the VM, block volume, network rules, and a host name, following the [Operations Runbook](../devops/runbook.md). Set `LIBRO_DOMAIN`.
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
2. Alert rules for `libro.auth.logins` failures and `libro.rate_limit.rejections`, and an external uptime probe against the public `/readyz`. Metrics are exported on the private port 9091 but nothing scrapes them yet.
3. Gateway-level throttling before running more than one instance (the in-app counters are per process and reset on restart).
4. Confirm `SERVER_FORWARD_HEADERS_STRATEGY=native` is set only when the app port is reachable solely from the trusted proxy.
5. An independent security review of the whole system, including the UI, done after Part 5.

**Done when.** The Roadmap's two open Verification-queue items are checked, with the review findings tracked.

---

## Part 8: Deferred product features

**Who:** Human decision. **Size:** varies. **Depends on:** nothing.

Not needed for a first usable UI. Each needs a product decision before any design work.

| Feature | Current state | Note |
| --- | --- | --- |
| Fines or charges for overdue or lost copies | `replacementCost` is stored; nothing is charged or tracked | Domain Decisions lists penalties as future scope. |
| Reservations or holds | None | Needs a queue model and fairness rules. |
| Cover images | None | Needs storage and upload validation. |
| JWT revocation or refresh tokens | Deliberately not implemented | Requires a separate, explicit revocation decision (AGENTS.md). |
| Move secrets from a `0600` `.env` to OCI Vault | `.env` on the VM | Removes plaintext at rest. |
| Image provenance (SBOM, signing) | None | Supply-chain hardening. |

---

## Part 9: Hardening backlog

**Who:** Agent. **Size:** S to M each. **Depends on:** Part 6.

Verify each item against the current workflow files before starting; drop any that are already done.

- Add a dependency-review step to CI (none was found in `.github/workflows`).
- Cache Docker layers and the Trivy database in CI/CD to cut run time.
- Group Dependabot updates to reduce PR noise (a `docker-compose` ecosystem entry already exists).
- Add a scheduled job that restores the latest backup into a scratch database and runs the smoke test, so backups are proven rather than assumed.
