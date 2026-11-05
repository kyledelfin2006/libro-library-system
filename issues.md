# Libro Library API — Pre-UI / CI-CD / OCI Quality Audit

Audit baseline: 2026-11-05 at commit `d707bd6`. This backlog was refreshed after the deployment-readiness work requested on 2026-11-05; completed findings are removed. Verify implementation against code, migrations, and tests.
Findings are sorted by Priority (P0 → P2), then Effort (S → M → L). Numbering is intentionally absent from headings so the template stays exact; use the titles in "Suggested execution order". Remaining findings carry a `Phase`/`Depends on` line and a `Detailed explanation`; the residual work plan is in section 3.

---

## 1. Repo Snapshot

**Stack.** Single-module Maven project, Java 25, Spring Boot 4.1.0 (Spring MVC, Spring Data JPA/Hibernate, Spring Security + OAuth2 Resource Server for HS256 JWT), PostgreSQL 18, Flyway 13.3.0 (V1–V5), springdoc-openapi 3.1.1, Lombok, JaCoCo 0.8.15. Testcontainers (Postgres) for integration tests.

**Architecture.** Package-by-feature under `app.*`: `auth`, `user`, `book`, `loan`, and `global`. Layering is controller → service → repository with DTOs at the boundary. `SecurityConfig` protects API routes with stateless JWT Bearer authentication; login, signup, and Actuator liveness/readiness probes are public. Pessimistic row locks guard the loan lifecycle.

**Config surface.** `src/main/resources/application.properties` reads datasource settings, the admin Flyway hash, JWT settings, rate limits, CORS origins, forwarded-header strategy, health exposure, and optional first-librarian bootstrap variables. Template: `envFileExample`. No Spring profiles or structured logging config in `main`.

**Tooling present.** `Dockerfile` (multi-stage, non-root JRE image), `docker-compose.yml` (postgres + app), JaCoCo, Failsafe under `-Pintegration`, 10 docs files.
**Tooling absent.** Linter/formatter (Checkstyle/Spotless/etc.).

**Current check results** (Windows, JDK 25, Maven Wrapper 3.9.x, Docker available):

| Check | Command | Result |
| --- | --- | --- |
| Unit/MVC tests | `./mvnw -B test` | Pass (205 tests, 0 failures) |
| Integration tests | `./mvnw -B -Pintegration verify` | Pass (18 tests on PostgreSQL 18 through Testcontainers, including `LoanConcurrencyIT`) |
| Lint / format | — | None configured (see the `.editorconfig` finding) |
| Dependency scan | Trivy in the CI `security` job | Configured; first results appear on the first CI run. Dependabot opens weekly update PRs. |

---

## 2. Findings

### [P2][S] Add an index for loan lookups by book and status
Phase: 4 (Hardening). Depends on: CI integration job.
Detailed explanation: PostgreSQL does not automatically index foreign-key columns. The partial unique index in V4 covers only `status = 'ACTIVE'` rows, but each checkout also asks whether a LOST loan exists for the book, and user deletion must check `lost_by_user_id`. As loan history grows these become table scans while a row lock is held, lengthening lock waits for concurrent borrowers. A forward-only V6 migration with two small indexes fixes it; V1-V5 must not be edited, and Hibernate validation must still pass on fresh and upgraded databases.
Category: Performance & resource use
Location: `src/main/resources/db/migration/V4__add_loans_and_copy_status.sql:43-52`, `src/main/java/app/loan/repository/LoanRepository.java:49`
Problem: `existsByBook_IdAndStatus(bookId, LOST)` (called on every borrow) has no usable index: V4 only has a partial unique index on `book_id WHERE status='ACTIVE'`, and PostgreSQL does not index FK columns automatically.
Impact if not fixed: Each checkout scans more of `loans` as history grows; `lost_by_user_id` FK checks on user delete are also unindexed.
Recommended fix: Add a forward-only V6 migration with the missing indexes.
Step-by-step plan (for an agent):
1. Create `V6__index_loan_book_status.sql` with `CREATE INDEX idx_loans_book_status ON loans (book_id, status);` and `CREATE INDEX idx_loans_lost_by ON loans (lost_by_user_id) WHERE lost_by_user_id IS NOT NULL;`.
2. Run `./mvnw -B -Pintegration verify` (Hibernate validate must pass).
3. Verification: `EXPLAIN` on `SELECT 1 FROM loans WHERE book_id=? AND status='LOST'` uses the new index (add as a documented check or IT assertion).
4. Update docs that say "V5 is the latest" (`AGENTS.md`, `engineering-reference.md`, `docs/loan.md`).
Acceptance criteria:
* [ ] V6 applies on both fresh and V5-upgraded databases.
* [ ] Docs name V6 as latest.
Files likely touched: `src/main/resources/db/migration/V6__index_loan_book_status.sql`, `AGENTS.md`, `guiding-docs/engineering-reference.md`, `docs/loan.md`
Risks / notes: Never edit V1–V5. Pair with the V3→V4 upgrade-path verification if both ship together.

### [P2][M] Verify the V3→V4 migration upgrade path on PostgreSQL
Phase: 4 (Hardening). Depends on: CI integration job.
Detailed explanation: Fresh installs and V4-to-V5 upgrades are tested, but a database that already contains users and books at V3 and then receives V4 (new `copy_status` column with a default, new loan tables and constraints) is not. Migrations behave differently on populated tables than on empty ones, and the first time this path runs should not be a production upgrade. A test that migrates to V3 with Flyway's target setting, inserts rows, then migrates to latest and checks data and schema validation closes the gap.
Category: Testing
Location: `guiding-docs/TODO.md:46`, `src/main/resources/db/migration/V3__add_librarian_role.sql`, `V4__add_loans_and_copy_status.sql`
Problem: Only fresh V1→V5 and V4→V5 paths are verified; a database that already holds data at V3 (users/books) migrating to V4 (adds `copy_status NOT NULL DEFAULT`, new tables) is untested.
Impact if not fixed: First upgrade of a populated instance on OCI is the first test of that path.
Recommended fix: Add an IT that migrates to V3, inserts rows, then migrates to latest and validates mapping.
Step-by-step plan (for an agent):
1. In a new `MigrationUpgradeIT` use Testcontainers Postgres and the Flyway API (`Flyway.configure().target("3")`, with the `adminPasswordHash` placeholder set to a test-only BCrypt hash).
2. Insert a user and a book via SQL, migrate to latest, assert existing rows survive, `copy_status='IN_COLLECTION'`, and `replacement_cost` equals the old `price`.
3. Tick the TODO item.
4. Verification: `./mvnw -B -Pintegration verify -Dit.test=MigrationUpgradeIT`.
Acceptance criteria:
* [ ] Test proves data survives V3→latest and Hibernate validation passes.
Files likely touched: `src/test/java/integration/MigrationUpgradeIT.java`, `guiding-docs/TODO.md`
Risks / notes: Needs Docker; use a fresh container, not the shared Spring one.

---

## 3. Implementation plan

The table below tracks the remaining backlog by the original audit phases. Everything in phases 0-3 except the response-shape decision is complete and verified by the unit and PostgreSQL integration suites. Never edit applied migrations (V1-V5) and never run `docker compose down -v`.

| Phase | Goal | Status |
| --- | --- | --- |
| 0. Baseline | A buildable, green repository | Complete |
| 1. Safety net | Automated checks and basic correctness | Complete (CI, runtime properties, H2 removed, client-error handling) |
| 2. Deployability | A container OCI can run and probe | Complete (non-root image, probes, compose hardening, trusted-proxy IP, timezone, admin-hash default) |
| 3. UI readiness | A stable contract the UI can code against | Complete |
| 4. Hardening | Prove invariants and watch dependencies | Remaining: V3-to-V4 upgrade test, loans index (V6) |
| 5. Cleanup | Consistency and documentation | Complete |

Cross-phase rules:
1. **Order inside a phase** follows the "Depends on" line of each issue; the flat list in section 4 is a dependency-valid linear order of the same work.
2. **Settled decisions**: timezone is Asia/Manila, every success response uses the `ApiResponse` envelope, list endpoints are paginated, Swagger UI is off by default and enabled by `LIBRO_API_DOCS_ENABLED=true`, and the CI platform is GitHub Actions.
3. **Docs travel with code.** Each pull request updates only the facts it changes (README, AGENTS.md, engineering reference, roadmap, OpenAPI), per the repository's contribution rules.
4. **Reporting.** Each pull request states exactly which Maven checks ran (`test`, `-Pintegration verify`), as AGENTS.md requires. Integration checks need Docker.
5. **Rollback.** All Phase 0-3 changes are code or config and revert with the commit; the only schema change (V6) is additive and index-only.
6. **In-code documentation standard (applies to every issue).** Documentation lives in the code it describes, as AGENTS.md requires, and is part of each issue's definition of done:
   * **Java:** every method added or materially changed gets a method-level JavaDoc (purpose, business rule, `@param`, `@return`, `@throws`) and line comments before each meaningful step; every new class gets a class-level JavaDoc stating its responsibility and its place in the request path; every `@ConfigurationProperties` record documents each property, its default, and the environment variable that feeds it.
   * **Configuration (`application.properties`, `.env.example`, `docker-compose.yml`):** each new key has a comment above it giving what it controls, the allowed values, the default, and the deployment context in which to change it.
   * **Dockerfile:** each instruction or stage has a comment explaining why it exists (layer-caching order, non-root user, JVM flags, health check).
   * **CI workflow YAML:** the file opens with a purpose header, and every job, trigger, permission, and non-obvious step carries a comment (see the reference workflow in the CI issue).
   * **SQL migrations:** each new statement is commented with its purpose and any index or constraint rationale.
   * **Tests:** each new test class and method has a JavaDoc or comment stating the behavior or invariant it proves.
   * **Cross-references:** where a change alters a fact owned by README, AGENTS.md, the engineering reference, OpenAPI annotations, or the roadmap, update that artifact in the same pull request.

---

## 4. Suggested execution order (flat list)

1. Add an index for loan lookups by book and status
2. Verify the V3→V4 migration upgrade path on PostgreSQL

Rationale: both remaining items are database-level hardening. Completed findings are removed from this file; git history records them.

---

## 5. Deferred / Post-Launch

* **Distributed rate limiting.** The in-memory limiter is per process and resets on restart; its eviction policy drops the oldest tracked key when the table is full (`PublicRouteRateLimitInterceptor.java:99-103`), so an attacker rotating many addresses can flush others' counters. Move throttling to the OCI API Gateway/WAF or a shared store once more than one instance runs.
* **Token revocation.** Role changes, password changes, and account deletion do not invalidate issued 15-minute tokens (documented). `AGENTS.md` forbids adding refresh tokens/denylists incidentally — needs a separate decision.
* **Account enumeration.** Signup returns distinct "University ID/Email is already registered" messages and `GET /app/users/{id}` distinguishes 404 from 403. Consider uniform responses after launch; rate limiting limits current exposure.
* **Retire the bootstrap administrator.** V2 creates a well-known `0000-0000` / `admin@library.local` ADMINISTRATOR account on every fresh database. After librarian provisioning exists, add a forward-only migration or runbook step to disable it; do not edit V2.
* **Search performance.** `queryBooks` uses `LOCATE(LOWER(..))` and `findBy...ContainingIgnoreCase`, which cannot use B-tree indexes. Introduce `pg_trgm` GIN indexes only if catalogue size warrants it.
* **`UserService` size.** At ~800 lines it mixes account CRUD and authorization policy; extract an `AccountAccessPolicy` component when next touching authorization rules.
* **Independent security review and gateway/WAF/TLS setup** (already tracked in `guiding-docs/TODO.md`): TLS termination, direct-access restriction, and monitoring for login/signup before public exposure.
* **Secrets management.** Move JWT key, DB password, and bootstrap hashes to OCI Vault when the deployment target is chosen; rotate the JWT key with a documented procedure (currently a single HS256 key with no key ID).
* **Code-style tooling.** Spotless/Checkstyle enforcement once the team agrees on a style; not blocking.
