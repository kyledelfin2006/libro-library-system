# Libro Development Problems Solved

## Why I built Libro

Libro is a library management system prototype. I built it as a practical backend system for managing a book collection while exploring the design decisions that matter in a real institutional application: data integrity, validation, persistence, error handling, container startup, and maintainable API boundaries. The ASU-CCS and existing MIS assumptions behind the user domain are documented separately in [Institutional Context](institutional-context.md).

This document is my development reflection. Each section records what went wrong or became unsafe, the effect it had, the reasoning behind the fix, and how I verified the result. I kept the narrative in first person because the important point is not only that the code changed; it is that I was able to trace symptoms to causes, choose a boundary-appropriate fix, and validate the behavior.

## 1. The application could not start because the database schema and entity model disagreed

### What went wrong

The first serious failure appeared during Docker startup. PostgreSQL was reachable, but the Spring Boot application repeatedly restarted. Hibernate reported that the `books.id` column had PostgreSQL's `SERIAL`/`INTEGER` type while the Java entity used `Long`, which Hibernate validates as `BIGINT`.

At the same time, schema ownership was unclear. PostgreSQL initialization scripts, Flyway, and Hibernate were each involved in the startup story. The application had a migration file, but the database could already contain tables created outside Flyway, so there was no reliable migration history for the schema Hibernate was validating.

### Effect

The failure looked like an application or API problem because Swagger assets could still be reachable and the container was restarting. In reality, requests could not be served because the application context never completed startup. The mismatch also meant that changing `ddl-auto` to `update` would only hide the underlying contract problem and would weaken the intended migration strategy.

### How I solved it

I traced the failure through the entity, migration, Maven dependencies, Compose file, and startup logs instead of treating the error as a generic Docker failure. I then made Flyway the single schema authority:

1. I corrected the initial books migration to use `BIGSERIAL`, matching Java `Long` and the database type expected by Hibernate.
2. I used Spring Boot's `spring-boot-starter-flyway` together with the PostgreSQL Flyway module so Boot 4 activates migration auto-configuration correctly.
3. I followed Flyway's `V<version>__<description>.sql` naming convention.
4. I removed the PostgreSQL init-script competition and explicitly configured `classpath:db/migration`.
5. I kept `spring.jpa.hibernate.ddl-auto=validate`, so entity/schema drift fails early and visibly instead of being silently changed by Hibernate.
6. Because this was still an empty pre-release development database, I recreated the disposable volume. I documented that this reset is not safe for a database containing real data.

### Verification

On a clean development database, Flyway records V1 and V2, Hibernate initializes successfully, and the application starts. The verification checks the container state, the health endpoint, the Flyway history, and the `books.id` database type. For an existing environment with data, the documented approach is a new forward migration rather than editing an applied migration or deleting the volume.

## 2. PostgreSQL readiness and volume behavior were not explicit enough for Compose

### What went wrong

The application container could start before PostgreSQL was ready to accept connections. Separately, the PostgreSQL 18 image's declared volume root differed from the older path used in the Compose configuration. That made it possible for the named volume to be mounted somewhere other than the version-specific data directory that PostgreSQL actually used.

### Effect

Startup became timing-sensitive and difficult to reproduce. A developer could see the database container running while the application still failed to connect or validated an unexpected database state. This made the Flyway incident harder to diagnose because infrastructure readiness and schema correctness were mixed together.

### How I solved it

I added a `pg_isready` health check to the database service and made the application depend on the database reaching the `healthy` state. I also mounted the named volume at PostgreSQL 18's `/var/lib/postgresql` volume root. The Compose file now expresses the dependency that previously existed only as an assumption.

### Verification

`docker compose ps` shows the database becoming healthy before the app starts. The application logs then show Flyway migration and JPA initialization in the expected order. This made startup deterministic enough to document and repeat.

## 3. Validation rules were split between the HTTP boundary and the service boundary

### What went wrong

The controller validated complete create and replace requests with `@Valid`, but a caller could reach the service without passing through the controller. That meant entity constraints were not automatically enforced for every service caller. PATCH also needed a different rule: omitted fields are represented by `null`, so applying full-request validation to a partial update would reject legitimate input.

### Effect

The API could appear protected while internal callers had a weaker validation path. Error behavior could also diverge depending on whether a bad value was rejected by DTO validation or by a manual service check.

### How I solved it

I injected Jakarta `Validator` into `BookService` and validate the resulting entity before create, PATCH, and PUT reach persistence. That makes the entity invariant effective for controller calls and direct service callers.

For PATCH, I deliberately kept `@Valid` off the request body and applied conditional field rules in the service. For PUT, I kept `@Valid` on the complete DTO and added service-level defense-in-depth checks. I also introduced `BookValidationException` for book write-rule failures so those failures remain distinct from unrelated `IllegalArgumentException` cases.

### Verification

Unit tests verify that invalid entities are rejected before repository persistence and that constraint violations become the public HTTP 400 validation shape through `GlobalExceptionHandler`. The behavior is documented as a deliberate boundary decision rather than an accidental difference between endpoints.

## 4. PUT and PATCH initially risked sharing the wrong semantics

### What went wrong

PUT and PATCH reuse `BookRequestDTO`, but they do not mean the same thing. If I applied full validation to PATCH, a request such as `{"price": 15.99}` would fail because omitted title, author, and genre values arrive as `null`. If I used PATCH's skip logic for PUT, a `null` field could be silently ignored and an old value would remain during what should have been a complete replacement.

### Effect

The first approach would make partial updates unusable. The second would make replacement requests misleading and could preserve stale data while telling the caller that the replacement succeeded.

### How I solved it

I made the distinction explicit:

- PUT uses `@Valid` and maps every mutable field onto the managed entity.
- PATCH has no `@Valid` annotation and updates only supplied fields.
- PATCH treats `null`, empty, and whitespace-only text as omitted values through a `hasText()` guard.
- PATCH validates a supplied price against zero or negativity.
- Both methods validate the resulting entity before the transaction can persist invalid state.

Both methods rely on Hibernate dirty checking and intentionally do not call `repository.save(existingBook)` after loading the managed entity. This keeps the transaction and persistence-context behavior clear rather than adding an unnecessary save call.

### Verification

The service tests cover partial price updates, omitted fields, blank text, complete replacement, invalid replacement payloads, entity validation, and the expectation that managed updates are flushed through dirty checking.

## 5. Validation errors could drift into different response contracts

### What went wrong

POST and PUT validation errors came through `handleValidationFailures`, while a PATCH price rule originally came through a general illegal-argument handler. The responses happened to look alike, but they were produced by separate paths.

### Effect

A later message or response-shape change in one handler could have broken clients only for one kind of write operation. The API contract depended on two implementations staying accidentally synchronized.

### How I solved it

I centralized construction of the shared validation error response and made the domain-specific book validation handler use that same builder. The exception type communicates the business context internally while the public HTTP 400 shape remains stable.

### Verification

`GlobalExceptionHandlerTest` checks the handler behavior, status codes, public error fields, validation-message aggregation, and the rule that parser, database, and fallback details are not leaked to clients.

## 6. Aggregate queries depended on unsafe positional results

### What went wrong

The count-and-total-value query and the genre distribution query previously returned `Object[]` values. The service then depended on positional indexes and runtime casts to understand what each value meant.

### Effect

The code was harder to read and easier to break during query changes. A changed select order could produce incorrect statistics without a compile-time signal. The public endpoint contract also had to be understood through implementation details rather than named fields.

### How I solved it

I introduced typed immutable constructor projections: `LibraryAggregate` for total count and total value, and `GenreCount` for genre/count pairs. The service maps those internal persistence projections into the existing public `LibraryStatisticsDTO` and `Map<String, Long>` response shapes. This improved the repository contract without making an unrelated API-breaking change.

### Verification

The service tests verify named projection mapping, most-expensive-book behavior, total statistics, and genre-distribution conversion. I also documented that repository/JPA integration coverage is still separate work; Mockito tests prove service mapping, not database query execution.

## 7. User identity rules needed to align with the existing university infrastructure

### What went wrong

The user domain could not simply be modeled as a generic email-and-password account because its institutional identity and academic relationships are defined by the project context. The specific ASU-CCS and MIS assumptions are recorded in [Institutional Context](institutional-context.md).

### Effect

Without explicit rules, the prototype could accept invalid identity values, store duplicate IDs or emails, or attach an IT major to the wrong course. Storing a raw password would also create an unnecessary credential risk.

### How I solved it

I modeled the institutional identity value as the unique public lookup value and applied the documented format rules. Creation normalizes identity values before duplicate checks and storage. The service rejects duplicate identity values, validates role/course/major relationships, bounds password input before BCrypt processing, encodes passwords, and never maps password data into `UserResponseDTO`.

I then added a transactional partial-update path for user profile fields. It preserves omitted values, trims supplied values, normalizes email case, validates blank and malformed input, and checks email uniqueness only when the email changes. It uses a managed entity so dirty checking persists the update consistently with the book feature.

### Verification

`UserServiceTest` covers update normalization, validation, unchanged-email behavior, duplicate-email rejection, and dirty-checking expectations. The academic mapping and password creation rules are implemented, while the controller and real authentication integration remain intentionally outside this prototype milestone.

## 8. The test feedback loop was too easy to misunderstand

### What went wrong

The project had important service and mapping behavior, but the test suite could become slow or noisy if every test rebuilt validators and mocks or printed expected exception paths. It was also easy to overstate what unit tests proved.

### Effect

Slower feedback discourages frequent verification. Noisy logs hide meaningful failures. More importantly, a green unit suite could be mistaken for proof that controller routing, JSON serialization, Flyway, PostgreSQL, and real JPA dirty checking had all been tested.

### How I solved it

I kept the suite focused and explicit: shared the stateless Jakarta Validator where safe, reused service fixtures, ran test classes concurrently while keeping methods within a class ordered, used real exception objects where that improved confidence, and disabled expected application log noise in tests. I also documented the boundary of the test evidence instead of calling it integration coverage.

### Verification

The current unit suite contains 87 passing tests: 50 for books, 14 for users, 5 for the book entity, 4 for mapping, and 14 for global exception handling. `mvn test` is the normal fast check; `mvn clean verify` additionally produces the JaCoCo report. Controller, JPA, Flyway, PostgreSQL, security, and container behavior remain candidates for integration testing.

## What these problems taught me

The recurring pattern was that the visible failure was usually one layer away from the real cause. A restart loop was a schema contract problem. A validation failure was a difference between full replacement and partial update. A fragile aggregate was a repository type-contract problem. A user model was an integration-boundary problem, not only an entity-design problem.

My approach became consistent:

1. Reproduce the symptom and identify which layer owns the failing contract.
2. Read the entity, DTO, service, repository, migration, configuration, and tests together.
3. Fix the ownership boundary instead of adding a local workaround.
4. Preserve public behavior where it was already useful and intentional.
5. Add a test or operational check that would have caught the original failure.
6. Document the remaining limits honestly so a prototype is not presented as production-ready infrastructure.

That process is the most reusable result of this project. The code solves concrete problems, but the development record shows how I identified them, assessed their effect, and turned each one into a more reliable system.
