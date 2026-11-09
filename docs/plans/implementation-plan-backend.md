# Implementation Plan: Backend Additions for the UI (Agent Execution Guide)

Audience: an AI coding agent executing this plan, or a human contributor. Read [AGENTS.md](../../AGENTS.md) first; its rules apply to every step.

Status: proposed, not built. This is one of three independent plans:

| Plan | Scope |
| --- | --- |
| [UI Implementation Plan](implementation-plan-ui.md) | The web UI. Consumes the changes below. |
| **This plan** | Small, additive API changes the UI needs. |
| [OCI Idle-Reclamation Plan](implementation-plan-oci-idle-reclamation.md) | Keeping the OCI Always Free VM from being reclaimed. |

Keep each plan's changes in its own PRs. This plan ships first or alongside the UI phase that needs each item (section 3).

## 0. Rules for the executing agent

1. Trace the existing request path before editing: controller, service, repository, mapper, DTO, tests. Follow the layering rules in [Architecture](../architecture.md#design-rules).
2. Every change is **additive and backward compatible**: new optional parameters, new response fields, new endpoints. Do not rename or remove existing fields.
3. Use request and response DTOs only. Never expose a JPA entity. Services return DTOs.
4. New client errors are subclasses of `InvalidRequestException` or `ResourceNotFoundException`. Never throw a bare `IllegalArgumentException` for a client error.
5. For Java methods you add or materially change, write method-level JavaDoc and line comments that explain the business rule.
6. Schema changes use a forward-only Flyway migration (the next number is **V7**). Never edit V1 to V6. Never run `docker compose down -v`. Keep SQL and JPA mappings aligned and start with Hibernate validation on.
7. No secrets, password hashes, tokens, or real personal data in code, migrations, logs, tests, or docs.
8. Add tests in the same package as the class under test, at the right boundary: service (unit), MVC and security, or PostgreSQL (`*IT`). Do not lower the 80% line-coverage floor.
9. Update OpenAPI annotations for each changed endpoint. Update the README, AGENTS.md, engineering reference, and roadmap only where an owned fact changed.
10. Report exactly which Maven checks ran (`./mvnw verify`, `./mvnw verify -Pintegration -DskipUTs`) and their results. Commit only when asked.

## 1. Why these changes exist

Each item was found by reading the real backend flow while planning the UI. Findings and evidence:

| ID | Finding | Evidence in the code |
| --- | --- | --- |
| BE-1 | A book copy shows only `IN_COLLECTION` or `LOST`. Whether a copy is **currently on loan** is invisible, so a catalog cannot show "available" or "on loan", and the Borrow button cannot be enabled correctly. | `BookResponseDTO` has `copyStatus` only; availability is enforced at checkout by the one-active-loan-per-copy index and a 409. |
| BE-2 | Business failures are distinguishable only by message text. A UI would have to match English strings to tell "copy unavailable" from "borrower has an overdue loan" from "limit reached". | `ErrorResponse` carries `error`, `details`, `statusCode`, `fieldErrors`, `requestId`, no stable code. Several loan failures all return 409. |
| BE-3 | The account directory cannot be searched or filtered. | `GET /app/users` accepts only page, size, sort. |
| BE-4 | No endpoint reports version, build, or commit. | No build-info configuration; Actuator `info` is not exposed on the public port. |
| BE-5 | No audit trail of who changed what. | No audit table through V6; services do not record events. |
| BE-6 | The dashboard's weekly-loans and most-borrowed charts cannot be built from the existing endpoints without fetching every loan. | Loan endpoints return pages of loans only; no aggregation by week or title exists. |

## 2. Work items

### BE-1: Book availability

**Goal.** Expose whether each copy can be borrowed right now, and allow filtering by it.

**Contract.**
- `BookResponseDTO` gains `available` (boolean): `true` when `copyStatus == IN_COLLECTION` and the copy has no `ACTIVE` loan.
- `GET /app/books/query` gains optional `available` (boolean) and `copyStatus` (`IN_COLLECTION` or `LOST`) parameters, combined with the existing filters by AND. Invalid values return 400 through the existing handling.
- `GET /app/books/{id}` and every paginated book list include `available`.

**Steps.**
1. Read `BookController`, `BookService`, `BookRepository`, the book mapper, and the existing query specification or JPQL.
2. Add the filter predicates. Use a `NOT EXISTS` subquery on loans with `status = 'ACTIVE'`; it is served by the existing partial unique index on active loans, so no migration is needed.
3. Compute `available` for a whole page with **one** query for the page's book IDs, not one query per row. Add a repository method returning the set of IDs with an active loan.
4. Map `available` explicitly in the mapper. Keep the checkout rules in `LoanService` as the source of truth; `available` is advisory and may be stale by the time a user clicks, so checkout still returns 409 when it loses a race.
5. Update OpenAPI text for the parameter and field.

**Tests.**
- Service: available, on loan, lost, returned-then-available again.
- MVC: new parameters validated; unauthorized access unchanged.
- PostgreSQL `*IT`: filter correctness against real loans; confirm no N+1 by asserting the query count or a single batched lookup.

**Done when.** A page of books reports correct `available` for mixed states, filters work, and existing book tests pass unchanged.

### BE-2: Machine-readable error codes

**Goal.** Let clients branch on a stable `code` instead of message text.

**Contract.** `ErrorResponse` gains an optional string `code` (null when none applies). Codes are upper snake case and stable once released. Initial set:

| Code | Raised when |
| --- | --- |
| `LOAN_COPY_UNAVAILABLE` | Checkout of a copy that is on loan or lost. |
| `LOAN_BORROWER_OVERDUE` | Borrower has an overdue active loan. |
| `LOAN_LIMIT_REACHED` | Borrower already has 3 active loans. |
| `LOAN_NOT_ACTIVE` | Return or lost-resolution of a loan already in a terminal state. |
| `RESOURCE_HAS_HISTORY` | Deleting a book or user that has loan history (409). |
| `PASSWORD_CURRENT_INCORRECT` | Owner password change with a wrong current password (400). |

**Steps.**
1. Read `LoanConflictException`, the other exception subclasses, and `GlobalExceptionHandler`.
2. Give the base `InvalidRequestException` and conflict types an optional code, set by dedicated subclasses or a constructor argument (AGENTS.md: add subclasses rather than new handler methods).
3. Have the handler copy the code into `ErrorResponse`. Do not change `details`, `error`, `statusCode`, or `fieldErrors`.
4. Add `code` to the OpenAPI schema and list the codes in the API documentation guideline's error section if that guide enumerates error fields.

**Tests.** One service-level test per code; one MVC test proving the JSON body includes `code` and still includes the existing fields; one test proving unrelated errors have no `code`.

**Done when.** Each loan and deletion conflict returns its code and all existing assertions on `details` still pass.

### BE-3: Account directory search and filters

**Goal.** Make the directory usable at more than a handful of accounts.

**Contract.** `GET /app/users` gains optional `role` (`STUDENT`, `FACULTY`, `ADMINISTRATOR`) and `q` (case-insensitive substring over university ID, first name, last name, and email). Filters apply **after** the caller's role scope, so an assistant can never see an account the scope hides, even by filtering for it. A `role` outside the caller's scope returns 403, not an empty page. `LIBRARIAN` is never a valid target filter.

**Steps.**
1. Read `UserController.getAllUsers`, `UserService.getAllUsers`, `UserAccessPolicy`, and the repository query.
2. Add the filters to the repository query or a specification, composing with the scope predicate. Never concatenate user input into JPQL or SQL.
3. Keep the existing allow-list of sort properties.
4. Update OpenAPI text.

**Tests.** Scope matrix test: each caller role crossed with each filter value. Service test that `q` cannot widen scope. MVC test for 403 on an out-of-scope `role`. An `*IT` test for substring matching on PostgreSQL.

**Done when.** The matrix passes and the existing `UserAccessPolicy` matrix test is unchanged.

### BE-4: Build and version information

**Goal.** Let staff see which build is running.

**Contract.** `GET /app/info`, role `ADMINISTRATOR` or `LIBRARIAN`, returns in the standard envelope: `version`, `buildTime`, `commit` (short hash), `environment` (a configured label, for example `production`). No dependency versions, hostnames, paths, or environment variables.

**Steps.**
1. Enable `build-info` and a git properties file in the Maven build. Make the commit available to the Docker build without leaking the `.git` directory into the image (pass it as a build argument or read it in CI), and confirm the CD workflow supplies it.
2. Add a small `info` feature class set following the package layout (controller, service, dto), or place it under `global` if no existing feature fits; choose the smaller footprint and justify it in the PR.
3. Add the route gate in `SecurityConfig` and a security test (401 anonymous, 403 student and faculty, 200 staff).
4. Add `LIBRO_ENVIRONMENT_NAME` to `.env.example` as a commented optional value, with its default in `application.properties` only.

**Tests.** MVC and security tests; a unit test that missing build metadata (local run) yields a stable placeholder rather than a 500.

**Done when.** The image built by CD reports the real commit; a local run reports a placeholder.

### BE-5: Audit trail

**Goal.** Record security-relevant changes: who, what, when.

**Schema (migration V7).** Table `audit_event`: `id`, `occurred_at` (Asia/Manila `LocalDateTime`, consistent with the rest), `actor_university_id` (plain text, **not** a foreign key, so deleting an account never destroys history), `actor_role`, `target_role` (nullable; the role of the affected account, null for book and loan events), `action`, `entity_type`, `entity_id`, `summary`. Indexes on `occurred_at` and on `(entity_type, entity_id)`. No column may hold a password, hash, token, or free-form request body. Check the fresh-database path and the V6-to-V7 upgrade path.

**Events to record.** Account created, profile changed, role-scoped delete, password reset by staff, password changed by owner (no value, only the fact), loan marked lost, book created, book changed, book deleted. Do **not** record reads, logins, or rate-limit hits (metrics cover those).

**Steps.**
1. Create an `audit` feature package using the standard sub-packages.
2. An `AuditService.record(...)` called from the existing services **inside the same transaction** as the change, so an event exists only if the change committed. Keep the call sites one line each.
3. Staff-only read endpoint `GET /app/audit` with pagination (default 12, maximum 100), an allow-listed sort, and filters for actor, entity type, and date range. **Decision (project owner, 2026-11-09): both `LIBRARIAN` and `ADMINISTRATOR` may read, with role scoping that mirrors loans.** Librarians see every event. Library assistants see only events whose `target_role` is `STUDENT` or `FACULTY`, plus book and loan events (which have no target account role); they never see events about assistant or librarian accounts, including password resets and deletions of those accounts. Apply the scope predicate in the repository query before filters and counting, and return 403 for an out-of-scope `actorRole` or `targetRole` filter, as BE-3 does for `role`.
4. Add a retention note in the runbook (no automatic deletion in this plan).

**Tests.** Service test per recorded action asserting one event with the right fields and none containing sensitive data; transaction test proving a rolled-back change leaves no event; a scope matrix test (librarian sees all; assistant never sees events targeting assistant or librarian accounts, even when filtering for them); MVC and security tests (student and faculty get 403); an `*IT` migration test for V6 to V7.

**Done when.** Every listed action produces exactly one event, the read endpoint is role-gated, and no event field can contain a secret.

### BE-6: Loan activity aggregates for dashboard charts

**Why.** The owner wants dashboards and charts ([UI plan 5.8](implementation-plan-ui.md#58-dashboards-and-charts)). Most tiles and charts use existing endpoints (`totalElements` from `size=1` calls, book statistics, genre distribution, overdue list). Two charts cannot be built from them without fetching every loan: loans per week, and most borrowed titles.

**Contract.** Both are read-only `GET`s for `ADMINISTRATOR` and `LIBRARIAN`, in the standard envelope, **role-scoped exactly like `GET /app/loans`** (assistants never count loans of assistant or librarian accounts).
- `GET /app/loans/activity?weeks=12`: one bucket per week, oldest first, with `weekStart` (Monday, Asia/Manila date), `checkouts` (by `borrowedAt`) and `returns` (by `returnedAt`). Weeks with no activity are present with zeros. `weeks` defaults to 12, minimum 1, maximum 52; anything else returns 400.
- `GET /app/loans/top-titles?days=90&limit=5`: titles with the most checkouts in the window, grouped by title and author (copies of one title count together), each with `title`, `author` and `checkouts`. `days` defaults to 90 (maximum 365) and `limit` to 5 (maximum 10).

**Steps.**
1. Read `LoanRepository`, `LoanService.getStaffLoans`, and how the staff scope is derived; reuse that scope predicate rather than copying it.
2. Implement each as one grouped query. Stored values are Manila local time, so week boundaries use the Manila `LibraryTime` clock to compute the window and the database's week truncation on the stored values; do not convert through UTC.
3. Fill empty weeks in the service, not the database.
4. Check each query plan on seeded data. Add a forward-only migration for an index on `loans.borrowed_at` (and `returned_at` if needed) only if the plan shows a scan that matters; keep SQL and the JPA mappings aligned.
5. Return DTOs only; add OpenAPI documentation with examples.

**Tests.** Service tests with a fixed `Clock` for week boundaries (a loan at 23:59 Sunday versus 00:00 Monday Manila), empty weeks, the scope matrix, and tie ordering in top titles; MVC and security tests (401, 403 for students and faculty, 400 for invalid `weeks`); an `*IT` test on PostgreSQL for the grouped queries.

**Done when.** The two charts render from these endpoints on the seeded demo data with correct bucket boundaries, and the scope matrix passes.

## 3. Sequencing and UI dependencies

| Item | Needed by UI phase | Order |
| --- | --- | --- |
| BE-2 error codes | 1 (auth) and 2 (borrow) | First |
| BE-1 availability | 2 (catalog, book detail) | With BE-2 |
| BE-3 directory filters | 5 (user management) | Before UI phase 5 |
| BE-4 build info | 6 (version page) | Before UI phase 6 |
| BE-5 audit trail | 7 (audit log) | Last; read access decided (librarian and assistants, role-scoped) |
| BE-6 loan activity aggregates | 4 (the two time and ranking charts; the rest of the dashboard ships without it) | Before the dashboard is finished |

The UI can start Phase 0 and Phase 1 immediately: they need no backend change.

## 4. Definition of done

- Each item is additive, tested at the right boundary, and documented in OpenAPI.
- `./mvnw verify` and `./mvnw verify -Pintegration -DskipUTs` pass and are reported.
- Flyway V7 (BE-5 only) is forward-only and verified for fresh and upgrade paths.
- AGENTS.md "Current implementation facts", the engineering reference, and the roadmap are updated only for facts that changed.
- No secret or sensitive data enters code, migrations, logs, or tests.
- When all items are built, replace this plan with the as-built facts in those documents and remove this file, following the repository's convention of keeping finished plans in Git history.

## 5. Risks

| Risk | Handling |
| --- | --- |
| `available` shown to a user goes stale | Advisory only; checkout still returns 409 with `LOAN_COPY_UNAVAILABLE`. |
| New filters leak accounts outside role scope | Scope predicate applied first; matrix tests. |
| Audit writes slow or fail the main change | Same transaction, one small insert; failure rolls back the change, which is the safe default. |
| Error codes become de facto API | Treat codes as versioned contract once released; never repurpose one. |
| Commit hash in the image leaks repository detail | Short hash only; no remote URL or branch name. |

## 6. Deferred

Not planned now; recorded so the decision is not lost.

| Item | Why deferred |
| --- | --- |
| Book cover support (an optional ISBN or `coverUrl` field on a book) | The UI ships generated covers ([UI plan 5.7](implementation-plan-ui.md#57-book-covers-without-cover-images)). Real covers need a data-model decision and, if an outside source is used, server-side fetching and caching so patrons' browsers never contact it. `BookCover` already accepts an optional `coverUrl`, so adding it later changes no UI structure. |
| Currency for replacement cost | The API returns a bare decimal; the UI shows no symbol until the owner picks a currency. |
