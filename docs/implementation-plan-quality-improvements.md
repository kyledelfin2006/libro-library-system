# Implementation Plan: Remaining Quality Improvements

This plan tracks the four remaining improvement areas identified for Libro, excluding Spring Security. PostgreSQL integration coverage has been implemented; its Docker-backed execution remains to be run in an environment with Docker. Keep the current feature-oriented, layered architecture: controllers own HTTP binding, services own business rules, repositories own persistence, DTOs own public payloads, and shared API documentation stays concise.

## 1. Cover user HTTP contracts and generated OpenAPI

### Problem

`BookApiMvcTest` covers the book HTTP boundary, but there is no equivalent MVC test for `UserAPI`. The generated Swagger UI and `/v3/api-docs` have not yet been inspected in a running application environment.

### Solution

Test user routes at the MVC boundary with a mocked service, then verify the generated OpenAPI document against the live controller/DTO contract. Reuse the existing book MVC-slice approach instead of adding another API abstraction.

### Implementation

1. Add `UserApiMvcTest` under `src/test/java/unit/user` with `@WebMvcTest(UserAPI.class)`, mocked `UserService`, and the real `GlobalExceptionHandler`.
2. Cover create, paginated list, lookup, PATCH, PUT, password change, and delete: binding, status codes, response envelope versus direct DTO/Page shapes, validation errors, and 404/409 error bodies.
3. Add OpenAPI verification to the PostgreSQL-backed integration run. Check that both tags and all live routes appear, that PATCH schemas are optional, password fields are write-only, and response/error schemas and pagination descriptions match the implementation.
4. Perform one manual Swagger UI review against the guideline checklist after the app starts with the test database. Record only checks actually completed in the TODO and development docs.

### Architecture

The MVC tests stop at `UserAPI` and mock `UserService`, matching the existing book test boundary. The OpenAPI check inspects Springdoc output generated from controllers and DTOs; do not maintain a second hand-written route/schema catalog.

### Done when

- Each user route has at least one successful contract test and its expected invalid/not-found case where applicable.
- Error and success payloads match the existing API contract.
- The generated `/v3/api-docs` contains accurate book and user operations and schemas, and Swagger UI can render and invoke them in the documented local setup.

## 2. Provide a paginated path for book searches and filters

### Problem

`/search`, `/budget`, `/sorted`, and `/price` return complete `List<BookResponseDTO>` results. Their payload and query work grow with the number of matching records. `/all` is paginated, but filtering endpoints do not provide a paginated equivalent.

### Solution

Add a paginated query path for interactive clients while preserving existing list routes during a compatibility period. First choose one clear public query shape rather than adding a separate paginated route for every filter.

### Implementation

1. Document current list-route usage and decide whether a single `GET /app/books/query` endpoint with optional text and price filters covers the needed UI use cases.
2. Bind filters as typed controller parameters and `Pageable` with the existing zero-based defaults, maximum page size, and sort-field allowlist.
3. Put filter validation and combination rules in `BookService`; add repository methods/projections that return `Page<Book>` without loading the full result set.
4. Map page content through `BookMapper` and preserve page metadata in the response. Document filter semantics, sort fields, limits, and 400 responses in OpenAPI.
5. Keep existing list routes and their response shapes unchanged initially. Only deprecate or remove them in a versioned contract change after clients have a migration path; do not silently truncate their results.

### Architecture

The controller binds query parameters and delegates. The service validates filter combinations and sorting. Repositories handle database paging. Responses continue to use `BookResponseDTO`; entities remain internal.

### Done when

- A client can combine supported filters with page, size, and sort parameters and receive page metadata.
- Page size is bounded and unapproved sort properties are rejected before repository access.
- Existing routes continue returning their existing shapes until an explicit compatibility decision is made.
- MVC and PostgreSQL integration tests cover filter binding, page boundaries, ordering, and result totals.

## 3. Remove stale test-count documentation

### Problem

The README and development reflection contain old test totals, while the latest verified suite ran 104 tests. `AGENTS.md` also lists older per-class totals. Repeated totals are easy to stale and can undermine portfolio claims.

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

## 4. Reduce repeated OpenAPI response annotation noise

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

## Suggested delivery order

1. Add user MVC tests and verify the generated OpenAPI document using the PostgreSQL integration profile.
2. Design and implement the paginated book query path as a backward-compatible addition.
3. Refresh stale test-count documentation.
4. Revisit response-annotation reuse after inspecting the resulting OpenAPI and controller readability.
