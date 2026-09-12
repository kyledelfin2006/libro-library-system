# Gap Report — Libro: Library API System

| Report attribute | Value |
|---|---|
| Scope | Repository-level code and architecture assessment |
| Status | Active; gaps are grouped by architectural layer |
| Last updated | September 9, 2026 |
| Register policy | Detailed sections contain active gaps only; resolved IDs move to the resolution register and are never reused |
| Counting basis | Severity totals include active gaps only; resolved gaps remain traceable in the portfolio summary |

---

## 1. Validation & Error Handling

| # | Gap | Location | Impact | Why it matters |
|---|-----|----------|--------|-----------------|
| 1.4 | **Input trimming is inconsistent across endpoints** | `BookService.patchBook` (lines 133–141, `.trim()` applied) vs. `BookService.addBook`/`replaceBook` (no `.trim()`) | PATCH trims string fields before setting them on the entity; POST and PUT do not trim | A client sending `"title": "  Dune  "` on POST/PUT stores the padded value, while PATCH strips it. This behavioral inconsistency is undocumented and could confuse API consumers. |
| 1.5 | **No `@Version`/optimistic locking on entity** | `app.book.entity.Book` | Concurrent updates to the same book can silently overwrite each other (lost update) | Without `@Version`, two simultaneous PUT/PATCH requests on the same `id` will not be detected. The last commit wins. For a library API this may be acceptable, but it's a gap for data integrity under concurrent access. |

---

## 2. Security

| # | Gap | Location | Impact | Why it matters |
|---|-----|----------|--------|-----------------|
| 2.1 | **All endpoints are public** | `app.auth.SecurityConfig` (lines 14–17) — `anyRequest().permitAll()` | Any client can create, read, update, or delete books without authentication | This is documented as an intentional development-stage posture, but it means there is zero access control. If exposed beyond localhost, anyone can modify the entire library catalog. |
| 2.2 | **CSRF disabled with no authentication** | `app.auth.SecurityConfig` (line 16) — `.csrf(AbstractHttpConfigurer::disable)` | No CSRF protection, but also no cookie-based auth to attack | This is safe-by-coincidence: CSRF attacks target cookie-authenticated sessions, and since nothing is authenticated, there's nothing to forge. However, if authentication is introduced later without re-enabling CSRF (for token-based auth), reverting CSRF to disabled would silently re-open the attack surface. |
| 2.3 | **No rate limiting** | `app.book.controller.BookAPI` / `application.properties` | Any client can exhaust the database or memory with rapid requests (e.g., large pagination, brute-force search) | Spring Security is present but no filter or interceptor throttles request frequency. An attacker could issue thousands of search or pagination requests to degrade service. |
| 2.4 | **No request body size limits** | `application.properties` — no `spring.servlet.multipart.max-request-size` or similar | A client could send an arbitrarily large JSON payload to exhaust memory | Spring Boot defaults to 1 MB for multipart, but for pure JSON (`application/json`) the default is unlimited. A large-enough payload could cause an `OutOfMemoryError`. |

---

## 3. API Design & Consistency

| # | Gap | Location | Impact | Why it matters |
|---|-----|----------|--------|-----------------|
| 3.1 | **Inconsistent success response envelopes** | `BookAPI` — various endpoints | `POST /add`, `PUT`, `PATCH`, `DELETE` return `ApiResponse<T>`; `GET /all` returns `Page<BookResponseDTO>`; `GET /stats` returns `LibraryStatisticsDTO`; `GET /search` returns `List<BookResponseDTO>`; `GET /genre` returns `Map<String, Long>` | API clients must handle a different response shape depending on which endpoint they call. There is no single envelope contract. AGENTS.md documents this as "current behavior; do not silently normalize it" but it remains a consumer friction point. |
| 3.2 | **No API versioning** | `BookAPI` `@RequestMapping("/app/books")` | No version prefix or header-based versioning strategy | Once the API is consumed by external clients, any response-shape or endpoint change becomes a breaking change with no parallel-versioning path. |
| 3.3 | **Timestamp uses epoch milliseconds** | `ApiResponse` (line 7), `ErrorResponse` (line 7) — `System.currentTimeMillis()` | `timestamp` field is a raw epoch-long, not ISO-8601 | Epoch milliseconds are machine-readable but not human-readable. ISO-8601 would align with JSON conventions and improve developer ergonomics. |
| 3.4 | **Search/budget endpoints return unbounded lists** | `BookAPI.searchBooks`, `BookAPI.budgetBooks` | Returns `List<BookResponseDTO>` with no pagination | If the catalog grows to thousands of books, a broad search (`author=an`) could return a massive list and consume server memory. Only `/all` supports pagination. |
| 3.5 | **No optimistic concurrency control headers** | `BookAPI` PUT/PATCH endpoints | Two clients editing the same book simultaneously will have one overwrite silently | No `ETag`, no `If-Match` header check, no `@Version`-based 412 response |
| 3.6 | **No caching headers on GET endpoints** | `BookAPI` all `@GetMapping` methods | Clients re-fetch the same data every time; no `ETag`, `Cache-Control`, or `Last-Modified` | For a read-heavy library catalog, caching could dramatically reduce database load. Every page refresh triggers fresh queries. |
| 3.7 | **Health endpoint returns `ApiResponse<Map<String, Boolean>>`** | `BookAPI.healthCheck` (lines 40–44) | The health response shape (`success`, `message`, `data`, `timestamp`) wraps a simple boolean map | This doesn't conform to common health-check conventions (e.g., Spring Boot Actuator's `/health` format). Orchestration tools like Kubernetes or Docker health checks expect specific structures. |
| 3.8 | **Genre distribution returns bare `Map<String, Long>`** | `BookAPI.getGenre` (line 122) | A raw `Map` with no envelope, no ordering guarantee, and no pagination | This is the most inconsistent response shape in the API — a bare map while every other endpoint wraps data. |

---

## 4. Testing & Coverage

| # | Gap | Location | Impact | Why it matters |
|---|-----|----------|--------|-----------------|
| 4.1 | **No integration tests** | `src/test/` contains only unit tests | 0 tests verify controller routing, JSON serialization, JPA query correctness, Flyway migrations, or real HTTP behavior | The AGENTS.md testing section explicitly states: "Current coverage is predominantly unit-level; HTTP, JPA, migration, security, and container paths lack automated integration coverage." Mockito tests verifying "no save call" document intent but do not prove dirty checking works in a real persistence context. |
| 4.2 | **No controller/MVC tests** | No `@WebMvcTest`, no `MockMvc` setup anywhere | Route mapping, path-variable binding, query-parameter parsing, `@Valid` triggering, HTTP status codes, and JSON response serialization are unverified | A typo in a `@RequestMapping` or `@RequestParam` would compile fine but fail at runtime. There are zero tests that send an HTTP request through the controller layer. |
| 4.3 | **No test profile / H2 configuration** | `pom.xml` has H2 dependency (line 51–55); `application.properties` has no H2 datasource | The H2 dependency exists but cannot be used for tests without a profile configuration | AGENTS.md notes: "The current default configuration still requires explicit `SPRING_DATASOURCE_*` values and does not define an H2 profile. Therefore, the dependency alone does not make the application or tests automatically run against H2." |
| 4.4 | **DELETE endpoint success returns `ApiResponse<Void>` with `data: null`** | `BookAPI.deleteBook` (line 92) | Returns `{"success":true,"message":"Book deleted successfully","data":null,"timestamp":...}` | The `ApiResponse(boolean, String)` constructor (line 9) is used, which sets `data = null`. This is a valid pattern, but clients must handle a nullable `data` field, which is less clean than omitting it entirely. |
| 4.5 | **No test verifies error response JSON serialization** | `GlobalExceptionHandlerTest` calls handler methods directly, not through Spring MVC | The tests verify that `ErrorResponse` objects are constructed correctly, but not that they serialize to the expected JSON shape | A Jackson annotation change (e.g., adding `@JsonIgnore`, changing a getter) would break the API contract without being caught by tests. |

---

## 5. Database & Migrations

| # | Gap | Location | Impact | Why it matters |
|---|-----|----------|--------|-----------------|
| 5.1 | **Missing semicolon on V1 migration** | `../src/main/resources/db/migration/V1__create_books_table.sql` (line 14) | `CREATE INDEX idx_created_at ON books(created_at)` lacks a trailing semicolon | PostgreSQL's `psql` / Flyway may handle this, but it's a syntax inconsistency. Other SQL dialects or import tools might fail. |
| 5.2 | **No `CHECK (price > 0)` constraint** | V1 migration (line 6: `price DECIMAL(10,2) NOT NULL`) | The database accepts zero or negative prices if data is inserted directly | AGENTS.md explicitly documents this: "The database enforces `NOT NULL` for price but not a positive-value check; application validation is the current positive-price guard." If the application is bypassed (direct DB access, migration, bulk import), invalid prices enter the system. |
| 5.4 | **No connection pool configuration** | `application.properties` — no HikariCP settings | Uses Spring Boot defaults (max 10 connections, no custom pool name) | Adequate for development, but no visibility into pool saturation in production. No metrics exposure for pool utilization. |

---

## 6. Infrastructure & Deployment

| # | Gap | Location | Impact | Why it matters |
|---|-----|----------|--------|-----------------|
| 6.1 | **Dockerfile is not multi-stage and requires pre-built JAR** | `Dockerfile` (line 3: `COPY target/*.jar app.jar`) | `docker compose build` fails if `mvn package` hasn't been run first | AGENTS.md notes: "The Dockerfile is not a multi-stage build." This creates a two-step build process that is error-prone for CI/CD pipelines and new contributors. A multi-stage Dockerfile would `mvn package` in the builder stage and copy the resulting JAR. |
| 6.3 | **`envFileExample` template uses placeholder defaults** | `envFileExample` (lines 1–3) | Values like `libraryDb`, `change_role`, `change_me` are not production-safe | While the file is a template (not committed credentials), the default values are weakly suggestive rather than explicitly marked as "replace-me." |
| 6.4 | **No production profile or externalized configuration** | `application.properties` has no profile-specific files | Same config for dev and prod — no way to tune database pool size, Flyway locations, or logging levels per environment | Spring Boot profiles (`application-prod.properties`, `application-dev.properties`) would allow environment-specific tuning without code changes. |
| 6.5 | **DevTools dependency included in all builds** | `pom.xml` (lines 64–69) | Spring Boot DevTools refreshes the application on classpath changes — useful for development but unnecessary (and slightly insecure) in production | Should be scoped or conditionally excluded for production images. |

---

## 7. Observability & Monitoring

| # | Gap | Location | Impact | Why it matters |
|---|-----|----------|--------|-----------------|
| 7.1 | **No Spring Boot Actuator** | `pom.xml` dependencies — no `spring-boot-starter-actuator` | No production-ready endpoints for health, metrics, info, or thread dumps | The custom `/health` endpoint only checks a single `count()` query. Actuator would provide standardized health indicators (DB, disk, memory), Prometheus-compatible metrics, and structured application info. |
| 7.2 | **No structured request logging** | `application.properties` — no logging or actuator log config | No access logs, no request IDs, no correlation between requests and exceptions | `@Slf4j` is used on `BookService` for debug/info logs, but there is no web request logging (no `CommonsRequestLoggingFilter`, no MDC). Debugging a production issue requires correlating scattered log lines. |
| 7.3 | **No metrics collection** | `pom.xml` — no Micrometer or Prometheus dependencies | No way to observe throughput, error rates, latency distributions, or database connection pool usage | Without metrics, it's impossible to detect performance degradation, unusual traffic patterns, or resource exhaustion before users report problems. |

---

## 8. Code Quality & Maintainability

| # | Gap | Location | Impact | Why it matters |
|---|-----|----------|--------|-----------------|
| 8.1 | **Repository query methods use raw `Object[]` projections** | `BookRepository.getGenres()` returns `List<Object[]>` (line 100), `getCountAndTotalValue()` returns `Object[]` (line 116) | The service must cast indexes manually (`row[0]`, `row[1]`) with no type safety | AGENTS.md notes: "Aggregate repository methods currently return low-level shapes (`Object[]` and `List<Object[]>`)." A `Class`-based or Spring Data projection would provide compile-time safety and eliminate `ClassCastException` risk. |
| 8.2 | **No `@Slf4j` on controller layer** | `BookAPI` — no logging annotation | Controller-level events (request received, response returned, errors) are only logged at the service layer | The service logs `"Processing request to add book: {}"` but the controller has no logging. For debugging, knowing the HTTP method and path from the controller would complement the service-level business log. |
| 8.3 | **`ApiResponse.data` is mutable** | `ApiResponse` (line 6: `private T data;`) | Although `success`, `message`, and `timestamp` are final, `data` has a setter-less mutable declaration that could confuse (it's actually never mutated) | The class has no explicit setters, so `data` is effectively immutable, but the inconsistent mutability declaration (`final` on most fields, not on `data`) makes the class's immutability contract unclear. |
| 8.4 | **Empty V2 migration** | `../src/main/resources/db/migration/V2__create_users_table.sql` — 0 bytes | Flyway records `V2` as applied, but it does nothing | Documented in AGENTS.md: "V2 is currently empty and may already be recorded in persistent databases." This is harmless unless a real users table needs to be added later — at that point, V2 cannot be edited to add the table; a new V3+ migration is required. |
| 8.5 | **Hardcoded allowlist for sort fields** | `BookService.getBooksSortedBy` (line 305: `Set.of("title", "author", "id", "price", "genre")`) | Adding a new sortable field requires editing the service, not just the entity | This is actually correct (security: sort fields should be allowlisted), but it's not documented that adding a sortable entity field requires updating this set. A comment or constant would make the dependency clear. |

---

## 9. Concurrency & Data Integrity

| # | Gap | Location | Impact | Why it matters |
|---|-----|----------|--------|-----------------|
| 9.1 | **No idempotent-create pattern** | `POST /app/books/add` — no idempotency key support | Retrying a failed POST (e.g., network timeout) may create duplicate books | If a client sends a POST, doesn't receive the response, and retries, a duplicate book is created. An idempotency-key pattern (or client-generated UUID) would prevent this. |
| 9.2 | **DELETE uses bulk JPQL without cascade check** | `BookRepository.deleteBookById` (line 130–132) — `DELETE FROM Book b WHERE b.id = :id` | No foreign-key relationship checks before deletion; `@Modifying` bypasses JPA entity lifecycle | If other tables reference `books.id` in the future, this bulk delete would either fail with a foreign-key violation or (if `clearAutomatically` causes a flush race) leave orphaned references. The current schema has no foreign keys, so this is a latent risk. |

---

## Summary

### Resolved gaps

| Gap | Resolution | Completed | Result |
|---|---|---|---|
| 1.1 | Injected Jakarta `Validator` into `BookService`, enforced `Book` constraints before create/PATCH/PUT persistence, and mapped `ConstraintViolationException` to the standard validation response | September 5, 2026 | Entity annotations now execute for every service write, including callers that bypass controller `@Valid`; unit coverage verifies enforcement before repository save and HTTP 400 translation |
| 1.2 | Removed the inaccurate `buildValidationErrorResponse` helper contract from `AGENTS.md` | September 3, 2026 | Documentation now matches the current handler implementation; the API contract is unchanged |
| 1.3 | Added a domain-specific `BookValidationException` for PATCH/PUT service rules and a dedicated handler that uses the shared validation-response builder | September 6, 2026 | Book write-validation failures are distinct from unrelated argument errors while retaining the established HTTP 400 `Validation failed` response; unit tests verify the exception type and response contract |
| 5.3 | Removed the PostgreSQL init-script mount and made Flyway the sole schema authority | September 9, 2026 | Fresh and existing schemas now follow one tracked migration path |
| 5.5 | Explicitly configured `classpath:db/migration` and corrected V1 before persistent data existed | September 9, 2026 | Fresh databases run a deterministic Flyway-owned schema history without automatic baselining |
| 6.2 | Added `pg_isready` health checking and `condition: service_healthy` | September 9, 2026 | The app starts only after PostgreSQL is ready to accept connections |
| 8.6 | Added springdoc OpenAPI/Swagger UI support | September 9, 2026 | Interactive and machine-readable API documentation is available |

### Portfolio summary

| Layer | Active | Resolved | Total identified | Critical | High | Medium | Low |
|---|---:|---:|---:|---:|---:|---:|---:|
| Validation & Error Handling | 2 | 3 | 5 | 0 | 0 | 1 | 1 |
| Security | 4 | 0 | 4 | 0 | 1 | 2 | 1 |
| API Design & Consistency | 8 | 0 | 8 | 0 | 2 | 4 | 2 |
| Testing & Coverage | 5 | 0 | 5 | 0 | 1 | 3 | 1 |
| Database & Migrations | 3 | 2 | 5 | 0 | 1 | 1 | 1 |
| Infrastructure & Deployment | 4 | 1 | 5 | 0 | 1 | 1 | 2 |
| Observability & Monitoring | 3 | 0 | 3 | 0 | 1 | 2 | 0 |
| Code Quality & Maintainability | 5 | 1 | 6 | 0 | 0 | 3 | 2 |
| Concurrency & Data Integrity | 2 | 0 | 2 | 0 | 0 | 1 | 1 |
| **Total** | **36** | **7** | **43** | **0** | **7** | **18** | **11** |

Severity columns count active gaps only.

**No critical vulnerabilities** were found. The highest-priority gaps are:
1. **Testing coverage** — no integration, MVC, or JPA tests exist (Gap 4.1–4.2)
2. **Security** — all endpoints public, no rate limiting (Gaps 2.1–2.3)
3. **Response envelope inconsistency** — 8 different response shapes across endpoints (Gap 3.1)
4. **Database positive-price constraint** — missing `CHECK(price > 0)` (Gap 5.2)
