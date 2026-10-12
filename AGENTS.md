# Agent and Contributor Guide

This file is the repository's internal entry point for AI agents and human contributors. Read it before changing code or documentation. The README is the recruiter-facing project overview; it is not the authority for implementation details.

## Start here

1. Read this file for contributor rules and the source-of-truth map.
2. Read [Current System Engineering Reference](guiding-docs/engineering-reference.md) for the repository map, current layer contracts, API inventory, configuration, and test strategy.
3. Read the relevant focused document before editing:
   - [Role and JWT target specification](guiding-docs/security-roles-and-jwt-guide.md) for the planned role policy, JWT design, and future loan rules.
   - [Development roadmap](guiding-docs/TODO.md) for outstanding work.
   - [API documentation guideline](docs/api-documentation-guideline.md) for controller/OpenAPI documentation.
   - [Domain decisions](docs/domain-decisions.md) for the physical-copy model and loan constraints.

## Sources of truth

Keep facts in the artifact that owns them. If prose disagrees with running code, migrations, or tests, investigate and correct the stale prose instead of treating it as implemented behavior.

| Question | Source of truth |
| --- | --- |
| What an endpoint currently does | Controller mappings, DTOs, service behavior, generated OpenAPI, and API tests |
| What access is currently enforced | SecurityConfig, any method/service authorization checks, and security tests |
| What roles and columns the database accepts | Latest applied Flyway migrations; JPA mappings must match |
| Current entity and relationship model | JPA entities plus [Domain Decisions](docs/domain-decisions.md) |
| API documentation conventions | [API Documentation Guideline](docs/api-documentation-guideline.md) |
| Planned role policy, JWT design, and loan behavior | [Role and JWT target specification](guiding-docs/security-roles-and-jwt-guide.md); it is a target, not live implementation |
| Remaining work | [Development roadmap](guiding-docs/TODO.md) |
| Recruiter-facing project description | [README](README.md) and the public-facing documents it links to |

## Current implementation facts

- The project is a single-module Java 25 / Spring Boot 4.1 REST API using Spring MVC, Spring Data JPA, Hibernate, PostgreSQL, Flyway, and Maven.
- The live authentication mechanism is HTTP Basic, backed by LibroUserDetailsService and BCrypt. Signup at POST /app/users/signup is public. JWT login and Bearer validation are not implemented.
- The live UserRole values are STUDENT, FACULTY, ADMINISTRATOR, and LIBRARIAN.
- STUDENT means a student patron; FACULTY means school faculty using the library as patrons; ADMINISTRATOR means a library assistant (such as fellow faculty, student assistants, or volunteers), not a system administrator; LIBRARIAN means the library's librarian.
- Book reads allow all four roles. Book creation, update, and deletion allow ADMINISTRATOR and LIBRARIAN.
- Librarians create faculty and administrator/library-assistant accounts. Administrators can list/read/manage student and faculty accounts and delete those accounts; librarians may also manage/delete administrator accounts. Students and faculty can delete their own accounts. Staff cannot manage another librarian account; administrator and librarian accounts cannot delete themselves.
- User account ownership and target-role rules are enforced in UserService as well as by route authorization. HTTP Basic and CSRF remain enabled.
- No librarian account is seeded; librarian-only routes require an existing librarian account. The historical V2 administrator bootstrap remains in place.
- Loans and loan history are not implemented. Each Book row represents one physical copy; documented loan permissions remain future policy.
- Flyway owns schema changes; Hibernate uses ddl-auto=validate.

These are a snapshot, not a substitute for checking the code when a task changes the relevant area.

## Do

- Keep HTTP handling in controllers, business rules and transactions in services, persistence behind repositories, and entity/DTO mapping explicit.
- Use request and response DTOs at API boundaries. Never accept a JPA entity from a client or return one directly.
- Keep secrets and real personal data out of source, migrations, docs, tests, logs, and examples. The historical administrator bootstrap migration accepts only a BCrypt hash through LIBRO_ADMIN_PASSWORD_HASH. No librarian account is seeded.
- Add forward-only Flyway migrations for schema changes once a migration may have been applied to a persistent database. Keep SQL and JPA mappings aligned.
- Preserve the physical-book-copy domain decision and enforce loan ownership from the authenticated principal when loan work begins.
- Update the README, this guide, the engineering reference, OpenAPI, and the roadmap only where their owned facts changed.
- Add focused tests for behavior changes at the appropriate service, MVC/security, or PostgreSQL persistence boundary.

## Don't

- Don't implement a future item merely because it appears in the JWT/loan specification; follow the user's requested scope.
- Don't claim JWT, Bearer authentication, or loan routes are live until code and tests enforce them.
- Don't let public signup choose a privileged role.
- Don't add repository access or domain workflows to controllers.
- Don't pass arbitrary client sort properties through to persistence.
- Don't edit an already-applied migration or destroy a database volume to make a migration pass.
- Don't expose password hashes, stack traces, SQL, database internals, or credentials in API responses.

## Security and migration notes

- Current security and the target policy differ. Check SecurityConfig, controllers/services, and tests before changing access behavior; use the target specification as the intended design only when implementing that scope.
- The current filter chain keeps CSRF enabled. Do not change CSRF or credential transport as incidental cleanup.
- Flyway is the only schema authority. For each schema change, check the fresh-database and previous-version migration paths, then start with Hibernate schema validation enabled.
- Never run docker compose down -v unless the user explicitly requests destruction of that database data.

## Contribution workflow

1. Trace the existing request path and read the relevant source-of-truth docs.
2. Make the smallest cohesive change at the correct layer.
3. Update contract documentation and tests for behavior that changed.
4. Run the appropriate Maven checks described in [Current System Engineering Reference](guiding-docs/engineering-reference.md), and report exactly which checks ran.

## Definition of done

- The requested behavior is implemented at its owning layer.
- Tests cover the changed contract and relevant failure/security cases.
- OpenAPI and recruiter-facing docs do not claim behavior that is absent from code.
- Flyway, JPA mappings, and persistence tests agree for schema changes.
- No secret or sensitive user data is included in the diff.
