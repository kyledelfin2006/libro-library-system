# Current System Engineering Reference

This is the detailed implementation reference for agents and contributors. The root [AGENTS.md](../../AGENTS.md) is the short instruction and source-of-truth index. This document describes the current repository; code, migrations, and tests remain authoritative if any detail here becomes stale.

## Purpose

It documents the repository as it exists, the intended architecture, the contracts between layers, and the checks expected before a contribution is considered complete.

The application is a single-module Spring Boot REST API for managing a library's book collection, user accounts, and loan history. It exposes book CRUD/search/statistics, user/account operations, loan checkout/return/loss/history routes, and health endpoints. PostgreSQL backs persistence.

## Project Snapshot

| Concern | Current choice                                                          |
|---|-------------------------------------------------------------------------|
| Language | Java 25                                                                 |
| Framework | Spring Boot 4.1.2                                                       |
| Build | Maven 3.10.0 through the committed Maven Wrapper, producing an executable Spring Boot JAR |
| HTTP layer | Spring MVC                                                              |
| Persistence | Spring Data JPA and Hibernate                                           |
| Database | PostgreSQL 18                                                           |
| Schema management | Flyway SQL migrations                                                   |
| Validation | Jakarta Bean Validation                                                 |
| Security | Spring Security stateless JWT Bearer authentication; HS256; role-based access for STUDENT, FACULTY, ADMINISTRATOR, and LIBRARIAN |
| Boilerplate reduction | Lombok 1.18.46                                                          |
| Testing | JUnit 5, Mockito, Jakarta Validator                                     |
| Coverage | JaCoCo report during Maven `verify`                                     |
| Health | Spring Boot Actuator liveness/readiness probes; only the health endpoint is exposed |
| Containers | Dockerfile plus Docker Compose                                          |
| API documentation | Springdoc OpenAPI 3; Swagger UI and `/v3/api-docs`                   |

The Maven coordinates are `io.github.kyledelfin2006:library-api-system:1.0-SNAPSHOT`. The committed `mvnw`/`mvnw.cmd` scripts pin Maven 3.10.0 for local builds, CI, and Docker image builds.
The POM overrides Boot's managed Jackson 3, Jackson 2, Spring Framework, and Tomcat versions with patched releases; revisit these overrides when Boot manages the same or newer versions. The runtime image also upgrades Alpine's `libexpat` package during its build.

## Repository Map

```text
library-api-system/
|-- AGENTS.md, README.md, LICENSE
|-- pom.xml, mvnw, mvnw.cmd, .mvn/wrapper/   Maven Wrapper; always build with ./mvnw
|-- Dockerfile                               multi-stage, layered runtime image
|-- docker-compose.yml                       local stack: db + app (+ optional `proxy` profile)
|-- .env.example                             environment template (required vs optional values)
|-- .editorconfig, .gitattributes, .dockerignore, .trivyignore
|-- .github/
|   |-- dependabot.yml
|   `-- workflows/
|       |-- ci.yml                           unit-test, integration-test, security-scan
|       `-- cd.yml                           ARM64 image build/scan/publish, gated SSH deploy
|-- infra/
|   |-- docker-compose.prod.yml              production override for the OCI VM
|   `-- caddy/Caddyfile                      TLS reverse proxy
|-- scripts/
|   |-- common.sh, deploy.sh, smoke.sh       operations (see docs/devops/runbook.md)
|   |-- backup.sh, restore.sh, seed-demo.sh
|   `-- ci/trivy-scan.sh                     vulnerability scan shared by CI and CD
|-- docs/
|   |-- architecture.md                      short overview for readers new to the project
|   |-- adr/                                 architecture decision records
|   |-- devops/                              CI/CD design and operations runbook
|   |-- guides/                              domain, loan, API-doc, and setup guides
|   `-- reference/                           this file, roles/JWT, roadmap
`-- src/
    |-- main/
    |   |-- java/app/
    |   |   |-- LibraryApplication.java      root package: component scan starts here
    |   |   |-- auth/
    |   |   |   |-- config/                  SecurityConfig, PasswordConfig, JwtConfiguration, JwtProperties, CorsProperties
    |   |   |   |-- controller/              AuthController (POST /app/auth/login)
    |   |   |   |-- dto/                     LoginRequestDTO, TokenResponseDTO
    |   |   |   |-- ratelimit/               per-IP limiter for login/signup + its properties
    |   |   |   |-- service/                 AuthService, LibroUserDetailsService
    |   |   |   `-- web/                     JsonSecurityErrorHandler (filter-chain 401/403 bodies)
    |   |   |-- book/
    |   |   |   |-- controller/              BookController
    |   |   |   |-- dto/                     BookRequestDTO, BookPatchRequestDTO, BookResponseDTO, LibraryStatisticsDTO
    |   |   |   |-- entity/                  Book, BookCopyStatus
    |   |   |   |-- exception/               BookNotFoundException, BookValidationException
    |   |   |   |-- mapper/                  BookMapper
    |   |   |   |-- repository/              BookRepository, projection/ (GenreCount, LibraryAggregate)
    |   |   |   `-- service/                 BookService
    |   |   |-- loan/
    |   |   |   |-- controller/              LoanController
    |   |   |   |-- dto/                     LoanResponseDTO
    |   |   |   |-- entity/                  Loan, LoanStatus
    |   |   |   |-- exception/               LoanConflictException, LoanNotFoundException
    |   |   |   |-- mapper/                  LoanMapper
    |   |   |   |-- repository/              LoanRepository
    |   |   |   `-- service/                 LoanService
    |   |   |-- user/
    |   |   |   |-- config/                  LibrarianBootstrapProperties, LibrarianBootstrapRunner
    |   |   |   |-- controller/              UserController
    |   |   |   |-- dto/                     UserCreateRequestDTO, UserPatchRequestDTO, UserReplaceRequestDTO,
    |   |   |   |                            PasswordChangeRequestDTO, UserResponseDTO
    |   |   |   |-- entity/                  User, enums/ (UserRole, UserCourse, UserITMajor)
    |   |   |   |-- exception/               UserNotFoundException
    |   |   |   |-- mapper/                  UserMapper
    |   |   |   |-- repository/              UserRepository
    |   |   |   |-- service/                 UserService, UserAccessPolicy
    |   |   |   `-- validation/              PasswordPolicy
    |   |   `-- global/                      cross-cutting code shared by every feature
    |   |       |-- exception/               GlobalExceptionHandler, ResourceNotFoundException, InvalidRequestException
    |   |       |-- logging/                 RequestIdFilter (X-Request-Id)
    |   |       |-- openapi/                 OpenApiConfig, @ErrorApiResponse(s)
    |   |       |-- response/                ApiResponse, ErrorResponse
    |   |       `-- time/                    LibraryTime, LibraryClockConfig (Asia/Manila Clock bean)
    |   `-- resources/
    |       |-- application.properties       every setting and its default
    |       `-- db/migration/                Flyway V1..V5 (forward-only)
    `-- test/
        |-- java/app/                        mirrors the main packages
        |   |-- **/*Test.java                unit and MVC-slice tests (Surefire, `./mvnw verify`)
        |   |-- **/*IT.java                  PostgreSQL integration tests (Failsafe, `-Pintegration`)
        |   `-- support/PostgresTestConfig   Testcontainers PostgreSQL, same digest as docker-compose.yml
        `-- resources/
            |-- application-test.properties  `test` profile: only the overrides a test run needs
            |-- junit-platform.properties    parallel test classes
            `-- logback-test.xml
```

Keep production code below the root `app` package. `LibraryApplication` sits at that root so Spring's default component scan discovers controllers, services, repositories, mappers, advice, and configuration beneath it.

Each feature package (`auth`, `book`, `loan`, `user`) uses the same sub-packages, so a reader who knows one feature can navigate the others: `controller` (HTTP only) → `service` (rules, transactions, mapping) → `repository` (persistence), with `dto`, `entity`, `mapper`, and `exception` beside them. Code that every feature shares lives in `global`. Tests live in the same package as the class they test, which keeps package-private helpers testable and makes the `*Test` / `*IT` suffix the only thing that decides which plugin runs a test.

## Runtime Architecture

Book, user, and loan API operations follow their feature controller and service paths, then share the JPA persistence boundary:

```text
HTTP client
  -> Spring Security filter chain
  -> BookController, UserController, or LoanController controller
  -> BookService, UserService, or LoanService
  -> feature repositories (LoanService also uses BookRepository and UserRepository)
  -> Hibernate/JPA
  -> PostgreSQL
```

Login uses the existing account-lookup path through the same user repository:

```text
POST /app/auth/login (university ID + password)
  -> AuthController and AuthService
  -> AuthenticationManager / DaoAuthenticationProvider
  -> LibroUserDetailsService.loadUserByUsername(universityId)
  -> UserRepository.findByUniversityId(universityId)
  -> Hibernate/JPA
  -> PostgreSQL users table
  -> BCrypt PasswordEncoder verifies the stored hash
  -> JwtEncoder signs the configured HS256 claims
```

Protected requests use a stateless header-only path:

```text
Authorization: Bearer <token>
  -> Spring Security Resource Server JwtDecoder
  -> signature, issuer, audience, timestamp, subject, and role validation
  -> JwtAuthenticationConverter maps role to ROLE_...
  -> SecurityConfig route rule
  -> controller passes Authentication name/authorities to service
```

`UserService` uses `UserRepository` for signup and account operations; `LoanService` uses it to load and lock borrowers; `LibroUserDetailsService` loads the stored password hash and role for login. Signup, login, and Actuator health probes are public; other routes require authentication. The required Base64 key is `LIBRO_JWT_SECRET_BASE64`; issuer and audience default to `libro-api`; access-token lifetime is capped at 900 seconds with 30 seconds of clock skew. Controllers do not access repositories directly. Response paths generally convert entities to DTOs through the feature mapper. Exceptions escape their originating layer and are converted to JSON by `GlobalExceptionHandler` unless rejected in the security filter chain.

For the two public POST routes, the request order is `Spring Security → MVC route matching → PublicRouteRateLimitInterceptor → request-body binding and validation → controller → service`. The interceptor applies independent fixed-window quotas keyed by route and `HttpServletRequest.getRemoteAddr()`. With `SERVER_FORWARD_HEADERS_STRATEGY=native`, Tomcat resolves the address from forwarding headers sent by the trusted proxy; the interceptor does not read spoofable headers itself. This mode defaults off and requires the app port to be reachable only through that proxy. If the quota is exhausted, the interceptor writes the shared JSON error shape with HTTP 429 and `Retry-After`, then stops the MVC request. The in-memory state is bounded and local to one process; it resets on restart, is not shared between replicas, and may evict an older active counter at capacity. Fixed windows can allow a burst around a window boundary, so gateway throttling remains required before public or multi-instance deployment.

### Application entry point

`app.LibraryApplication` uses `@SpringBootApplication`, which combines configuration, auto-configuration, and component scanning. Do not move it below a feature package unless component scanning is configured explicitly.

### Controller layer

`BookController` owns HTTP concerns only:

- Base route: `/app/books`.
- Parses path variables, query parameters, pagination, and JSON bodies.
- Applies `@Valid` to complete create and replace payloads.
- Uses `BookPatchRequestDTO` for PATCH and deliberately does not apply `@Valid`; its optional fields describe the actual partial-update contract.
- Delegates business rules to `BookService`.
- Receives response DTOs from `BookService`; it never sees a `Book` entity.
- Chooses HTTP status codes and response envelopes.
- Documents every operation sufficiently for Swagger UI to act as the interactive API reference, while Springdoc derives paths and schemas from controller signatures and DTOs wherever possible. Follow [API Documentation Guideline](../guides/api-documentation-guideline.md): document non-inferable behavior, keep constraints on DTOs, and avoid repeated annotation boilerplate.
- Uses `@ErrorApiResponse` for expected errors with the shared `ErrorResponse` schema. Keep response status and endpoint-specific meaning beside the operation; the composed annotation centralizes only repeated schema metadata.

Do not add repository access to the controller. New endpoint logic should remain thin and be independently testable in the service.

`UserController` owns account routes and delegates ownership and target-role checks to `UserService`, which asks `UserAccessPolicy`. `LoanController` binds checkout, return, lost-resolution, and history routes; it passes the authenticated university ID to `LoanService` and never accepts a borrower ID for checkout.

### Service layer

`BookService`, `UserService`, and `LoanService` are the business-logic boundaries and transaction owners for their features. They coordinate repositories and mapping, validate rules not adequately represented by request annotations, and translate missing data into feature exceptions.

Every public service method accepts request DTOs and returns response DTOs. Mapping happens inside the service transaction, so an entity never reaches a controller; with `spring.jpa.open-in-view=false` a lazy relation read after the method returns would fail. `BookService` and `UserService` are `@Transactional(readOnly = true)` at class level, and write methods override that with `@Transactional`. A read-only transaction lets Hibernate skip dirty checking and tells the reader at a glance which methods change data.

Write methods are transactional:

- `addBook` maps and explicitly saves a new entity.
- `patchBook` loads a managed entity and changes only supplied fields.
- `replaceBook` validates a complete replacement, then maps all fields onto a managed entity.
- `deleteBookById` executes a modifying JPQL query and checks the affected row count.

PATCH and PUT load the copy through `BookRepository.findByIdForUpdate`. That pessimistic row lock is also used by loan checkout and lost-loan resolution. It makes catalog edits wait for an in-flight loss resolution and then load the committed copy status, preventing a stale edit from restoring `IN_COLLECTION` after staff recorded `LOST`.

Before create, PATCH, and PUT reach the persistence boundary, the service explicitly validates
the resulting `Book` with Jakarta `Validator`. This makes entity constraints effective for
internal service callers as well as HTTP callers. Entity constraint violations are translated to
the standard HTTP 400 validation response by `GlobalExceptionHandler`.

Business-rule failures throw `InvalidRequestException` (or its subclass `BookValidationException` for book writes),
which the global handler maps to the standard HTTP 400 validation response. Only this type becomes a 400: a bare
`IllegalArgumentException` from a library is treated as a server fault (HTTP 500 with a generic message), so internal
messages are never echoed to clients.

PATCH and PUT intentionally omit `repository.save(existingBook)`. Hibernate dirty checking flushes changes to managed entities when the transaction commits. Preserve this behavior unless the persistence model changes. Calling these methods outside Spring's managed proxy, or removing `@Transactional`, changes that guarantee.

#### PUT vs PATCH: partial-update design

The book controller distinguishes full replacement (PUT) from partial updates (PATCH) through validation strategy:

- **PUT (`replaceBook`)**: The controller annotates the request body with `@Valid @RequestBody BookRequestDTO`. This activates all DTO annotations (`@NotBlank`, `@Size`, `@Positive`, `@NotNull`) on every invocation, enforcing a complete, valid payload. The service method (`replaceBook`) additionally performs manual checks as defense-in-depth, protecting callers that bypass the controller's `@Valid` (e.g., scheduled jobs, internal consumers). The mapper's `updateBookFromDto` overwrites every mutable field onto the fetched managed entity.

- **PATCH (`patchBook`)**: The controller accepts `BookPatchRequestDTO` without `@Valid`. Its optional schema fields match the partial-update contract: omitted or `null` values and blank text are ignored; a supplied replacement cost must be positive. The service applies field-level conditional logic and validates the resulting managed entity.

This design solves a problem solved by many implementations incorrectly:

- **Applying PUT rules to PATCH**: If `@Valid` were on PATCH, sending `{"replacementCost": 15.00}` would fail because `title`, `author`, and `genre` are `null` and violate `@NotBlank`. This breaks partial updates entirely.
- **Applying PATCH rules to PUT**: If PUT used the same `hasText()` skip logic, a client could PUT `{"title": null, "author": "Orwell", ...}` and the `null` title would be silently ignored, leaving the old title in place — violating the "complete replacement" contract of PUT.

The `hasText()` method (`s != null && !s.trim().isEmpty()`) also correctly handles the edge case where a client sends `"title": ""` or `"title": "   "` — these are treated as "no update" rather than as an attempt to blank the field. This preserves existing data rather than corrupting it with empty strings.

Read methods handle pagination, field-restricted sorting, searches, replacement cost ranges, genre distribution, and aggregate statistics. Keep input allowlists in the service; never pass arbitrary client field names directly to JPA sorting or query construction.

`GET /app/books/query` combines optional title, author, genre, and inclusive replacement-cost bounds in one database-paged query. The service validates replacement-cost bounds and sort properties before repository access, and adds `id` as a tie-breaker for stable pages. Missing text filters match all books; supplied text is matched as a case-insensitive literal substring. The list route paths remain stable, while book responses expose the renamed `replacementCost` and new optional `description` fields.

### Repository layer

`BookRepository` extends `JpaRepository<Book, Long>`, gaining standard CRUD, pagination, sorting, and count operations. Its custom methods use three Spring Data styles:

- Derived queries, such as `findByTitleContainingIgnoreCase` and `findTopByOrderByReplacementCostDesc`.
- JPQL aggregate/select queries for ranges, totals, averages, and genre distribution.
- A modifying JPQL delete returning the affected row count.

`deleteBookById` uses `@Modifying(clearAutomatically = true)` because bulk JPQL bypasses normal entity lifecycle synchronization. If more bulk updates are introduced, account for stale persistence-context state in the same way.

The count-and-total-replacement-cost aggregate uses the typed `LibraryAggregate` constructor projection under `app.book.repository.projection`; the service maps that internal projection into the public `LibraryStatisticsDTO`. Genre distribution follows the same pattern through the typed `GenreCount` constructor projection, which the service maps into the existing `Map<String, Long>` response. These records are internal persistence projections, not public DTOs. Prefer typed projections for future complex aggregations when they improve safety without adding unnecessary abstraction.

### User-domain status

The user feature currently contains the entity, enums, create/response DTOs, mapper, repository, BCrypt `PasswordEncoder` bean, and `UserService`. `UserService` loads accounts, asks `UserAccessPolicy` whether the caller may act on the target, normalizes university IDs and email addresses, rejects duplicates, validates password complexity, hashes passwords before persistence, and enforces the role/course/major rules. Passwords must be 8–72 characters, include uppercase and lowercase letters, a number, and a symbol, and stay within BCrypt's 72 UTF-8 byte input limit. Signup, password changes, and login check the byte boundary before BCrypt processing. The enum and V2 database checks define the allowed academic values; the service enforces their cross-field relationships.

`UserController` exposes public student signup, librarian-only faculty and library-assistant creation, a role-filtered staff directory, owner/staff-scoped profile and password operations, and role-scoped account deletion. `ADMINISTRATOR` means a library assistant (not a system administrator); `LIBRARIAN` means the library's librarian. Signup ignores the submitted role and creates a student; staff-account creation fixes the role in the service. `SecurityConfig` allows book reads to all roles and book CRUD to ADMINISTRATOR and LIBRARIAN. `UserService` checks ownership and target roles for account routes. V2 defines the original users table and historically seeds a bootstrap administrator from a supplied BCrypt hash; V3 adds LIBRARIAN to the accepted role and academic-field constraints. No librarian is seeded by migration; an optional startup runner creates the first one from a complete environment-provided identity and BCrypt hash. `LibroUserDetailsService` loads the stored hash and role for login and maps the role into Spring Security authorities.

### Entity and database model

The current domain decision is that one `Book` entity represents one physical borrowable copy, not a title-level inventory record. Separate physical copies of the same title are separate rows with different generated IDs. One `Book` row can have at most one active loan, enforced by a PostgreSQL partial unique index. Do not add `availableCopies` or `totalCopies` to `Book` without revisiting this decision.

`Loan` represents one checkout event and remains in history after return or loss. Its explicit state is `ACTIVE`, `RETURNED`, or `LOST`; nullable `returnedAt` alone does not define activity because a lost loan also has no return time. Overdue is derived from active status and the Manila-local due time, and is not stored. V4 adds loan state constraints and prevents deletion of referenced users or copies.

`Book` maps to `books`:

| Java field | Java type | Database definition | Constraints |
|---|---|---|---|
| `id` | `Long` | `BIGSERIAL PRIMARY KEY` | Generated with `IDENTITY` |
| `title` | `String` | `VARCHAR(100)` | Nonblank, not null |
| `author` | `String` | `VARCHAR(50)` | Nonblank, not null |
| `genre` | `String` | `VARCHAR(50)` | Nonblank, not null |
| `description` | `String` | `VARCHAR(2000)` | Optional catalog summary |
| `replacementCost` | `BigDecimal` | `books.replacement_cost DECIMAL(10,2)` | Not null, positive at application boundary |
| `copyStatus` | `BookCopyStatus` | `VARCHAR(24)` | `IN_COLLECTION` or `LOST`; defaults to `IN_COLLECTION` |
| `createdAt` | `LocalDateTime` | `TIMESTAMP` | Not null, set on insert, immutable after creation, omitted from response DTOs |

`Loan` maps to `loans` and has required borrower/book links plus `borrowedAt`, `dueAt`, and `status`. `returnedAt` is set only for returned loans; `lostAt` and `lostBy` are set only for lost loans. All timestamps are Manila-local `LocalDateTime` values. The response DTO adds a calculated `overdue` field; it is never persisted.

Use `BigDecimal` for all money-related values and comparisons. Do not replace it with `double` or `float`. Use `compareTo` for numerical equality in tests because `BigDecimal.equals` also compares scale.

V1 created indexes on title, author, genre, and the original monetary column. V5 renames that column to `replacement_cost` and renames its index. PostgreSQL may not fully exploit ordinary B-tree indexes for every case-insensitive contains query (`%term%`), so benchmark before claiming those indexes optimize substring search.

### DTO and mapper boundary

`BookRequestDTO` is the complete inbound contract for create and PUT; title, author, genre, and positive `replacementCost` are required. `description` is optional and limited to 2,000 characters. `BookPatchRequestDTO` is the partial-update contract; fields are optional, null and blank text values are ignored, and service logic validates supplied values. Unknown JSON fields are ignored by Jackson.

`BookResponseDTO` is the regular outbound book shape and intentionally omits the internal `createdAt` timestamp. Its `replacementCost` field records the value used to replace one physical copy; it does not assess or collect a fee. `LibraryStatisticsDTO` is an immutable Java record containing the total count, sum of replacement costs, and the copy with the highest replacement cost.

`BookMapper` centralizes:

- Request DTO to new entity conversion.
- Existing entity replacement for PUT.
- Entity to response DTO conversion.
- Entity-list to response-list conversion.

Only `BookService` calls `BookMapper`. Do not expose `Book` from a service or controller: entity exposure couples clients to persistence structure and makes future database changes risky. Keep partial-update semantics in the service; `updateBookFromDto` is full replacement behavior.

### Error handling and response contracts

`GlobalExceptionHandler` is a `@RestControllerAdvice` shared by all controllers. It maps validation, malformed input, missing resources and routes, unsupported methods, invalid sort properties, database failures, and unexpected exceptions to `ErrorResponse`.

Two base types in `app.global.exception` keep the handler small as features grow:

| Base type | Subclasses | HTTP result |
| --- | --- | --- |
| `ResourceNotFoundException` | `BookNotFoundException`, `UserNotFoundException`, `LoanNotFoundException` | 404 with error `"<Resource> not found"` and the service's client-safe detail |
| `InvalidRequestException` (extends `IllegalArgumentException`) | `BookValidationException` | 400 `"Validation failed"` with the rule's message |

A new resource or rule needs only a subclass, not a new handler method.

`ErrorResponse` contains:

```json
{
  "error": "Validation failed",
  "details": "Replacement cost must be greater than 0",
  "timestamp": 0,
  "statusCode": 400,
  "fieldErrors": {
    "replacementCost": "Replacement cost must be greater than 0"
  }
}
```

Validation responses include `fieldErrors`, a map from DTO/entity field names to messages.
Clients should use this map for field-level rendering instead of parsing the combined
`details` string. Constraint violations without a property path use `_global`. Other
error responses return an empty `fieldErrors` map for a consistent JSON shape.

**Every** successful response uses the generic `ApiResponse<T>` envelope with `success`, `message`, `data`, and `timestamp`; use `ApiResponse.ok(data)` for reads. Pages are returned inside `data` as `ApiResponse<Page<T>>`. Errors always use `ErrorResponse`. Do not return a bare DTO, list, map or `Page` from a controller; changing the envelope shape is an API compatibility change.

Handler order matters conceptually. `DataIntegrityViolationException` is a subtype of `DataAccessException`; preserve the specific conflict behavior when refactoring exception handling. Never return stack traces, SQL, credentials, or database internals to clients.

### Security

`SecurityConfig` permits `POST /app/users/signup`, `POST /app/auth/login`, `GET /livez` and `GET /readyz` without authentication. Health details are hidden and every other Actuator endpoint lives on the private management port; other routes require a valid Bearer token. Book reads require any live role; book CRUD requires ADMINISTRATOR or LIBRARIAN. `POST /app/users/faculty` and `POST /app/users/administrators` require LIBRARIAN. The account directory requires ADMINISTRATOR or LIBRARIAN, while profile/password routes permit all authenticated roles through the filter chain and rely on `UserService` to enforce owner access or staff access by target role. Students and faculty may delete themselves; administrators may delete students/faculty; librarians may also delete administrators. The filter chain is stateless and disables CSRF because credentials are accepted only from the explicit Authorization header; CORS allows exact environment-configured origins and no credentials. Use HTTPS outside local development.

Local login uses `AuthenticationManager`, `LibroUserDetailsService`, and BCrypt, then issues an HS256 token with a maximum 900-second lifetime. `LIBRO_JWT_SECRET_BASE64` must decode to at least 32 bytes and has no development fallback. Public signup always creates STUDENT. Only the librarian creates school faculty and library assistant accounts. Flyway V2 seeds the administrator login `0000-0001` from `LIBRO_ADMIN_PASSWORD_HASH`, which must contain a BCrypt hash. To establish the first librarian, operators can set all five `LIBRO_BOOTSTRAP_LIBRARIAN_*` values at startup; the runner validates all-or-nothing configuration and a BCrypt-only hash, creates the account only when none exists, and logs no account data. Do not add an open librarian signup route or place a raw password or real JWT key in migrations or source. Before untrusted deployment, add gateway throttling, restrict direct app access, and monitor login/signup abuse. `SERVER_FORWARD_HEADERS_STRATEGY=native` is available only when ingress restricts the app port to the trusted proxy. See [JWT Authentication Design](jwt-authentication-design.md) for exact claim and validation rules.

`UserAccessPolicy` (called by `UserService` after it loads the target) checks the authenticated university ID against the target account for profile reads, updates, password operations, and deletion. `UserAccessPolicyTest` pins the full role matrix. Owners may manage their own profile/password. Administrators may view/manage student and faculty accounts and delete those accounts. Librarians may also view/manage/delete administrator accounts. Neither staff role may manage another librarian account; administrator/librarian self-deletion is denied. The directory returns student/faculty accounts to administrators, and also library assistants to librarians.

For account routes, Spring Security validates the Bearer token and supplies the controller's `Authentication` parameter. The controller passes `authentication.getName()` as `callerId` (the authenticated university ID), the path's `universityId` as `targetId` (the account requested), and `authentication.getAuthorities()` as the caller's granted roles. `UserService` loads the target account before checking its role. `canStaffManage` centralizes the target-role boundary shared by profile access and deletion; deletion checks ownership separately because students/faculty may delete themselves but staff accounts may not. The staff directory passes only permitted roles to `findByUserRoleIn`, so paging and sorting operate on the visible subset. Faculty and assistant creation receive the caller's authorities and select their stored role in the service; the request body's role cannot grant access.

The current local JWT flow is implemented; deployment still needs a real TLS domain (the `proxy` profile is the reference), alerts on the login and rate-limit metrics, gateway throttling beyond one instance, and a deliberate choice about institutional SSO if it is required. The in-app rate counters are process-local and do not replace gateway limits. Swagger UI and `/v3/api-docs` are disabled by default; `LIBRO_API_DOCS_ENABLED=true` serves them publicly, and when the flag is `false` springdoc is disabled and no permit rule exists. Filter-chain 401/403 responses use the standard JSON error body through `JsonSecurityErrorHandler`. Loan operations must identify the borrower from the authenticated principal rather than a client-supplied user ID.

## Endpoint Inventory

Authentication routes use `/app/auth`; book routes use `/app/books`; user routes use `/app/users`; loan routes use `/app/loans`. Signup, login, and Actuator health probes are public.

Authentication routes:

| Method | Path | Purpose | Response shape |
|---|---|---|---|
| POST | `/app/auth/login` | Verify a university ID/password and issue a short-lived JWT | `ApiResponse<TokenResponseDTO>` |

| Method | Path | Purpose | Response shape |
|---|---|---|---|
| GET | `/stats` | Count, total replacement cost, highest replacement-cost copy | `ApiResponse<LibraryStatisticsDTO>` |
| GET | `/stats/average-replacement-cost` | Average collection replacement cost | `ApiResponse<BigDecimal>` |
| GET | `/stats/count` | Number of books | `ApiResponse<Long>` |
| GET | `/search?type=...&value=...` | Search by author, title, genre, or exact replacement cost | `ApiResponse<Page<BookResponseDTO>>` |
| GET | `/budget?maxReplacementCost=...` | Books at or below a maximum replacement cost | `ApiResponse<Page<BookResponseDTO>>` |
| GET | `/all?page=...&size=...&sort=...` | Paginated collection | `ApiResponse<Page<BookResponseDTO>>` |
| GET | `/query?title=...&author=...&genre=...&minReplacementCost=...&maxReplacementCost=...` | Paginated book query with optional combined filters and sorting | `ApiResponse<Page<BookResponseDTO>>` |
| GET | `/sorted?category=...` | Ascending sort by an allowed field (`category` decides the order) | `ApiResponse<Page<BookResponseDTO>>` |
| GET | `/genre` | Count grouped by genre | `ApiResponse<Map<String, Long>>` |
| GET | `/replacement-cost?minReplacementCost=...&maxReplacementCost=...` | Inclusive replacement cost range | `ApiResponse<Page<BookResponseDTO>>` |
| GET | `/{id}` | Retrieve one book | `ApiResponse<BookResponseDTO>` |
| POST | `/add` | Create a validated book | `ApiResponse<BookResponseDTO>`, HTTP 201 |
| PATCH | `/{id}` | Partially update supplied fields | `ApiResponse<BookResponseDTO>` |
| PUT | `/{id}` | Replace all mutable fields | `ApiResponse<BookResponseDTO>` |
| DELETE | `/{id}` | Delete one book | `ApiResponse<Void>` |

Operational probes are outside the book API and do not appear under the OpenAPI controller inventory:

| Method | Path | Purpose | Access |
|---|---|---|---|
| GET | `/livez` | Confirms the application process is alive | Public; details hidden |
| GET | `/readyz` | Confirms the application and database are ready | Public; details hidden |

User routes:

| Method | Path | Purpose | Response shape |
|---|---|---|---|
| GET | `/app/users` | Paginated student/faculty directory for administrators; librarian also sees library assistants (default size 12) | `ApiResponse<Page<UserResponseDTO>>` |
| GET | `/app/users/me` | Current profile for the authenticated account; deleted accounts return 404 | `ApiResponse<UserResponseDTO>` |
| GET | `/app/users/{universityId}` | Retrieve own profile; staff may read accounts allowed by role | `ApiResponse<UserResponseDTO>` |
| POST | `/app/users/signup` | Create a user account (public) | `ApiResponse<UserResponseDTO>`, HTTP 201 |
| POST | `/app/users/faculty` | Create a faculty account (librarian only) | `ApiResponse<UserResponseDTO>`, HTTP 201 |
| POST | `/app/users/administrators` | Create a library assistant account (librarian only) | `ApiResponse<UserResponseDTO>`, HTTP 201 |
| PATCH | `/app/users/{universityId}` | Update own or student/faculty profile (staff may manage student/faculty) | `ApiResponse<UserResponseDTO>` |
| PUT | `/app/users/{universityId}` | Replace own or student/faculty profile (staff may manage student/faculty) | `ApiResponse<UserResponseDTO>` |
| PUT | `/app/users/{universityId}/password` | Change own password or staff-reset a student/faculty password | `ApiResponse<Void>` |
| DELETE | `/app/users/{universityId}` | Owner deletes own student/faculty account; staff delete permitted target roles | `ApiResponse<Void>` |

Loan routes:

| Method | Path | Purpose | Response shape |
|---|---|---|---|
| POST | `/app/loans/{bookId}` | Student/faculty borrows one physical copy; 14-calendar-day term | `ApiResponse<LoanResponseDTO>`, HTTP 201 |
| PUT | `/app/loans/{loanId}/return` | Borrower returns own active loan | `ApiResponse<LoanResponseDTO>` |
| PUT | `/app/loans/{loanId}/lost` | Authorized library staff resolves an active loan as lost | `ApiResponse<LoanResponseDTO>` |
| GET | `/app/loans/me` | Caller reads own paged history | `ApiResponse<Page<LoanResponseDTO>>` |
| GET | `/app/loans` | Staff lists loans across permitted borrower roles; optional status, overdue, and borrower filters | `ApiResponse<Page<LoanResponseDTO>>` |
| GET | `/app/loans/users/{universityId}` | Authorized staff reads a permitted user's history | `ApiResponse<Page<LoanResponseDTO>>` |

Only students and faculty can borrow or return. All roles can read their own history. Library assistants can view/resolve student and faculty loans; librarians can also view/resolve library-assistant loans. Check `SecurityConfig` and `LoanService` for the enforced policy.

When adding an endpoint, update this file and `README.md`, document the operation and public DTO schemas following [API Documentation Guideline](../guides/api-documentation-guideline.md), provide request/response examples where useful, and add tests at the appropriate layer.

## Dependency Guide

Dependencies are declared in `pom.xml`. The Spring Boot parent manages versions for most Spring ecosystem dependencies.

### `spring-boot-starter-web`

Provides Spring MVC, embedded HTTP server support, JSON serialization through Jackson, controllers, routing annotations, `ResponseEntity`, and web exception types. It implements the entire HTTP adapter around the book use cases.

### `spring-boot-starter-validation`

Provides Jakarta Validation and its implementation. `@Valid` in `BookController` activates constraints on complete `BookRequestDTO` bodies; the resulting `MethodArgumentNotValidException` is transformed by global advice. PATCH uses a separate optional DTO and service validation.

### `spring-boot-starter-data-jpa`

Provides Spring Data repositories, JPA, Hibernate, transaction integration, derived-query parsing, JPQL execution, pagination, and sorting. It is responsible for repository implementation generation and dirty checking.

### `spring-boot-starter-security`

Provides the servlet security filter chain and DAO password authentication primitives. The project defines an explicit stateless `SecurityFilterChain`; signup, login, and Actuator health probes are public, and protected routes use JWT Bearer validation rather than generated-password, form-login, or HTTP Basic authentication.

### `spring-boot-starter-oauth2-resource-server`

Provides Spring Security's JWT resource-server support and Nimbus-backed JOSE integration. The project uses `JwtDecoder` to validate incoming Bearer tokens and `JwtEncoder` to sign tokens issued by the local login service; it does not act as an OAuth authorization server or use an external identity provider.

### PostgreSQL JDBC driver

Runtime driver used by the datasource to communicate with PostgreSQL. It is not required at compile time, hence runtime scope.

### Spring Boot Flyway starter and PostgreSQL module

`spring-boot-starter-flyway` supplies the Boot 4 migration auto-configuration. Flyway 13.3.0 discovers versioned scripts in `classpath:db/migration`, records applied versions in `flyway_schema_history`, and migrates the database before JPA validation. The PostgreSQL module supplies database-specific Flyway support.

Never edit a migration that may have been applied to a shared or persistent database. Add the next versioned migration (V6 or later for schema changes after the current V5). Migration filenames use Flyway's required double underscore between version and description; verify discovery during startup.

### Lombok

Compile-time annotation processing generates DTO/entity accessors, constructors, `toString`, service constructor injection, and SLF4J logger fields through the `@Slf4j` annotation. The compiler plugin explicitly includes the Lombok processor. Contributors need IDE annotation-processing support for accurate editor diagnostics, though Maven remains authoritative.

### `spring-boot-starter-test`

Provides the JUnit 5 test platform, Mockito, Spring testing utilities, AssertJ, and related test infrastructure. `BookServiceTest` instantiates the service with a mocked repository; the MVC tests start a Spring web slice.

### `spring-security-test`

Test-scoped Spring Security MockMvc support used to exercise the real filter chain and supply authenticated test principals in MVC tests. It does not change production security behavior.

### PostgreSQL integration-test dependencies

The opt-in `integration` Maven profile uses Testcontainers PostgreSQL and Spring Boot's `@ServiceConnection` support to test the real PostgreSQL 18 schema. Keep these dependencies test-scoped; ordinary unit tests do not need Docker. Testcontainers module versions are aligned through its imported BOM.

### `micrometer-registry-prometheus`

Adds the Prometheus scrape format at `/actuator/prometheus` on the private management port. It is a runtime-only dependency; the public application port never serves it.

### Maven build plugins

- `spring-boot-maven-plugin` repackages the artifact as an executable fat JAR.
- `maven-compiler-plugin` compiles Java 25 and runs Lombok's annotation processor.
- `maven-surefire-plugin` runs `*Test` classes in `test`; `-DskipUTs` turns off only this plugin (bound to its `skip` parameter, so the standard `-DskipTests` still skips both test plugins).
- `maven-failsafe-plugin` (in the `integration` profile) runs `*IT` classes in `integration-test` and fails the build in `verify`, after Testcontainers has shut down.
- `jacoco-maven-plugin` attaches its agent during tests, writes an HTML/XML coverage report during `verify`, and fails `verify` below 80% line coverage (`-Djacoco.haltOnFailure=false` keeps the report but skips the gate).

## Configuration and Environment

The application requires the database variables, the bootstrap hash, and the JWT secret; issuer, audience, and token lifetime can be overridden:

| Variable | Required? | Purpose | How docker-compose.yml passes it |
|---|---|---|---|
| `SPRING_DATASOURCE_URL` | Yes | JDBC database URL | `jdbc:postgresql://db:5432/${POSTGRES_DB}` |
| `SPRING_DATASOURCE_USERNAME` | Yes | Database role | `${POSTGRES_USER}` |
| `SPRING_DATASOURCE_PASSWORD` | Yes | Database password | `${POSTGRES_PASSWORD}` |
| `LIBRO_ADMIN_PASSWORD_HASH` | First start only | BCrypt hash used by the historical V2 bootstrap account migration. Required only for the first startup of an empty database; blank is accepted once V2 is applied, and a blank value on a fresh database makes the migration fail fast | Passed through only when set |
| `LIBRO_JWT_SECRET_BASE64` | Yes | HS256 signing key; valid Base64 that decodes to at least 32 bytes | `${LIBRO_JWT_SECRET_BASE64:?...}`: Compose refuses to start without it |
| `LIBRO_JWT_ISSUER` | No | Non-blank JWT issuer; defaults to `libro-api` | Passed through only when set; Spring default `libro-api` |
| `LIBRO_JWT_AUDIENCE` | No | Non-blank single expected audience; defaults to `libro-api` | Passed through only when set; Spring default `libro-api` |
| `LIBRO_JWT_ACCESS_TOKEN_TTL_SECONDS` | No | Positive lifetime, capped at 900 seconds; defaults to `900` | Passed through only when set; Spring default `900` |
| `LIBRO_LOGIN_RATE_LIMIT_REQUESTS` | No | Per-IP login requests per window; defaults to `20` | Passed through only when set; Spring default `20` |
| `LIBRO_LOGIN_RATE_LIMIT_WINDOW_SECONDS` | No | Login fixed-window duration, 1–86400 seconds; defaults to `60` | Passed through only when set; Spring default `60` |
| `LIBRO_SIGNUP_RATE_LIMIT_REQUESTS` | No | Per-IP signup requests per window; defaults to `10` | Passed through only when set; Spring default `10` |
| `LIBRO_SIGNUP_RATE_LIMIT_WINDOW_SECONDS` | No | Signup fixed-window duration, 1–86400 seconds; defaults to `3600` | Passed through only when set; Spring default `3600` |
| `LIBRO_RATE_LIMIT_MAX_TRACKED_CLIENT_ROUTES` | No | Maximum in-memory route/address counters; at least `2`, defaults to `10000` | Passed through only when set; Spring default `10000` |
| `SERVER_FORWARD_HEADERS_STRATEGY` | No | `none` (default) or `native`; use `native` only behind a trusted proxy with direct app access blocked | Passed through only when set; Spring default `none` |
| `MANAGEMENT_SERVER_PORT` | No | Private Actuator/Prometheus port; defaults to `9091`. Do not publish it | Not passed; the Spring default `9091` always applies |
| `LIBRO_API_DOCS_ENABLED` | No | Serves `/v3/api-docs` and Swagger UI without authentication; defaults to `false` (the `.env.example` template also leaves it `false`; set `true` locally or on the synthetic-data demo) | Passed through only when set; Spring default `false` |
| `LIBRO_DB_POOL_SIZE` | No | Hikari maximum pool size per instance; defaults to `10` | Passed through only when set |
| `LOGGING_LEVEL_APP` | No | Log level for the `app` package; defaults to `INFO` | Passed through only when set |
| `LIBRO_CORS_ALLOWED_ORIGINS` | No | Comma-separated exact UI origins; blank allows none and wildcards are rejected | Passed through only when set |
| `LIBRO_BOOTSTRAP_LIBRARIAN_UNIVERSITY_ID` | No | Optional initial librarian ID in `0000-0000` format; all five bootstrap values required together | Passed through only when set |
| `LIBRO_BOOTSTRAP_LIBRARIAN_EMAIL` | No | Optional initial librarian email | Passed through only when set |
| `LIBRO_BOOTSTRAP_LIBRARIAN_FIRST_NAME` | No | Optional initial librarian first name | Passed through only when set |
| `LIBRO_BOOTSTRAP_LIBRARIAN_LAST_NAME` | No | Optional initial librarian last name | Passed through only when set |
| `LIBRO_BOOTSTRAP_LIBRARIAN_PASSWORD_HASH` | No | BCrypt hash only for the initial librarian; never plaintext | Passed through only when set |

Docker Compose additionally reads `POSTGRES_DB`, `POSTGRES_USER`, and `POSTGRES_PASSWORD` from a root `.env` file. Production Flyway startup requires `LIBRO_ADMIN_PASSWORD_HASH` to be a valid BCrypt hash because V2 seeds the historical `ADMINISTRATOR` account; the test-only hash needed for disposable PostgreSQL integration tests is in `src/test/resources/application-test.properties`. Production also requires `LIBRO_JWT_SECRET_BASE64`; tests supply a deterministic test-only signing key from the same `test` profile file.

Defaults live in one place, `src/main/resources/application.properties`. `docker-compose.yml` passes optional variables with a bare name, so an unset variable is left out of the container and the Spring default applies. A variable that is present but empty (`NAME=`) is passed as an empty string; that is why `.env.example` keeps optional values commented out. Typed `@ConfigurationProperties` (`JwtProperties`, `RateLimitProperties`) are validated, so an empty issuer or limit stops startup with a binding error rather than running misconfigured.

The `test` profile (`application-test.properties`, activated with `@ActiveProfiles("test")` on each Spring test) is layered on top of the main file. Tests therefore run with the production defaults and override only the JWT key, the Flyway V2 placeholder, and a random management port. The first librarian bootstrap is optional and startup-only; omit all five values when it is not being used. `.env.example` is a template; never commit real credentials. Do not log secrets or place them in source, migrations, README examples, or exception messages.

Important persistence settings:

- `spring.jpa.hibernate.ddl-auto=validate`: Hibernate checks entity/schema compatibility but does not create or alter tables.
- `spring.flyway.enabled=true`: Flyway owns migration execution.
- `spring.flyway.locations=classpath:db/migration`: migration discovery is explicit.
- `spring.sql.init.mode=never`: legacy `schema.sql`/`data.sql` initialization is disabled.
- `spring.data.web.pageable.max-page-size=100`: limits API-requested pages.
- MVC missing-handler settings route unknown endpoints into JSON exception handling.

### Runtime behavior

These defaults are set in `application.properties` and are intentional:

| Concern | Behavior |
| --- | --- |
| Database sessions | `spring.jpa.open-in-view=false`: connections are released when the service method returns, so lazy relations must be read inside service transactions (mappers already run there). |
| Shutdown | `server.shutdown=graceful` with a 20-second timeout lets in-flight requests finish during rolling restarts. |
| Metrics | Actuator listens on the private management port (`MANAGEMENT_SERVER_PORT`, default `9091`, never published by compose) and exposes `health` and `prometheus`. `libro.rate_limit.rejections` (tagged by route) counts throttled login/signup requests and `libro.auth.logins` (tagged `result=success|failure`) counts login outcomes, alongside the standard HTTP, JVM and Hikari metrics. No metric carries account identifiers. |
| Health probes | `/livez` and `/readyz` are also served on the public port for load balancers and Docker. Readiness includes `readinessState` and the database; liveness does not, so a database outage does not restart healthy containers. Details are hidden. |
| Errors | Boot's fallback error pages never include messages, stack traces or binding errors. Spring MVC client errors (missing parameter, unsupported media type, unknown path) keep their native 4xx status through `GlobalExceptionHandler`. |
| Request IDs | `RequestIdFilter` runs first, accepts a safe inbound `X-Request-Id` (letters, digits, hyphens, at most 64 characters) or generates a UUID, echoes it in the response header, puts it in the log pattern, and includes it as `requestId` in every error body, including 401, 403 and 429. |
| Time | Stored `LocalDateTime` values use Asia/Manila through `LibraryTime` (entities) and the loan `Clock` (services). |
| Pagination | Every list endpoint returns a page; `spring.data.web.pageable.max-page-size=100` caps the size. |

## Database Migration Rules

`V1__create_books_table.sql` creates `books` with a `BIGSERIAL` ID, the `created_at` column, and indexes. `V2__create_users_table.sql` creates `users` with identity, role, course, major, uniqueness, format, and cross-field constraints, then inserts the historical `ADMINISTRATOR` account (library-assistant role) using the configured BCrypt hash. V3 adds the LIBRARIAN role and matching staff academic-field constraints. V4 adds `books.copy_status` and the `loans` table with state checks, foreign-key delete restrictions, borrower-history indexes, and the one-active-loan-per-copy index. V5 adds optional `books.description` and renames `price` to `replacement_cost` while preserving stored amounts. No librarian or loan data is seeded. V1 was corrected before release while the application had no persistent data; once a migration is deployed to a persistent environment, follow the forward-only rule below instead.

For every schema change:

1. Add a new forward-only migration under `src/main/resources/db/migration`.
2. Keep the JPA entity and migration definitions aligned, including length, nullability, precision, and scale.
3. Run against a fresh database and an existing database migrated from the previous version.
4. Start the application with `ddl-auto=validate` to catch drift.
5. Add integration coverage for nontrivial queries or constraints.

Docker Compose does not mount migrations into `/docker-entrypoint-initdb.d`; Flyway is the single schema authority. There is no automatic baseline for non-empty unmanaged schemas. During pre-release development, recreate the explicitly disposable empty volume after a V1 correction. Once persistent data exists, use a new forward migration instead.

## Build and Run Workflows

### Local build and tests

```powershell
.\mvnw.cmd clean test
.\mvnw.cmd clean verify
```

`test` runs the test suite. `verify` also generates JaCoCo output under `target/site/jacoco/`. Use the Maven Wrapper (`.\mvnw.cmd` on Windows or `./mvnw` on Linux/macOS) for all builds.

Run the PostgreSQL integration suite when Docker is available:

```powershell
.\mvnw.cmd -Pintegration verify            # unit + integration
.\mvnw.cmd -Pintegration -DskipUTs verify  # integration only (what CI's integration-test job runs)
```

Run `clean` after renaming or deleting resources: Maven copies resources into `target/` but never removes stale ones, so an old file there can keep influencing tests.

### Docker workflow

The Dockerfile is a multi-stage build. The build stage packages the JAR with the Maven Wrapper (a BuildKit cache mount keeps `~/.m2` between local builds) and extracts it into Spring Boot layers. The runtime stage is JRE-only, copies the `dependencies`, `spring-boot-loader`, `snapshot-dependencies`, and `application` layers in that order so a code change rebuilds only the small last layer, runs as a non-root user, and defines the image's single `/readyz` health check (Compose relies on it rather than repeating it).

```powershell
Copy-Item .env.example .env
docker compose up --build
```

The app is exposed on `127.0.0.1:8080` and PostgreSQL on `127.0.0.1:5432`. The optional `proxy` profile (`docker compose --profile proxy up`) adds a Caddy reverse proxy from [`infra/caddy/Caddyfile`](../../infra/caddy/Caddyfile) that terminates TLS on ports 80/443 for `LIBRO_DOMAIN`, adds HSTS, and is the only publicly bound service; set `SERVER_FORWARD_HEADERS_STRATEGY=native` when using it. PostgreSQL has a `pg_isready` health check, and the app waits for the database before startup and reports healthy from `/readyz`.

Persistent data lives in the `postgres_data` Docker volume. `docker compose down` retains it; `docker compose down -v` deletes it. Never run the latter on behalf of a user unless data destruction is explicitly requested and confirmed.

### Continuous integration

`.github/workflows/ci.yml` (GitHub Actions, read-only permissions, superseded runs cancelled) runs on every pull request and on pushes to `main`:

| Job | Command | Purpose |
| --- | --- | --- |
| `unit-test` | `./mvnw -B -ntp verify` | Docker-free unit and MVC suite with the 80% JaCoCo gate; uploads reports. |
| `integration-test` | `./mvnw -B -ntp -Pintegration -DskipUTs -Djacoco.haltOnFailure=false verify` | Only the PostgreSQL suite through Testcontainers, including `LoanConcurrencyIT`. Unit tests already ran in `unit-test`. |
| `security-scan` | `scripts/ci/trivy-scan.sh fs`, then `image` | Fails on HIGH/CRITICAL findings with a fix available, in dependencies and in the built image. Accepted findings go in `.trivyignore` with an expiry note; the same script gates the CD image. |

Require all three checks in branch protection for `main`. `.github/dependabot.yml` opens weekly update pull requests for Maven, Docker and GitHub Actions; raise Spring Boot-managed libraries through the parent version, not individually.

## Design Patterns and Rationale

### Layered architecture

HTTP, business logic, persistence, and database concerns live in distinct layers. This limits coupling and enables service tests without web or database startup.

### Feature-oriented packages

Book-related code is grouped under `app.book`, while cross-cutting concerns live under `app.global` and security under `app.auth`. New cohesive domains should follow the same feature package style rather than creating repository-wide folders by technical type.

### Repository pattern

The repository abstracts persistence and let's Spring Data generate routine implementations. Service code depends on a narrow data-access interface instead of an `EntityManager` or SQL details.

### DTO pattern

Request and response models isolate external contracts from JPA entities. Validation belongs on inbound DTOs; entity constraints remain a defense-in-depth persistence invariant. **Entity objects must never be instantiated directly from client input, and must never be returned directly to clients.** All controller inputs are request DTOs (e.g., `BookRequestDTO`, `BookPatchRequestDTO`) and all controller outputs are response DTOs (e.g., `BookResponseDTO`, `LibraryStatisticsDTO`). `BookMapper` is the sole entity conversion point.

Reasons:
1. Decouples the entity model from the client-facing API so the database schema can evolve independently.
2. Ties the entity model only to the database for data integrity and prevents accidental leakage of database-only fields.
3. Enables request validation at the boundary via Jakarta Validation on request DTOs.
4. Allows response customization without changing entities, including aggregation into read-only DTOs like `LibraryStatisticsDTO`.

### Mapper pattern

Each feature has one mapper (`BookMapper`, `UserMapper`, `LoanMapper`), called only by its service. Keep mapping deterministic and free of database or HTTP behavior.

### Service/facade pattern

`BookService` presents the application operations needed by the controller and coordinates multiple repository calls for statistics and updates.

### Dependency injection

Constructor injection is used throughout. `BookController` declares its constructor directly; `BookService` receives a Lombok-generated constructor through `@AllArgsConstructor`. Keep dependencies `final` and avoid field injection.

### Transaction script and unit of work

Each write service method defines a transaction boundary. Hibernate's persistence context acts as a unit of work, tracking managed entity changes and flushing at commit.

### Global exception translation

Central advice maps Java/application exceptions to stable HTTP errors, keeping error construction out of endpoint methods.

## Testing Strategy

Test sources mirror the main packages under `src/test/java/app/`: a test lives in the same package as the class it tests (for example `app.book.service.BookServiceTest`). The class-name suffix decides which plugin runs it: `*Test` runs in Surefire (`./mvnw verify`), `*IT` runs in Failsafe (`-Pintegration`). Shared test infrastructure lives in `app.support`. Spring tests use `@ActiveProfiles("test")`. Use the current run's `target/surefire-reports/` and `target/failsafe-reports/` as the source for test counts; do not maintain totals in this guide.

- `BookControllerMvcTest`: Spring Boot 4 MVC-slice coverage for route/status contracts, JSON shapes, invalid request bodies, pagination and query binding, and global exception responses. It uses a mocked service (which returns DTOs) and does not start JPA, Flyway, or PostgreSQL.

- `UserControllerMvcTest`: MVC-slice coverage for every user route, binding and validation behavior, pagination, response shapes, 404/409 error bodies, and exclusion of password data from public responses. It uses a mocked `UserService` and does not start JPA, Flyway, or PostgreSQL.
- `SecurityFlowMvcTest`: MVC-slice checks for public signup/login, allowlisted and denied CORS preflights, real BCrypt credential authentication, signed-token issue/validation, generic login failures, Basic rejection, invalid token signatures/claims/algorithms/transports, and route role gates.
- `HealthEndpointMvcTest`: checks public liveness/readiness probes, hidden health details, and the protected-by-default Actuator surface.
- `PublicRouteRateLimitInterceptorTest`: checks per-route/client quotas, trusted forwarded address processing, and rejection of unprocessed spoofed `X-Forwarded-For` headers.
- `JwtConfigurationTest`: direct checks that a minimum-size Base64 key is accepted and malformed or undersized keys fail at startup.
- `LoanSecurityMvcTest`: MVC-slice checks for loan role gates, principal-derived borrower identity, authenticated history access, and header-only unsafe requests without CSRF.
- `OpenApiMvcTest`: generated `/v3/api-docs` contract checks in an MVC slice, covering the Bearer scheme, public signup/login operations, login request/response errors, representative paths, response codes, write-only password inputs, public response privacy, and loan-history pagination/sort documentation without Docker.

- `UserServiceTest`: Mockito-based service unit tests for partial-update normalization, validation, password verification and encoding, the BCrypt 72 UTF-8 byte boundary, unchanged-email handling, duplicate-email rejection, missing-user handling, and dirty-checking expectations.
- `LoanServiceTest`: Mockito-based service tests for student/faculty checkout, overdue and three-active-loan limits, copy availability, return ownership and terminal state, staff lost-resolution scope, history access, sorting, and page-size bounds.

- `BookServiceTest`: Mockito-based service unit tests for CRUD rules, text normalization, entity-validation enforcement, replacement-cost range validation, dirty-checking expectations, search, sorting, replacement-cost statistics, typed projections, genre distribution, and other aggregate behavior.
- `BookTest`: entity-construction, lifecycle, and direct Jakarta Validator tests for request DTO and entity constraints.
- `BookMapperTest`: focused tests for entity-to-DTO mapping, null inputs, and list mapping.
- `GlobalExceptionHandlerTest`: direct unit tests for exception handlers, including status/error contracts, DTO/entity/service validation handling, and non-leakage of internal parser, database, constraint, and fallback exception details.
- `BookPersistenceIT`: PostgreSQL 18 integration tests for Flyway migrations, Hibernate schema validation, repository queries and projections, committed service updates, and a catalog edit waiting for lost-loan resolution while preserving the committed `LOST` copy status.
- `LoanPersistenceIT`: PostgreSQL integration tests for staff loan query counts and filters, committed return history, the one-active-loan-per-copy index, and foreign-key deletion restrictions.
- `LoanMigrationIT`: isolated PostgreSQL V3-to-V4 upgrade with retained user/book rows, loan indexes and constraints, and V4 Hibernate schema validation.
- `UserPersistenceIT`: PostgreSQL integration tests for normalized creation, stored hashes, committed updates, and uniqueness/format constraints.
- `LibrarianBootstrapIT`: PostgreSQL tests for first-account creation, repeated-start idempotence, unset configuration, and invalid/partial configuration. Integration tests share one context-managed PostgreSQL container and use a resource lock. Run them with `./mvnw -Pintegration verify` (or `mvnw.cmd` on Windows).

The unit/MVC suite execution setup is deliberately small and optimized:

- `src/test/resources/junit-platform.properties` enables concurrent execution between test classes but keeps methods within each class on the same thread.
- The PostgreSQL integration classes share a Spring context and a JUnit resource lock. Use rollback for query fixtures and targeted cleanup for committed write tests; avoid database cleanup in read-only checks.
- `BookServiceTest` uses `@TestInstance(PER_CLASS)` so its repository mock and `BookService` are constructed once. `@BeforeEach` resets the repository mock and rebuilds mutable book fixtures, preserving test isolation. The stateless `BookMapper` is real rather than mocked.
- `BookTest` shares one thread-safe Jakarta `Validator` and closes its `ValidatorFactory` in `@AfterAll` instead of rebuilding a factory per validation test.
- `GlobalExceptionHandlerTest` shares its stateless handler and constructs real Spring exceptions where practical, avoiding extra mock creation.
- `src/test/resources/logback-test.xml` disables logs only in tests. Expected exception-handler tests must not flood test output with stack traces.

These choices keep the feedback loop small without deleting, merging, or weakening tests. Build times are environment-dependent; first-time Maven dependency downloads, Mockito/Byte Buddy agent startup, and machine resources may change the total.

The exception-handler tests verify direct Java method behavior without loading Spring MVC. The generated OpenAPI contract and JWT security flow run in the normal Docker-free suite. Login tests mock `UserRepository`, so they verify Spring's lookup path but do not verify the university-ID query against PostgreSQL. The PostgreSQL profile provides real-database coverage when Docker is available; a passing unit/MVC suite alone does not prove persistence behavior.

Choose test scope based on the change:

| Change | Minimum useful verification |
|---|---|
| Pure service rule | JUnit/Mockito service unit test |
| DTO constraint | Validator unit test and invalid boundary cases |
| Mapper behavior | Focused mapper unit test |
| Controller contract | MVC test for route, status, body, and validation |
| Generated OpenAPI contract | Focused MVC slice test for routes, response codes, and sensitive schema fields |
| Repository query | JPA integration test, preferably PostgreSQL-backed for database-specific behavior |
| Migration | Fresh and upgrade-path PostgreSQL startup test |
| Security rule | Authenticated/unauthenticated MVC or integration tests |

Name tests as behavior statements, keep monetary assertions scale-safe, and verify observable outcomes rather than internal calls unless the call itself is the contract. Do not weaken an assertion merely to make a changed implementation pass.
Keep plain unit tests free of Spring startup and keep full-context or container tests for behavior their smaller neighbors cannot prove. Avoid timing assertions without a measured baseline, and serialize integration classes that share a database.

Before handoff, run at least:

```powershell
.\mvnw.cmd test
```

For dependency, persistence, build, or release changes, run:

```powershell
.\mvnw.cmd clean verify
```

If tests cannot run because Java 25, Maven, Docker, PostgreSQL, or network access is unavailable, state exactly what was not verified.

## Contribution Workflow

1. Read the affected controller, service, repository, DTO/entity, tests, configuration, and migration before editing.
2. Check `git status` and preserve unrelated user changes.
3. Define whether the change alters the HTTP contract, business rules, persistence model, security posture, or deployment workflow.
4. Implement in the owning layer and avoid incidental refactors.
5. Add or update tests that fail without the intended behavior.
6. Run formatting/compiler/tests appropriate to the change.
7. Update `README.md` and this guide when commands, endpoints, architecture, dependencies, or contributor expectations change.
8. Summarize changed behavior, verification performed, and remaining risks.

Keep commits focused. Do not mix schema changes, API-breaking response changes, security changes, and broad formatting in one contribution unless they are inseparable.

## Coding Conventions

- Use Java 25 features only when they improve clarity and remain compatible with the configured toolchain.
- Prefer constructor injection and immutable dependencies.
- Keep controllers thin and services responsible for business rules.
- Return DTOs, not entities, at API boundaries. Entity objects must never be instantiated from client input or returned directly to clients; services map entities to response DTOs with the feature's mapper before returning.
- Use `BigDecimal` for money and define rounding explicitly if a calculation can produce additional scale.
- Trim and normalize user input deliberately; do not silently change semantics in only one endpoint.
- Validate both structure (`@Valid`) and cross-field/business rules (service layer).
- Use parameterized JPQL or derived queries. Never concatenate untrusted values into queries.
- Restrict client-controlled sort fields with an allowlist.
- Use SLF4J parameterized logging through Lombok's `@Slf4j`; do not use `System.out`.
- Avoid logging complete request bodies if they may later contain sensitive data.
- Keep public exceptions meaningful but free of internal implementation details.
- Add concise Javadoc where a method's contract, transaction behavior, or query shape is not obvious.
- Do not add abstractions for hypothetical future domains; extract when duplication or a clear boundary exists.

## API Compatibility Rules

Treat these as breaking changes unless explicitly requested:

- Renaming or removing routes, query parameters, or JSON fields.
- Changing response envelope shape or HTTP status codes.
- Tightening validation in a way that rejects previously accepted requests.
- Changing PATCH null/blank semantics.
- Altering pagination defaults or maximum size.
- Changing replacement cost precision, scale, or inclusive range behavior.
- Changing which routes are public or which credentials a client must send.

When a breaking change is intended, document migration guidance and update all examples and tests in the same contribution.

## Known Limitations and Risks

- Signup, login, and Actuator health probes are public; all other routes require stateless JWT Bearer authentication. User profiles and passwords are owner-scoped with role-limited staff management; the directory and cross-account profile reads are staff-only.
- A role/password change or account deletion may leave a token valid until expiry plus 30 seconds of clock skew; this is an accepted policy (see the JWT design). App-level per-IP throttles cover login and signup; before public internet exposure, the deployment gateway must add shared throttling and monitoring and prevent direct access to the app. Forwarded headers remain off by default and may be enabled only behind a trusted proxy.
- Swagger UI and `/v3/api-docs` are off unless `LIBRO_API_DOCS_ENABLED=true`; enable them only where the API contract may be public.
- CORS allows no browser origin by default; configure exact UI origins before serving a cross-origin browser client.
- V2 creates the users table and may already be recorded in persistent databases; do not edit it after deployment.
- Signup always creates a student; librarians create faculty and library assistants; students/faculty may delete themselves, administrators may delete student/faculty accounts, and librarians may also delete library assistant accounts.
- Loan routes and V4 are implemented. `LoanServiceTest` and `LoanSecurityMvcTest` cover service rules and route gates; `BookPersistenceIT`, `LoanPersistenceIT`, and `LoanConcurrencyIT` cover the current schema, the lost-status/catalog-edit lock interaction, loan history constraints, terminal return persistence, and concurrent checkout limits. User routes have MVC contract, PostgreSQL integration, and JWT security-flow coverage; the security MVC tests mock the user repository.
- `BookPersistenceIT` verifies a fresh PostgreSQL database migrates through V5 and passes Hibernate schema validation. It also migrates an isolated V4 schema to V5 and checks that stored monetary values remain intact; running the integration profile requires Docker and a test-only `LIBRO_ADMIN_PASSWORD_HASH`.
- `LoanMigrationIT` verifies the V3-to-V4 upgrade path against PostgreSQL with V4 Hibernate validation.
- API envelope and error `timestamp` fields are epoch milliseconds rather than ISO-8601 values. Loan date-time fields use `LocalDateTime` and are serialized without a timezone offset; the loan contract treats them as Asia/Manila local time.
- Public probes are `/livez` and `/readyz` (details hidden; Docker checks use readiness). Actuator `health` and `prometheus` are served only on the private management port, which must not be published.
- Ordinary indexes may not accelerate case-insensitive substring searches as expected.
- The database enforces `NOT NULL` for replacement cost but not a positive-value check; application validation is the current positive-value guard.
- Book API clients must send/read `replacementCost` and use replacement-cost query names. V5 preserves stored values during the column rename. The old `price` field, search value, sort key, and range route are removed, so existing clients must update.

Address these in focused, tested contributions. Do not bundle opportunistic fixes into unrelated work.

## Guidance for AI Agents

Before changing code:

- Inspect the repository rather than relying on README snippets or prior assumptions.
- Read complete affected files, including tests and configuration.
- Search for every call site before renaming a method, route, field, or type.
- Check for uncommitted work and never discard changes you did not create.
- State assumptions only when repository evidence cannot resolve them.

While changing code:

- Make the smallest coherent change that fully solves the request.
- Preserve public behavior unless the request authorizes a contract change.
- Do not fabricate dependencies, endpoints, migrations, commands, or test results.
- Do not edit generated output under `target/`.
- Do not modify an existing applied migration to evolve the schema.
- Never insert credentials or use destructive database/volume commands without explicit authorization.
- Keep documentation synchronized with implementation.

Before reporting completion:

- Review the diff for accidental changes and stale comments.
- Compile and run relevant tests where the environment permits.
- Confirm new migrations and JPA mappings agree.
- Confirm error cases as well as the happy path.
- Report commands actually run and any verification gaps honestly.

## Definition of Done

A contribution is complete when the requested behavior is implemented in the correct layer, public contracts are deliberate, validation and error handling are coherent, persistence changes have forward migrations, relevant tests pass, documentation reflects the result, unrelated work is preserved, and remaining limitations are clearly disclosed.
