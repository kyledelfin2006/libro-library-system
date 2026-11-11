# Backend Improvements Implementation Plan

> **Lifecycle:** when the steps are complete, replace this plan with as-built documentation or delete it and remove its links from the plans index and Roadmap.

Status: in progress; the typed projection code change is applied, and unit/PostgreSQL integration verification remains pending.

## Preferred improvement and reasoning

Replace the positional `Object[]` results returned by `LoanRepository.weeklyActivity` and `LoanRepository.topTitles` with typed Spring Data projections.

Both endpoints already provide the UI data they need. The current repository methods return untyped columns, and `LoanService` relies on array indexes and casts. The weekly activity path also accepts two possible date representations. These mappings are harder to review and can fail at runtime if a query column or driver type changes.

Typed projections give the service a named, typed view of each query row without changing endpoint behavior. This is preferred over adding a new endpoint, table, index, cache, or distributed rate limiter: no product gap or measured database bottleneck requires those changes, and the current deployment is single-instance. Gateway throttling remains the documented requirement before public multi-instance exposure.

## Implementation design

- Add `WeeklyLoanActivityProjection` and `TopTitleProjection` interfaces under `app.loan.repository.projection`.
- Alias the native query columns to match projection accessors and return typed projection lists from the two `LoanRepository` methods.
- Map projections to the existing `WeeklyLoanActivityDTO` and `TopTitleDTO` in `LoanService`. Keep the zero-filled weeks, staff scope, rolling date window, title ordering, and result limit unchanged.
- Leave the public DTOs, routes, OpenAPI response schemas, database schema, and migrations unchanged.

## Code changes

| File | Change |
| --- | --- |
| `src/main/java/app/loan/repository/projection/WeeklyLoanActivityProjection.java` | Add typed week date, checkout count, and return count accessors. |
| `src/main/java/app/loan/repository/projection/TopTitleProjection.java` | Add typed title, author, and checkout count accessors. |
| `src/main/java/app/loan/repository/LoanRepository.java` | Return typed projections and align SQL aliases with their accessors. |
| `src/main/java/app/loan/service/LoanService.java` | Replace array indexing, casts, and date fallback with projection accessors. |
| `src/test/java/app/loan/service/LoanAggregatesTest.java` | Supply typed projection fixtures and preserve zero-fill, date-boundary, and staff-scope assertions. |
| `src/test/java/app/loan/repository/LoanReportingIT.java` | Verify PostgreSQL maps native result aliases and scalar types to the projections; retain aggregation and role-scope assertions. |

No migration, configuration, dependency, or public API change is expected.

## Execution steps

1. [x] Add the two projection interfaces with accessor types matching PostgreSQL's `date` and aggregate `bigint` results.
2. [x] Update the weekly and top-title native queries with matching column aliases and typed return values.
3. [x] Update the service mappings and focused unit and PostgreSQL integration fixtures. Preserve the existing response DTOs and query semantics.
4. [ ] Run the focused loan aggregate tests and `./mvnw verify -Pintegration`; confirm generated OpenAPI response schemas are unchanged.

## Risks and completion checks

- PostgreSQL and Hibernate must expose the aliased date and numeric values using the declared projection types; the integration test is the acceptance check for this mapping.
- Role-scoped rows, Manila week boundaries, zero-filled weeks, title tie ordering, and the requested top-title limit remain unchanged.
- No `Object[]` result mapping remains in these two query paths, and no public contract or schema migration is introduced.
