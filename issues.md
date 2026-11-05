# Libro Library API — Pre-UI / CI-CD / OCI Quality Audit

Audit baseline: 2026-11-05 at commit `d707bd6`. This backlog was refreshed after the deployment-readiness work requested on 2026-11-05; completed findings are removed. Verify implementation against code, migrations, and tests.
Findings are sorted by Priority (P0 → P2), then Effort (S → M → L). Numbering is intentionally absent from headings so the template stays exact; use the titles in "Suggested execution order". Remaining findings carry a `Phase`/`Depends on` line and a `Detailed explanation`; the residual work plan is in section 3.

---

## 1. Repo Snapshot

**Stack.** Single-module Maven project, Java 25, Spring Boot 4.1.0 (Spring MVC, Spring Data JPA/Hibernate, Spring Security + OAuth2 Resource Server for HS256 JWT), PostgreSQL 18, Flyway 13.3.0 (V1–V5), springdoc-openapi 3.1.1, Lombok, JaCoCo 0.8.15. Testcontainers (Postgres) for integration tests.

**Architecture.** Package-by-feature under `app.*`: `auth`, `user`, `book`, `loan`, and `global`. Layering is controller → service → repository with DTOs at the boundary. `SecurityConfig` protects API routes with stateless JWT Bearer authentication; login, signup, and Actuator liveness/readiness probes are public. Pessimistic row locks guard the loan lifecycle.

**Config surface.** `src/main/resources/application.properties` reads datasource settings, the admin Flyway hash, JWT settings, rate limits, CORS origins, forwarded-header strategy, health exposure, and optional first-librarian bootstrap variables. Template: `envFileExample`. No Spring profiles or structured logging config in `main`.

**Tooling present.** `Dockerfile` (multi-stage, non-root JRE image), `docker-compose.yml` (postgres + app), JaCoCo, Failsafe under `-Pintegration`, 10 docs files, an earlier read-only security report (`reports/read-only.report.md`).
**Tooling absent.** Linter/formatter (Checkstyle/Spotless/etc.), metrics endpoint, `.editorconfig`.

**Current check results** (Windows, JDK 25, Maven Wrapper 3.9.x, Docker available):

| Check | Command | Result |
| --- | --- | --- |
| Unit/MVC tests | `./mvnw -B test` | Pass (205 tests, 0 failures) |
| Integration tests | `./mvnw -B -Pintegration verify` | Pass (18 tests on PostgreSQL 18 through Testcontainers, including `LoanConcurrencyIT`) |
| Lint / format | — | None configured (see the `.editorconfig` finding) |
| Dependency scan | Trivy in the CI `security` job | Configured; first results appear on the first CI run. Dependabot opens weekly update PRs. |

---

## 2. Findings

### [P2][S] Fix stale audit documentation and OpenAPI license metadata
Phase: 5 (Cleanup). Depends on: other issues, so statuses reflect final state.
Detailed explanation: AGENTS.md treats prose that disagrees with code as a defect. The tracked security report still says login and signup have no rate limiting, which was implemented afterwards. The OpenAPI license string says no license is declared while the repository carries an MIT license. These are low risk individually but mislead reviewers and API consumers, so fix them when statuses are final.
Category: Developer experience
Location: `reports/read-only.report.md` (stale rate-limit statements), `src/main/java/app/global/config/OpenApiConfig.java:27` ("no distribution license declared" vs MIT `LICENSE`)
Problem: A tracked report contradicts the code and the OpenAPI license string contradicts `LICENSE`.
Impact if not fixed: Misleading information for reviewers and API consumers; confusion about which ignore rules apply.
Recommended fix: Mark the report as a dated historical snapshot with a status table and correct the license metadata.
Step-by-step plan (for an agent):
1. Add a status table at the top of `reports/read-only.report.md` mapping each finding to fixed/open.
2. Set `@License(name = "MIT", url = "https://opensource.org/licenses/MIT")` in `OpenApiConfig` and update any test asserting the string (`OpenApiMvcTest`).
3. Keep ignore rules documented and verify no stale config claims remain.
4. Verification: `git check-ignore -v .env target pom.xml` shows only intended matches; `./mvnw -B test`.
Acceptance criteria:
* [ ] No tracked doc claims behavior contradicted by code.
* [ ] OpenAPI license matches `LICENSE`.
Files likely touched: `reports/read-only.report.md`, `src/main/java/app/global/config/OpenApiConfig.java`, `.gitignore`, `src/test/java/unit/global/OpenApiMvcTest.java`
Risks / notes: Keep OpenAPI description claims aligned with `SecurityConfig` after other issues change them.

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

### [P2][S] Tidy Maven build configuration and enforce toolchain versions
Phase: 5 (Cleanup). Depends on: test baseline (to measure coverage).
Detailed explanation: The POM carries overlapping or implicit settings: Lombok's version is set both by a property and a literal, the Java level is set through `source`, `target`, and plugin configuration instead of `release`, and nothing stops someone building with an older JDK or Maven and getting confusing compiler errors. JaCoCo produces a report but cannot fail the build when coverage drops. The Enforcer plugin and a JaCoCo `check` goal make the toolchain and quality bar explicit and automatic. Set the coverage floor from a measured baseline after the test fix, not before.
Category: Dependency hygiene
Location: `pom.xml:18-23,128-134,174-189,192-213,218-237`
Problem: Lombok is declared in two places (`<lombok.version>` and a hard-coded `1.18.46` at line 132; Boot already manages it), compiler `source`/`target` are set three ways (properties plus plugin config) rather than `maven.compiler.release`, no `maven-enforcer-plugin` requires Java 25/Maven 3.9, JaCoCo has no `check` goal/threshold, `springdoc` and `flyway.version` override the BOM without a comment, and `spring-boot-devtools` is `optional` without `runtime` scope.
Impact if not fixed: Version drift, a build on the wrong JDK compiles with confusing errors, and coverage can regress unnoticed.
Recommended fix: Consolidate versions, use `release`, add Enforcer and a JaCoCo coverage floor.
Step-by-step plan (for an agent):
1. Remove the hard-coded Lombok `<version>` (keep Boot-managed or the property, not both); set `<maven.compiler.release>25</maven.compiler.release>` and drop `source`/`target`.
2. Add `maven-enforcer-plugin` with `requireJavaVersion [25,)` and `requireMavenVersion [3.9,)`.
3. Add a JaCoCo `check` execution (start at the current measured line/branch coverage, e.g. rounded down) bound to `verify`.
4. Add a short comment explaining each BOM override (`flyway.version`, springdoc).
5. Verification: `./mvnw -B verify` passes; `./mvnw -B verify -Djava.version=17` style mismatch fails fast under a wrong JDK.
Acceptance criteria:
* [ ] One source of truth per version.
* [ ] Build fails on wrong JDK/Maven.
* [ ] Coverage floor enforced.
Files likely touched: `pom.xml`
Risks / notes: Measure coverage after the test-properties fix; the current number is distorted by the 40 failing tests.

### [P2][S] Add `.editorconfig` for editor consistency
Phase: 5 (Cleanup); schedule early if the Maven wrapper lands first. Depends on: nothing.
Detailed explanation: `.gitattributes` now pins line endings for shell scripts, wrapper scripts, and Dockerfile content. `.editorconfig` is still absent, so editor indentation, charset, and final-newline defaults vary between contributors.
Category: Developer experience
Location: repository root (`.editorconfig` is absent)
Problem: No editor-level charset, indentation, and final-newline defaults; no Checkstyle/Spotless formatter is configured.
Impact if not fixed: Editors keep producing avoidable formatting differences.
Recommended fix: Add `.editorconfig`; optionally add Spotless (check-only) later.
Step-by-step plan (for an agent):
1. Create `.gitattributes`: `* text=auto eol=lf`, `*.cmd text eol=crlf`, `*.png binary`.
2. Create `.editorconfig` (UTF-8, LF, 4-space Java, final newline).
3. Run `git add --renormalize .` and review the diff size; commit separately.
4. Verification: `git ls-files --eol | grep -v "i/lf"` lists only intentionally CRLF files.
Acceptance criteria:
* [ ] Files committed and the tree normalizes cleanly.
Files likely touched: `.gitattributes`, `.editorconfig`
Risks / notes: A renormalize commit can touch many files — do it in an isolated commit with no other changes.

### [P2][S] Replace `jakarta.transaction.Transactional` in `UserService` and mark read paths read-only
Phase: 5 (Cleanup). Depends on: nothing.
Detailed explanation: `UserService` imports the Jakarta transaction annotation while the other services use Spring's. Spring honors both, but the Jakarta version has no `readOnly` flag and different rollback defaults, so the codebase has two transaction conventions. Read methods have no transaction at all, so multi-query reads are not consistent and miss Hibernate's read-only optimizations. Aligning on Spring's annotation is mechanical and low risk, and matters more once open-in-view is disabled.
Category: Structure & modularity
Location: `src/main/java/app/user/service/UserService.java:15,67,80,97,184,255,347,556`
Problem: `UserService` uses `jakarta.transaction.Transactional` while `BookService`/`LoanService` use Spring's. The Jakarta annotation lacks `readOnly` and Spring-specific rollback semantics; `getAllUsers`, `getUserByUniversityId` have no transaction at all.
Impact if not fixed: Inconsistent transaction behavior across services and no read-only optimization; surprises when mapping lazy data later.
Recommended fix: Use `org.springframework.transaction.annotation.Transactional` everywhere and add `@Transactional(readOnly = true)` on read methods.
Step-by-step plan (for an agent):
1. Change the import in `UserService`.
2. Add `@Transactional(readOnly = true)` to `getAllUsers` and `getUserByUniversityId`.
3. Verification: `./mvnw -B test -Dtest=UserServiceTest` and `./mvnw -B -Pintegration verify -Dit.test=UserPersistenceIT`.
Acceptance criteria:
* [ ] No `jakarta.transaction` imports remain in `src/main`.
* [ ] Tests pass.
Files likely touched: `src/main/java/app/user/service/UserService.java`
Risks / notes: Behavior is equivalent for the write methods.

### [P2][S] Rename `envFileExample` to `.env.example`
Phase: 5 (Cleanup, last). Depends on: all issues that add environment variables.
Detailed explanation: `.env.example` is the convention that editors, secret scanners, and new contributors recognize. The current name is not recognized by common tooling. The file now includes CORS, forwarded-header, and librarian-bootstrap variables, but should be renamed and kept as the single inventory of runtime configuration. It must contain only placeholders.
Category: Developer experience
Location: `envFileExample` (referenced in 8 places in README/compose/docs), `.dockerignore:20`
Problem: The non-standard filename is not recognized by tooling and secret scanners.
Impact if not fixed: New contributors and the OCI deployment lack a complete variable inventory.
Recommended fix: Rename and complete the file; update references.
Step-by-step plan (for an agent):
1. `git mv envFileExample .env.example`; run the repo-wide replacement (`grep -rn envFileExample .`).
2. Preserve the existing deployment variables and safe placeholder values during the rename.
3. Ensure `.dockerignore` still excludes `.env` and `.env.*` but not needed docs.
4. Verification: `grep -rn envFileExample .` returns nothing; `docker compose config` resolves with a copy of the file as `.env`.
Acceptance criteria:
* [ ] A single `.env.example` file lists every environment variable.
Files likely touched: `.env.example`, `README.md`, `docker-compose.yml`, `Dockerfile`, `.dockerignore`, `guiding-docs/engineering-reference.md`
Risks / notes: Do this after the other config issues to avoid repeated edits. The example must contain no real secrets.

### [P2][M] Pick one success-response shape (envelope vs bare DTO) before the UI is written
Phase: 3 (UI readiness) if the owner agrees; otherwise defer. Depends on: pagination issue, test baseline.
Detailed explanation: Some endpoints return `{success,message,data}` and others return the bare DTO, even for the same resource (a user is bare on GET but wrapped on PATCH). The UI would need per-call unwrapping and a generated client would model two shapes. Changing this after the UI exists is a breaking change for every call site, so the cost is lowest now. It is a product decision, hence P2 and the instruction to choose the convention with the owner first and record it in the API documentation guideline.
Category: Structure & modularity
Location: `src/main/java/app/book/controller/BookAPI.java` (`/stats`, `/search`, `/{id}`, `/all` return bare DTOs; `/add`, `/{id}` PATCH/PUT/DELETE, `/stats/count` return `ApiResponse`), `src/main/java/app/user/controller/UserAPI.java:129-142` (bare) vs the rest (envelope), `src/main/java/app/loan/controller/LoanAPI.java` (mixed)
Problem: The same resource type is returned in different shapes depending on the operation (e.g. `GET /app/users/{id}` returns a bare profile; `PATCH` returns `{success,message,data}`).
Impact if not fixed: The UI client needs per-endpoint unwrapping logic and a generated client will be awkward; changing it after the UI exists is a breaking change.
Recommended fix: Adopt a rule: reads return bare DTOs/pages, mutations return the bare updated resource (or 204), and drop the envelope — or the reverse; apply it uniformly.
Step-by-step plan (for an agent):
1. Choose the convention with the owner and write it in `docs/api-documentation-guideline.md`.
2. Change controllers and `@Operation`/`@ApiResponse` schemas to match; keep `ErrorResponse` for all errors.
3. Update `BookApiMvcTest`, `UserApiMvcTest`, `LoanSecurityMvcTest`, `OpenApiMvcTest`, README examples.
4. Verification: `./mvnw -B test`; `grep -rn "ApiResponse<" src/main/java/app/*/controller` shows only the chosen usage.
Acceptance criteria:
* [ ] One documented, uniformly applied success shape.
* [ ] All tests and README samples updated.
Files likely touched: `src/main/java/app/book/controller/BookAPI.java`, `src/main/java/app/user/controller/UserAPI.java`, `src/main/java/app/loan/controller/LoanAPI.java`, `src/main/java/app/global/responses/ApiResponse.java`, tests, `README.md`, `docs/api-documentation-guideline.md`
Risks / notes: Breaking change — acceptable only because there is no UI yet.

### [P2][S] Expose basic metrics (Micrometer/Prometheus) behind the private management port
Phase: 4 (Hardening). Depends on: Actuator health issue.
Detailed explanation: With no metrics there is no evidence for choosing an OCI shape, spotting slow endpoints, or noticing a credential-stuffing attack. Micrometer's Prometheus registry provides request latency, JVM, and connection-pool metrics for free once Actuator exists, and a counter on rate-limit rejections gives a direct abuse signal. The metrics endpoint must sit on a separate management port that is not published, so it cannot be reached from the internet.
Category: MVP additions
Location: `pom.xml` (no Actuator/Micrometer); `application.properties`
Problem: No request rate/latency/error metrics, JVM or HikariCP pool gauges, or login-failure counters.
Impact if not fixed: No data to size the OCI shape or alert on login abuse.
Recommended fix: After the Actuator issue, add `micrometer-registry-prometheus` and expose `/actuator/prometheus` only on a private management port.
Step-by-step plan (for an agent):
1. Add `io.micrometer:micrometer-registry-prometheus`.
2. Set `management.server.port=${MANAGEMENT_SERVER_PORT:9091}` and `management.endpoints.web.exposure.include=health,prometheus`; do not publish 9091 in compose.
3. Increment a `Counter` for rate-limit rejections in `PublicRouteRateLimitInterceptor` (inject `MeterRegistry`).
4. Verification: `curl localhost:9091/actuator/prometheus` (from inside the network) lists `http_server_requests_seconds` and the counter.
Acceptance criteria:
* [ ] Metrics reachable on the private port only, and not through the public port.
Files likely touched: `pom.xml`, `src/main/resources/application.properties`, `src/main/java/app/auth/ratelimit/PublicRouteRateLimitInterceptor.java`, `docker-compose.yml`
Risks / notes: Depends on the Actuator health endpoint issue. Do not add `/actuator/**` to `permitAll`.

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
| 3. UI readiness | A stable contract the UI can code against | Complete except the response-shape decision below |
| 4. Hardening | Prove invariants and watch dependencies | Remaining: V3-to-V4 upgrade test, loans index (V6), metrics |
| 5. Cleanup | Consistency and documentation | Remaining: POM tidy and enforcer, `.editorconfig`, Spring `@Transactional`, doc drift, `.env.example` |

Cross-phase rules:
1. **Order inside a phase** follows the "Depends on" line of each issue; the flat list in section 4 is a dependency-valid linear order of the same work.
2. **Decision still needed from the owner before the UI is written**: the response-shape convention (envelope vs bare DTO). Settled: timezone is Asia/Manila, list endpoints are paginated, Swagger UI is public but switchable, and the CI platform is GitHub Actions.
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

1. Pick one success-response shape (envelope vs bare DTO) before the UI is written
2. Tidy Maven build configuration and enforce toolchain versions
3. Add an index for loan lookups by book and status
4. Verify the V3→V4 migration upgrade path on PostgreSQL
5. Replace `jakarta.transaction.Transactional` in `UserService` and mark read paths read-only
6. Expose basic metrics (Micrometer/Prometheus) behind the private management port
7. Add `.editorconfig` and `.gitattributes` to stabilize line endings and formatting
8. Fix doc/config drift (stale audit report, license text, `.gitignore` oddities)
9. Rename `envFileExample` to `.env.example` and add deployment-facing variables

Rationale: settle the response shape before UI work starts, then complete hardening and cleanup. Completed findings are removed from this file; git history records them.

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
