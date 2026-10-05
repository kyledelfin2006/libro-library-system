# Libro User and Loan Development TODO

This is the public development roadmap for the user and loan domains. It
describes implementation status and planned work; it must not contain secrets,
credentials, or real user data.

## Current accomplishments

- [x] Added the `User` JPA entity under `app.user.entity`.
- [x] Added `UserRole` values: `STUDENT` and `FACULTY`.
- [x] Added `UserCourse` values: `IT`, `EMC`, and `IS`.
- [x] Added IT-major values: `SE`, `SMBPO`, `IST`, and `HN`.
- [x] Added university ID and password-hash fields.
- [x] Added `lastName`, `firstName`, `middleInitial`, and `email` fields.
- [x] Added basic name and email validation annotations.
- [x] Added the `createdAt` field and `@PrePersist` timestamp callback.
- [x] Added Flyway V2 for the `users` table.
- [x] Added database uniqueness, format, role, course, and major constraints.
- [x] Added indexes for common user filters.
- [x] Added `UserRepository` lookup, existence, role, and course methods.
- [x] Added Spring Security HTTP Basic authentication using the university ID and stored BCrypt password hash; signup is public and other routes require authentication.
- [x] Added the BCrypt `PasswordEncoder` bean.
- [x] Added `UserMapper` for request/entity and entity/response conversion.
- [x] Added `UserService` create, paginated-read, lookup, and delete operations.
- [x] Added Jakarta `Validator` request validation to user creation.
- [x] Added the initial `UserCreateUpdateDTO` and `ChangePasswordDTO` types.
- [x] Add `UserCreateRequestDTO`.
- [x] Add `UserResponseDTO`; never include the raw password or `passwordHash`.
- [x] Add the initial update DTO (`UserCreateUpdateDTO`).
- [x] Add the `UserService` foundation for create, read, and delete operations.
- [x] Complete the update operation: expose a transactional service contract, validate and normalize partial updates, and fix duplicate email handling.
- [x] Validate university ID format and normalize input deliberately.
- [x] Validate duplicate university IDs and emails in the service.
- [x] Enforce academic rules in the service:
- [x] Faculty must have `course = null` and `major = null`.
- [x] EMC and IS students must have `major = null`.
- [x] IT students must have an approved major.
- [x] Add a `PasswordEncoder` bean.
- [x] Enforce passwords of at least 8 characters with uppercase and lowercase letters, a number, and a symbol;

## User domain

- [x] Add `UserAPI` under `/app/users` for public signup, paginated list,
  lookup, profile PATCH/PUT, password change, and account deletion. All routes
  except signup require HTTP Basic authentication.
- [x] Add PostgreSQL-backed user persistence tests for normalized creation,
  stored password hashes, committed updates, and database constraints.
- [x] Add MVC-slice tests for all user routes, request binding and validation,
  pagination, response shapes, not-found and conflict responses, and public
  response privacy.
- [x] Add focused service tests for partial updates, normalization, validation,
  unchanged emails, and duplicate-email rejection.
- [x] Add getters/setters and a no-argument constructor to `ChangePasswordDTO`
  for validation and future controller binding.
- [x] Add a transactional `UserService.updatePassword` operation that validates
  the DTO, verifies the current password, and stores only the encoded hash.
- [x] Add password-change tests for valid changes, incorrect current passwords,
  invalid formats, missing users, and encoded-password storage.

## Loan domain

- [ ] Create `app.loan` using feature-oriented packages.
- [ ] Add the `Loan` entity with `User` and `Book` relationships.
- [ ] Add `borrowedAt`, `dueAt`, and nullable `returnedAt` fields.
- [ ] Add `LoanRepository`.
- [ ] Add an active-loan lookup using `returnedAt IS NULL`.
- [ ] Add a database rule allowing only one active loan per book.
- [ ] Add `LoanService`.
- [ ] In `LoanService.borrowBook`, load the user and book, check availability,
  assign both with `loan.setUser(user)` and `loan.setBook(book)`, set dates,
  and save the loan in one transaction.
- [ ] Add return-book logic by setting `returnedAt`.
- [ ] Add loan history and overdue queries.
- [ ] Add loan DTOs and a thin `LoanController`.
- [ ] Add loan service and controller tests.

## Spring Security and authentication

- [x] Add `LibroUserDetailsService` to load users by university ID, map roles to
  Spring authorities, and verify credentials with the BCrypt `PasswordEncoder`.
- [x] Require authentication except for `POST /app/users/signup`; retain CSRF
  protection for unsafe methods.
- [x] Add MVC security-flow tests for anonymous signup, anonymous rejection,
  successful Basic authentication, and incorrect-password rejection. The tests
  use the real `LibroUserDetailsService` and a mocked `UserRepository`.
- [ ] Decide whether local HTTP Basic accounts are sufficient or institutional
  SSO is required before deployment to untrusted clients.
- [ ] Define role-specific permissions for `STUDENT` and `FACULTY`; currently
  every authenticated user can access every protected route.
- [ ] Review CSRF behavior for intended API clients and document the HTTP Basic
  scheme, public signup exception, and CSRF requirements in OpenAPI.
- [ ] Remove client control over role assignment during public signup before
  deployment.
- [ ] For future loan routes, derive the borrower from the authenticated
  principal rather than a client-supplied user ID.

## Verification and documentation

- [x] Run `mvn test` after user-domain tests are added.
- [x] Add an opt-in PostgreSQL 18 Testcontainers integration suite for Flyway,
  JPA schema validation, book queries/projections, user persistence, service dirty
  checking, and database constraints (`mvn -Pintegration verify`).
- [x] Move generated OpenAPI route/schema assertions into the Docker-free MVC
  suite so `mvn test` checks representative operations and password privacy.
- [x] Add Docker-free MVC tests for the current HTTP Basic security flow.
- [ ] Run `mvn -Pintegration verify` with Docker available and confirm the
  PostgreSQL tests pass; the current environment has no Docker daemon.
- [ ] Verify that V2 has not already been applied to a persistent database; if
  it has, create a forward-only V3 migration instead of editing V2.
- [x] Apply [the API Documentation Guideline](../docs/api-documentation-guideline.md)
  to book and user operation descriptions, request/response DTO schemas, and
  expected status/error responses.
- [ ] Manually review Swagger UI against the guideline checklist with the
  application running. The automated generated-spec assertions pass in
  `OpenApiMvcTest`; a browser review is still pending.
- [x] Update `README.md` with the live user API routes.
- [ ] Document loan routes in `README.md` when those routes are implemented.
- [ ] Review entity, migration, DTO, and database naming for consistency.

## Recommended next slice

Run the Docker-backed PostgreSQL integration checks and inspect Swagger UI
  against the project guideline. Before an untrusted deployment, decide whether
local authentication is acceptable, implement role-specific access and safe
role assignment, and settle CSRF/OpenAPI documentation. Loan routes remain
future work and should use the authenticated principal as the borrower.
Controllers should translate HTTP requests and responses, not contain
business logic.
