# AGENTS.md

## Purpose

This file is the working guide for AI coding agents and human contributors to the Libro: Library API System. Read it before changing the project. It documents the repository as it exists, the intended architecture, the contracts between layers, and the checks expected before a contribution is considered complete.

The application is a single-module Spring Boot REST API for managing a library's book collection. It exposes CRUD, search, pagination, sorting, price filtering, aggregation, and health endpoints backed by PostgreSQL.

## Project Snapshot

| Concern | Current choice                                                          |
|---|-------------------------------------------------------------------------|
| Language | Java 25                                                                 |
| Framework | Spring Boot 4.1.0                                                       |
| Build | Maven, producing an executable Spring Boot JAR                          |
| HTTP layer | Spring MVC                                                              |
| Persistence | Spring Data JPA and Hibernate                                           |
| Database | PostgreSQL 18                                                           |
| Schema management | Flyway SQL migrations                                                   |
| Validation | Jakarta Bean Validation                                                 |
| Security | Spring Security filter chain; all requests currently permitted and CSRF disabled |
| Boilerplate reduction | Lombok 1.18.46                                                          |
| Testing | JUnit 5, Mockito, Jakarta Validator                                     |
| Coverage | JaCoCo report during Maven `verify`                                     |
| Containers | Dockerfile plus Docker Compose                                          |

The Maven coordinates are `kyle.com:library-api-system:1.0-SNAPSHOT`. There is no Maven Wrapper in the repository, so local commands require a compatible `mvn` installation unless Maven is added or supplied by the development environment.

## Repository Map

```text
library-api-system/
|-- AGENTS.md
|-- README.md
|-- pom.xml
|-- Dockerfile
|-- docker-compose.yml
|-- envFileExample
`-- src/
    |-- main/
    |   |-- java/app/
    |   |   |-- LibraryApplication.java
    |   |   |-- auth/
    |   |   |   `-- SecurityConfig.java
    |   |   |-- book/
    |   |   |   |-- controller/BookAPI.java
    |   |   |   |-- dto/
    |   |   |   |   |-- BookRequestDTO.java
    |   |   |   |   |-- BookResponseDTO.java
    |   |   |   |   `-- LibraryStatisticsDTO.java
    |   |   |   |-- entity/Book.java
    |   |   |   |-- exceptions/
    |   |   |   |   |-- BookNotFoundException.java
    |   |   |   |   `-- BookValidationException.java
    |   |   |   |-- mapper/BookMapper.java
    |   |   |   |-- repository/BookRepository.java
    |   |   |   `-- service/BookService.java
    |   |   `-- global/
    |   |       |-- exceptions/GlobalExceptionHandler.java
    |   |       `-- responses/
    |   |           |-- ApiResponse.java
    |   |           `-- ErrorResponse.java
    |   `-- resources/
    |       |-- application.properties
    |       `-- db/migration/
    |           |-- V1_create_books_table.sql
    |           |-- V2_create_users_table.sql
    |           `-- V3__add_created_at_to_books.sql
    `-- test/
        |-- java/unit/
        |   |-- BookMapperTest.java
        |   |-- BookServiceTest.java
        |   |-- BookTest.java
        |   `-- GlobalExceptionHandlerTest.java
        `-- resources/
            |-- junit-platform.properties
            `-- logback-test.xml
```

Keep production code below the root `app` package. `LibraryApplication` sits at that root so Spring's default component scan discovers controllers, services, repositories, mappers, advice, and configuration beneath it.

## Runtime Architecture

The standard request path is:

```text
HTTP client
  -> Spring Security filter chain
  -> BookAPI controller
  -> BookService
  -> BookRepository
  -> Hibernate/JPA
  -> PostgreSQL
```

The response path generally converts `Book` entities to DTOs through `BookMapper`. Exceptions escape their originating layer and are converted to JSON by `GlobalExceptionHandler`.

### Application entry point

`app.LibraryApplication` uses `@SpringBootApplication`, which combines configuration, auto-configuration, and component scanning. Do not move it below a feature package unless component scanning is configured explicitly.

### Controller layer

`BookAPI` owns HTTP concerns only:

- Base route: `/app/books`.
- Parses path variables, query parameters, pagination, and JSON bodies.
- Applies `@Valid` to complete create and replace payloads.
- Deliberately does not apply `@Valid` to PATCH payloads because omitted fields are represented by `null`.
- Delegates business rules to `BookService`.
- Uses `BookMapper` to prevent entities from becoming the public API representation.
- Chooses HTTP status codes and response envelopes.

Do not add repository access to the controller. New endpoint logic should remain thin and be independently testable in the service.

### Service layer

`BookService` is the business-logic boundary and transaction owner. It coordinates repositories and mapping, validates rules not adequately represented by request annotations, and translates missing data into `BookNotFoundException`.

Write methods are transactional:

- `addBook` maps and explicitly saves a new entity.
- `patchBook` loads a managed entity and changes only supplied fields.
- `replaceBook` validates a complete replacement, then maps all fields onto a managed entity.
- `deleteBookById` executes a modifying JPQL query and checks the affected row count.

Before create, PATCH, and PUT reach the persistence boundary, the service explicitly validates
the resulting `Book` with Jakarta `Validator`. This makes entity constraints effective for
internal service callers as well as HTTP callers. Entity constraint violations are translated to
the standard HTTP 400 validation response by `GlobalExceptionHandler`.

PATCH and PUT business-rule failures use `BookValidationException`. Its dedicated global handler
uses the shared validation-response builder, keeping book validation distinct from unrelated
argument errors while preserving the public HTTP 400 response shape.

PATCH and PUT intentionally omit `repository.save(existingBook)`. Hibernate dirty checking flushes changes to managed entities when the transaction commits. Preserve this behavior unless the persistence model changes. Calling these methods outside Spring's managed proxy, or removing `@Transactional`, changes that guarantee.

#### PUT vs PATCH: partial-update design

The controller layer distinguishes full replacement (PUT) from partial updates (PATCH) through validation strategy:

- **PUT (`replaceBook`)**: The controller annotates the request body with `@Valid @RequestBody BookRequestDTO`. This activates all DTO annotations (`@NotBlank`, `@Size`, `@Positive`, `@NotNull`) on every invocation, enforcing a complete, valid payload. The service method (`replaceBook`) additionally performs manual checks as defense-in-depth, protecting callers that bypass the controller's `@Valid` (e.g., scheduled jobs, internal consumers). The mapper's `updateBookFromDto` overwrites every mutable field onto the fetched managed entity.

- **PATCH (`patchBook`)**: The controller annotates the request body with `@RequestBody BookRequestDTO` (no `@Valid`). This is deliberate: a PATCH request represents a partial update where omitted fields arrive as `null`, and applying `@NotBlank` or `@Positive` to a `null` PATCH field would reject legitimate partial updates. Instead, the service applies field-level conditional logic via a `hasText()` helper that skips `null` and blank strings entirely, and only validates price against zero or negativity when a non-null price is supplied.

This design solves a problem solved by many implementations incorrectly:

- **Applying PUT rules to PATCH**: If `@Valid` were on PATCH, sending `{"price": 15.00}` would fail because `title`, `author`, and `genre` are `null` and violate `@NotBlank`. This breaks partial updates entirely.
- **Applying PATCH rules to PUT**: If PUT used the same `hasText()` skip logic, a client could PUT `{"title": null, "author": "Orwell", ...}` and the `null` title would be silently ignored, leaving the old title in place — violating the "complete replacement" contract of PUT.

The `hasText()` method (`s != null && !s.trim().isEmpty()`) also correctly handles the edge case where a client sends `"title": ""` or `"title": "   "` — these are treated as "no update" rather than as an attempt to blank the field. This preserves existing data rather than corrupting it with empty strings.

Read methods handle pagination, field-restricted sorting, searches, price ranges, genre distribution, and aggregate statistics. Keep input allowlists in the service; never pass arbitrary client field names directly to JPA sorting or query construction.

### Repository layer

`BookRepository` extends `JpaRepository<Book, Long>`, gaining standard CRUD, pagination, sorting, and count operations. Its custom methods use three Spring Data styles:

- Derived queries, such as `findByTitleContainingIgnoreCase` and `findTopByOrderByPriceDesc`.
- JPQL aggregate/select queries for ranges, totals, averages, and genre distribution.
- A modifying JPQL delete returning the affected row count.

`deleteBookById` uses `@Modifying(clearAutomatically = true)` because bulk JPQL bypasses normal entity lifecycle synchronization. If more bulk updates are introduced, account for stale persistence-context state in the same way.

Aggregate repository methods currently return low-level shapes (`Object[]` and `List<Object[]>`). The service is responsible for type conversion and API-friendly DTO construction. Prefer typed projections for future complex aggregations if they improve safety without adding unnecessary abstraction.

### Entity and database model

`Book` maps to `books`:

| Java field | Java type | Database definition | Constraints |
|---|---|---|---|
| `id` | `Long` | `SERIAL PRIMARY KEY` | Generated with `IDENTITY` |
| `title` | `String` | `VARCHAR(100)` | Nonblank, not null |
| `author` | `String` | `VARCHAR(50)` | Nonblank, not null |
| `genre` | `String` | `VARCHAR(50)` | Nonblank, not null |
| `price` | `BigDecimal` | `DECIMAL(10,2)` | Not null, positive at application boundary |
| `createdAt` | `LocalDateTime` | `TIMESTAMP` | Not null, set on insert, immutable after creation, omitted from response DTOs |

Use `BigDecimal` for all money-related values and comparisons. Do not replace it with `double` or `float`. Use `compareTo` for numerical equality in tests because `BigDecimal.equals` also compares scale.

The V1 migration creates indexes on title, author, genre, and price. PostgreSQL may not fully exploit ordinary B-tree indexes for every case-insensitive contains query (`%term%`), so benchmark before claiming those indexes optimize substring search.

### DTO and mapper boundary

`BookRequestDTO` is the inbound contract. It validates required text lengths and positive, non-null prices. Unknown JSON fields are ignored by Jackson.

`BookResponseDTO` is the regular outbound book shape and intentionally omits the internal `createdAt` timestamp. `LibraryStatisticsDTO` is an immutable Java record containing total count, total value, and the most expensive book response.

`BookMapper` centralizes:

- Request DTO to new entity conversion.
- Existing entity replacement for PUT.
- Entity to response DTO conversion.
- Entity-list to response-list conversion.

Do not expose `Book` directly from new endpoints. Entity exposure couples clients to persistence structure and makes future database changes risky. Keep partial-update semantics in the service; `updateBookFromDto` is full replacement behavior.

### Error handling and response contracts

`GlobalExceptionHandler` is a `@RestControllerAdvice` shared by all controllers. It maps validation, malformed input, missing books/routes, unsupported methods, invalid sort properties, database failures, and unexpected exceptions to `ErrorResponse`.

`ErrorResponse` contains:

```json
{
  "error": "Validation failed",
  "details": "Price must be greater than 0",
  "timestamp": 0,
  "statusCode": 400
}
```

Successful mutation and selected statistic endpoints use generic `ApiResponse<T>` with `success`, `message`, `data`, and `timestamp`. Some read endpoints return DTOs, lists, maps, or Spring `Page` directly. This mixed contract is current behavior; do not silently normalize it in an unrelated change because that would break API clients.

Handler order matters conceptually. `DataIntegrityViolationException` is a subtype of `DataAccessException`; preserve the specific conflict behavior when refactoring exception handling. Never return stack traces, SQL, credentials, or database internals to clients.

### Security

`SecurityConfig` installs Spring Security but currently permits every request and disables CSRF. This is an explicit development-stage posture, not production authentication. Do not describe the API as protected.

If authentication is introduced, treat it as an API contract and architecture change. Add endpoint authorization rules, an authentication mechanism, tests for allowed and denied requests, credential/secret handling, and updated documentation together. Reconsider CSRF based on whether credentials are cookie-based or token-based.

## Endpoint Inventory

All routes use `/app/books` as their base.

| Method | Path | Purpose | Response shape |
|---|---|---|---|
| GET | `/health` | API and database reachability | `ApiResponse<Map<String, Boolean>>` |
| GET | `/stats` | Count, total value, most expensive book | `LibraryStatisticsDTO` |
| GET | `/stats/average-price` | Average collection price | `ApiResponse<BigDecimal>` |
| GET | `/stats/count` | Number of books | `ApiResponse<Long>` |
| GET | `/search?type=...&value=...` | Search by author, title, genre, or exact price | List of `BookResponseDTO` |
| GET | `/budget?maxPrice=...` | Books at or below a maximum price | List of `BookResponseDTO` |
| GET | `/all?page=...&size=...&sort=...` | Paginated collection | Spring `Page<BookResponseDTO>` |
| GET | `/sorted?category=...` | Ascending sort by an allowed field | List of `BookResponseDTO` |
| GET | `/genre` | Count grouped by genre | Map of genre to count |
| GET | `/price?minPrice=...&maxPrice=...` | Inclusive price range | List of `BookResponseDTO` |
| GET | `/{id}` | Retrieve one book | `BookResponseDTO` |
| POST | `/add` | Create a validated book | `ApiResponse<BookResponseDTO>`, HTTP 201 |
| PATCH | `/{id}` | Partially update supplied fields | `ApiResponse<BookResponseDTO>` |
| PUT | `/{id}` | Replace all mutable fields | `ApiResponse<BookResponseDTO>` |
| DELETE | `/{id}` | Delete one book | `ApiResponse<Void>` |

When adding an endpoint, update this file and `README.md`, provide request/response examples where useful, and add tests at the appropriate layer.

## Dependency Guide

Dependencies are declared in `pom.xml`. The Spring Boot parent manages versions for most Spring ecosystem dependencies.

### `spring-boot-starter-web`

Provides Spring MVC, embedded HTTP server support, JSON serialization through Jackson, controllers, routing annotations, `ResponseEntity`, and web exception types. It implements the entire HTTP adapter around the book use cases.

### `spring-boot-starter-validation`

Provides Jakarta Validation and its implementation. `@Valid` in `BookAPI` activates constraints on `BookRequestDTO`; the resulting `MethodArgumentNotValidException` is transformed by global advice.

### `spring-boot-starter-data-jpa`

Provides Spring Data repositories, JPA, Hibernate, transaction integration, derived-query parsing, JPQL execution, pagination, and sorting. It is responsible for repository implementation generation and dirty checking.

### `spring-boot-starter-security`

Provides the servlet security filter chain. The project defines a `SecurityFilterChain` bean so default generated-password authentication is replaced by permit-all behavior.

### PostgreSQL JDBC driver

Runtime driver used by the datasource to communicate with PostgreSQL. It is not required at compile time, hence runtime scope.

### H2

An in-memory database driver declared at runtime for development/testing. The current default configuration still requires explicit `SPRING_DATASOURCE_*` values and does not define an H2 profile. Therefore, the dependency alone does not make the application or tests automatically run against H2. Add a deliberate test/profile configuration before relying on it.

### Flyway core and PostgreSQL module

Flyway 13.3.0 discovers versioned scripts in `classpath:db/migration`, records applied versions in `flyway_schema_history`, and migrates the database before JPA validation. The PostgreSQL module supplies database-specific Flyway support.

Never edit a migration that may have been applied to a shared or persistent database. Add the next versioned migration (`V3__description.sql`, and so on). Existing filenames use a single underscore after the version; use Flyway's conventional double underscore for new files unless the repository deliberately standardizes otherwise, and verify discovery during startup.

### Lombok

Compile-time annotation processing generates DTO/entity accessors, constructors, `toString`, service constructor injection, and SLF4J logger fields through the `@Slf4j` annotation. The compiler plugin explicitly includes the Lombok processor. Contributors need IDE annotation-processing support for accurate editor diagnostics, though Maven remains authoritative.

### `spring-boot-starter-test`

Provides the JUnit 5 test platform, Mockito, Spring testing utilities, AssertJ, and related test infrastructure. Current tests instantiate `BookService` with Mockito rather than starting a Spring application context.

### Maven build plugins

- `spring-boot-maven-plugin` repackages the artifact as an executable fat JAR.
- `maven-compiler-plugin` compiles Java 25 and runs Lombok's annotation processor.
- `jacoco-maven-plugin` attaches its agent during tests and writes an HTML/XML coverage report during `verify`.

## Configuration and Environment

`application.properties` requires these environment variables:

| Variable | Purpose | Docker Compose value |
|---|---|---|
| `SPRING_DATASOURCE_URL` | JDBC database URL | `jdbc:postgresql://db:5432/${POSTGRES_DB}` |
| `SPRING_DATASOURCE_USERNAME` | Database role | `${POSTGRES_USER}` |
| `SPRING_DATASOURCE_PASSWORD` | Database password | `${POSTGRES_PASSWORD}` |

Docker Compose additionally reads `POSTGRES_DB`, `POSTGRES_USER`, and `POSTGRES_PASSWORD` from a root `.env` file. `envFileExample` is a template; never commit real credentials. Do not log secrets or place them in source, tests, migrations, README examples, or exception messages.

Important persistence settings:

- `spring.jpa.hibernate.ddl-auto=validate`: Hibernate checks entity/schema compatibility but does not create or alter tables.
- `spring.flyway.enabled=true`: Flyway owns migration execution.
- `spring.sql.init.mode=never`: legacy `schema.sql`/`data.sql` initialization is disabled.
- `spring.data.web.pageable.max-page-size=100`: limits API-requested pages.
- MVC missing-handler settings route unknown endpoints into JSON exception handling.

## Database Migration Rules

`V1_create_books_table.sql` creates `books` with the `created_at` column and its indexes. `V2_create_users_table.sql` currently has no SQL content. The V3 migration has been removed.

For every schema change:

1. Add a new forward-only migration under `src/main/resources/db/migration`.
2. Keep the JPA entity and migration definitions aligned, including length, nullability, precision, and scale.
3. Run against a fresh database and an existing database migrated from the previous version.
4. Start the application with `ddl-auto=validate` to catch drift.
5. Add integration coverage for nontrivial queries or constraints.

Docker Compose currently mounts V1 into `/docker-entrypoint-initdb.d` while the application also runs Flyway from its JAR. PostgreSQL init scripts run only when the data volume is first created; Flyway runs on application startup. Avoid adding more Docker init mounts. Prefer Flyway as the single schema authority, and consider removing the V1 mount in a focused infrastructure change after validating fresh startup behavior.

## Build and Run Workflows

### Local build and tests

```powershell
mvn clean test
mvn clean verify
```

`test` runs the test suite. `verify` also generates JaCoCo output under `target/site/jacoco/`. Use `mvn clean package` to produce the JAR expected by the Dockerfile.

### Docker workflow

The Dockerfile is not a multi-stage build. It copies `target/*.jar`, so package the application before building the image:

```powershell
Copy-Item envFileExample .env
mvn clean package
docker compose up --build
```

The app is exposed on port 8080 and PostgreSQL on 5432. The Compose `depends_on` directive controls startup order but does not wait for database readiness. Spring/containers may restart until PostgreSQL accepts connections. A production-quality change should add a database health check and conditional dependency rather than relying on timing.

Persistent data lives in the `postgres_data` Docker volume. `docker compose down` retains it; `docker compose down -v` deletes it. Never run the latter on behalf of a user unless data destruction is explicitly requested and confirmed.

## Design Patterns and Rationale

### Layered architecture

HTTP, business logic, persistence, and database concerns live in distinct layers. This limits coupling and enables service tests without web or database startup.

### Feature-oriented packages

Book-related code is grouped under `app.book`, while cross-cutting concerns live under `app.global` and security under `app.auth`. New cohesive domains should follow the same feature package style rather than creating repository-wide folders by technical type.

### Repository pattern

The repository abstracts persistence and let's Spring Data generate routine implementations. Service code depends on a narrow data-access interface instead of an `EntityManager` or SQL details.

### DTO pattern

Request and response models isolate external contracts from JPA entities. Validation belongs on inbound DTOs; entity constraints remain a defense-in-depth persistence invariant. **Entity objects must never be instantiated directly from client input, and must never be returned directly to clients.** All controller inputs are request DTOs (e.g., `BookRequestDTO`) and all controller outputs are response DTOs (e.g., `BookResponseDTO`, `LibraryStatisticsDTO`). `BookMapper` is the sole conversion point.

Reasons:
1. Decouples the entity model from the client-facing API so the database schema can evolve independently.
2. Ties the entity model only to the database for data integrity and prevents accidental leakage of database-only fields.
3. Enables request validation at the boundary via Jakarta Validation on request DTOs.
4. Allows response customization without changing entities, including aggregation into read-only DTOs like `LibraryStatisticsDTO`.

### Mapper pattern

Mapping is centralized in `BookMapper` rather than duplicated across controllers and services. Keep mapping deterministic and free of database or HTTP behavior.

### Service/facade pattern

`BookService` presents the application operations needed by the controller and coordinates multiple repository calls for statistics and updates.

### Dependency injection

Constructor injection is used throughout. `BookAPI` declares its constructor directly; `BookService` receives a Lombok-generated constructor through `@AllArgsConstructor`. Keep dependencies `final` and avoid field injection.

### Transaction script and unit of work

Each write service method defines a transaction boundary. Hibernate's persistence context acts as a unit of work, tracking managed entity changes and flushing at commit.

### Global exception translation

Central advice maps Java/application exceptions to stable HTTP errors, keeping error construction out of endpoint methods.

## Testing Strategy

Current coverage consists of:

- `BookServiceTest`: 42 Mockito-based service unit tests for CRUD rules, entity-validation enforcement, dirty-checking expectations, search, sorting, pricing, and aggregates.
- `BookTest`: five entity-construction, lifecycle, and direct Jakarta Validator tests for request DTO and entity constraints.
- `BookMapperTest`: four focused tests for entity-to-DTO mapping, null inputs, and list mapping.
- `GlobalExceptionHandlerTest`: 14 direct unit tests for every exception handler, including status/error contracts, DTO/entity/service validation handling, and non-leakage of internal parser, database, constraint, and fallback exception details.

The suite contains 65 tests. Its execution setup is deliberately small and optimized:

- `src/test/resources/junit-platform.properties` enables concurrent execution between test classes but keeps methods within each class on the same thread.
- `BookServiceTest` uses `@TestInstance(PER_CLASS)` so its repository mock and `BookService` are constructed once. `@BeforeEach` resets the repository mock and rebuilds mutable book fixtures, preserving test isolation. The stateless `BookMapper` is real rather than mocked.
- `BookTest` shares one thread-safe Jakarta `Validator` and closes its `ValidatorFactory` in `@AfterAll` instead of rebuilding a factory per validation test.
- `GlobalExceptionHandlerTest` shares its stateless handler and constructs real Spring exceptions where practical, avoiding extra mock creation.
- `src/test/resources/logback-test.xml` disables logs only in tests. Expected exception-handler tests must not flood test output with stack traces.

These choices fixed a slow feedback loop without deleting, merging, or weakening tests. Reference measurements from the Java 25 development machine on September 1, 2026 were 5.098 seconds for a warm `mvn test`, 10.579 seconds for `mvn clean test`, and 12.596 seconds for `mvn clean verify`. Treat those figures as evidence from one environment, not a cross-machine performance requirement. First-time Maven dependency downloads may still dominate an initial run.

The exception-handler tests verify direct Java method behavior without loading Spring MVC. The tests do not currently prove controller routing, JSON serialization, security behavior, JPA query correctness, Flyway migration success, PostgreSQL compatibility, or transaction/dirty-checking behavior in a real persistence context. Mockito tests that verify no `save` call document intent but do not substitute for a JPA integration test.

Choose test scope based on the change:

| Change | Minimum useful verification |
|---|---|
| Pure service rule | JUnit/Mockito service unit test |
| DTO constraint | Validator unit test and invalid boundary cases |
| Mapper behavior | Focused mapper unit test |
| Controller contract | MVC test for route, status, body, and validation |
| Repository query | JPA integration test, preferably PostgreSQL-backed for database-specific behavior |
| Migration | Fresh and upgrade-path PostgreSQL startup test |
| Security rule | Authenticated/unauthenticated MVC or integration tests |

Name tests as behavior statements, keep monetary assertions scale-safe, and verify observable outcomes rather than internal calls unless the call itself is the contract. Do not weaken an assertion merely to make a changed implementation pass.

Before handoff, run at least:

```powershell
mvn test
```

For dependency, persistence, build, or release changes, run:

```powershell
mvn clean verify
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
- Return DTOs, not entities, at API boundaries. Entity objects must never be instantiated from client input or returned directly to clients; always use request and response DTOs with `BookMapper` as the conversion point.
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
- Changing price precision, scale, or inclusive range behavior.
- Replacing permit-all security with required authentication.

When a breaking change is intended, document migration guidance and update all examples and tests in the same contribution.

## Known Limitations and Risks

- Authentication and authorization are not implemented; every endpoint is public.
- CSRF is disabled.
- H2 is declared but has no dedicated application profile or integration-test setup.
- The Docker image requires a prebuilt JAR and does not build source itself.
- Compose startup ordering does not guarantee PostgreSQL readiness.
- Schema initialization is split conceptually between a Docker V1 init mount and application Flyway; Flyway should become the sole authority.
- V2 is currently empty and may already be recorded in persistent databases.
- Test coverage is predominantly unit-level; HTTP, JPA, migration, security, and container paths lack automated integration coverage.
- Success response shapes are inconsistent across endpoints.
- `timestamp` fields are epoch milliseconds rather than ISO-8601 values.
- Genre aggregation uses raw `Object[]` projections.
- Health checks prove a database count query can run but are not integrated with Spring Boot Actuator or container health checks.
- Ordinary indexes may not accelerate case-insensitive substring searches as expected.
- The database enforces `NOT NULL` for price but not a positive-value check; application validation is the current positive-price guard.

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
