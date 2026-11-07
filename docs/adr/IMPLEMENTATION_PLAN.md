# Implementation Plan: work before the UI starts

This is the single execution plan for the changes that must land **before any UI is planned or built**. It contains only open problems. When a part is finished, delete it in the same pull request; git history keeps the record. Settled facts live in the code, the [Engineering Reference](../reference/engineering-reference.md), and the [Roadmap](../reference/ROADMAP.md).

UI design and build, deployment to OCI, public-exposure gates, and deferred product features are **out of scope** here and are tracked in the Roadmap.

Last verified against `main` after PR #22 (branch protection live, `GET /app/users/me` and `GET /app/loans` merged, V3-to-V4 upgrade test merged).

## How to use this plan

**For the maintainer:** parts are independent work packages; run them in the order of the [dependency table](#order-and-dependencies).

**For an AI agent executing a part:**

1. Read [AGENTS.md](../../AGENTS.md), then the documents the part links to. Do not trust prose over code; verify the "Problem" section against the current code before editing.
2. Work on a new branch. `main` is protected: one pull request per part, and the `unit-test`, `integration-test`, and `security-scan` checks must pass.
3. Make the smallest change at the owning layer. Follow the Do and Don't lists in AGENTS.md (DTOs at the boundary, no new bare `IllegalArgumentException`, no edits to applied Flyway migrations, no secrets in source).
4. Run `./mvnw verify` (and `./mvnw -Pintegration -DskipUTs -Djacoco.haltOnFailure=false verify` when persistence or migrations change; it needs Docker and a test-only `LIBRO_ADMIN_PASSWORD_HASH`). Report exactly which checks ran.
5. Meet every item in the part's **Done when** list, update the docs the part names, and remove the finished part from this file.
6. Stop and ask when a decision is not covered here.

## Decisions (final)

These are decided. Implement them as written; change one only by editing this table in a pull request first.

| # | Topic | Decision | Reason |
| --- | --- | --- | --- |
| D1 | Token expiry | Keep the 15-minute access token. No refresh tokens and no denylist; no backend change. | Honors the "no incidental refresh or revocation" rule in AGENTS.md; a separate design decision is needed if that ever changes. |
| D2 | Browser access during development | Cross-origin access stays off by default; a local UI will use `LIBRO_CORS_ALLOWED_ORIGINS` with its exact dev origin. No code change. | The setting already exists and rejects wildcards. |
| D3 | Forgotten password | Staff-assisted reset (Part 3) with a forced change at next login. | No email service exists; a library is staffed in person. |

## Order and dependencies

| Part | Title | Depends on | Blocks |
| --- | --- | --- | --- |
| 1 | OpenAPI contract fixes | none | UI client generation |
| 2 | End-to-end smoke check with demo data | Part 1 | UI start |
| 3 | Account recovery (staff-assisted reset), API side | none | UI start |

Parts 1 and 3 can run in parallel. Run Part 2 last, so it also exercises the new reset endpoint and the final OpenAPI document.

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

**Who:** Agent plus Human (needs Docker and a local `.env`). **Size:** S. **Depends on:** Part 1 (so the OpenAPI document is checked in the same run).

**Problem.** Each piece is unit, MVC, or integration tested, but nothing proves the whole user journey works together against a running stack with seeded data. A client will exercise exactly this chain on day one.

**Tasks.**

1. Start the local stack (`docker compose up --build`) with a test `.env`; seed with `bash scripts/seed-demo.sh`.
2. Walk the journey with `curl` or the existing `scripts/smoke.sh` pattern: login, `GET /app/users/me`, book list and search, borrow, `GET /app/loans/me`, return; then as library staff `GET /app/loans?overdue=true`, and mark a loan lost.
3. Record anything that fails or is awkward for a client (for example a missing field in a response) as a new problem in the relevant part of this file.
4. If the journey is worth keeping, extend `scripts/smoke.sh` or add a documented script so it can be rerun before deploys.

**Done when.** Every step above returns the documented status and body shape on a clean database, and any defect found is fixed or recorded here.

**Verify.** Command output captured in the pull request description.

---

## Part 3: Account recovery (staff-assisted reset)

**Who:** Agent. **Size:** M. **Depends on:** nothing.

**Problem.** `PUT /app/users/{universityId}/password` requires `currentPassword` (`PasswordChangeRequestDTO`), so a user who forgot their password has no recovery path, and the project has no email delivery.

**Decision (D3).** Staff set a temporary password for an account they may already manage; the user must change it at next login.

**Design.**

- Endpoint: `PUT /app/users/{universityId}/password/reset`, request `{ "temporaryPassword": "..." }`, response `ApiResponse<Void>`. Route gate: `ADMINISTRATOR` and `LIBRARIAN`.
- Authorization in `UserService` via `UserAccessPolicy` (the same target-role rules as account management): assistants reset students and faculty; librarians also reset assistants; nobody resets a librarian, and nobody resets their own account through this route.
- Validation: the existing 8 to 72 character complexity rule and the 72-byte BCrypt limit. Hash with BCrypt. Never log or return the password.
- Schema: forward-only Flyway migration **V6** adds `must_change_password boolean not null default false` to `users`; update the JPA entity (Hibernate stays on `validate`). The reset sets it to `true`; the existing change-password operation sets it to `false`.
- Login response (`TokenResponseDTO`) and `GET /app/users/me` gain `mustChangePassword`. Clients are expected to force a password-change step while it is true. Server-side enforcement beyond that flag is out of scope (accepted risk: a client that ignores the flag still holds a valid token for the account the staff member chose to reset).
- A locked-out librarian has no API path by design. Document an operator procedure in the runbook: generate a BCrypt hash and update the account's row on the VM, with a backup first.
- Add a counter metric `libro.auth.password_resets` on the private management port.

**Tasks.** Implement the above. Add service tests (full allowed and denied role matrix), MVC/security tests (each role, no token), and a PostgreSQL test for the V5-to-V6 upgrade with existing rows. Update OpenAPI, the Engineering Reference API inventory, the Roles and JWT guide, the README API table, and `docs/devops/runbook.md`.

**Done when.** A student who forgot their password can be reset by a library assistant, can log in with the temporary password, is forced to choose a new one, and the matrix test shows no role can reset an account outside its scope.
