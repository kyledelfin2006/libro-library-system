# Implementation Plan: Remaining Quality Improvements

This plan tracks the quality improvements identified for Libro, excluding Spring Security. Problem 1 has been implemented, with its PostgreSQL integration test pending execution in an environment with Docker; Problems 2 and 3 remain. Keep the current feature-oriented, layered architecture: controllers own HTTP binding, services own business rules, repositories own persistence, DTOs own public payloads, and shared API documentation stays concise.

## 1. Provide a paginated path for book searches and filters — implemented; PostgreSQL check pending

### Problem

`/search`, `/budget`, `/sorted`, and `/price` return complete `List<BookResponseDTO>` results. Their payload and query work grow with the number of matching records. `/all` is paginated, but filtering endpoints do not provide a paginated equivalent.

### Implementation

`GET /app/books/query` accepts optional title, author, genre, minimum price, and maximum price filters. Filters combine with AND; text uses case-insensitive literal substring matching; price bounds are inclusive and can be used separately. The endpoint returns `Page<BookResponseDTO>` using the existing zero-based defaults, size cap, and sort allowlist. `BookService` validates ranges and sorting, while `BookRepository` pages and counts matching rows in PostgreSQL. Existing list routes retain their response shapes. Do not deprecate or remove them without a separate versioned contract change.

### Architecture

The controller binds query parameters and delegates. The service validates filter combinations and sorting. Repositories handle database paging. Responses continue to use `BookResponseDTO`; entities remain internal.

### Done when

- A client can combine supported filters with page, size, and sort parameters and receive page metadata.
- Page size is bounded and unapproved sort properties are rejected before repository access.
- Existing routes continue returning their existing shapes until an explicit compatibility decision is made.
- MVC and PostgreSQL integration tests cover filter binding, page boundaries, ordering, and result totals.

## 2. Remove stale test-count documentation

### Problem

The README and development reflection contain volatile test totals, while `AGENTS.md` lists per-class totals. Repeated totals are easy to stale and can undermine portfolio claims.

### Solution

Treat Maven's test report as the source of truth. Document test scope and commands in prose, but avoid hard-coded suite totals unless they are generated or refreshed as part of the same change.

### Implementation

1. Search README, `AGENTS.md`, `docs/`, and `internal-docs/` for exact test totals and per-class counts.
2. Remove volatile totals from the README and contributor guide; keep the test-class responsibilities and known coverage limits.
3. In the development reflection, either refresh its counts and date the verification or describe the verification without implying a current total.
4. Keep the TODO focused on pending work. Mark integration coverage and generated-spec inspection complete only after running those checks.
5. Before a portfolio release, run `mvn clean verify` and report the count from that run rather than reusing a stored number.

### Architecture

No production-code change. README remains the portfolio entry point; `AGENTS.md` remains the contributor guide; the development reflection remains a historical account; TODO remains the working roadmap.

### Done when

- No document presents an outdated test count as current.
- Each document has one clear audience and purpose.
- Verification claims distinguish unit/MVC checks from JPA, Flyway, PostgreSQL, and live Swagger checks.

## 3. Reduce repeated OpenAPI response annotation noise

### Problem

Both controllers repeat `@ApiResponses` blocks that declare the same `ErrorResponse` schema. Operation-specific explanations are valuable, but the repeated annotation scaffolding can make route methods harder to scan and invites drift.

### Solution

Keep concise endpoint-specific operation descriptions and response meanings, and extract only response declarations that are truly identical. Do not create a general OpenAPI framework or move endpoint behavior out of the controller.

### Implementation

1. Inventory repeated response declarations and separate shared schema/content metadata from endpoint-specific descriptions and status behavior.
2. Prototype the smallest Springdoc-supported reuse mechanism for identical responses, such as a narrowly scoped composed annotation or named shared response component.
3. Keep the shared type in the existing global OpenAPI/configuration area only if it is reused enough to reduce total code and improve readability.
4. Compare generated `/v3/api-docs` before and after; confirm that status codes, descriptions, and the `ErrorResponse` schema remain equivalent.
5. If abstraction adds more indirection than it removes, retain the local annotations and instead shorten redundant descriptions.

### Architecture

Controllers remain the source of operation-specific HTTP documentation. Shared metadata is cross-cutting and belongs under `app.global` only when genuinely shared. DTO constraints and service rules remain in their current layers.

### Done when

- Endpoint methods are easier to scan without losing expected status/error details.
- Generated OpenAPI output is unchanged in meaning and still matches runtime behavior.
- No generic annotation registry, customizer framework, or duplicate YAML contract is introduced.

## Suggested next steps

1. Refresh stale test-count documentation.
2. Revisit response-annotation reuse after inspecting the resulting OpenAPI and controller readability.
