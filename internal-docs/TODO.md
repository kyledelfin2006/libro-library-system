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
- [x] Added the Spring Security dependency and a development `permitAll` filter chain.
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

- [x] Add `UserAPI` under `/app/users` for create, paginated list, lookup,
  profile PATCH/PUT, password change, and account deletion. These routes
  currently use development `permitAll`; this does not satisfy production
  authentication or authorization requirements.
- [ ] Add repository and controller tests.
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

- [ ] Integrate the password policy with the institution's approved authentication
  or SSO solution before production use.
- [ ] Choose the authentication model (institutional SSO, sessions, or tokens)
  before deployment to untrusted clients. Decide whether local password
  authentication is supported; add `UserDetailsService` only if that model
  requires it.
- [ ] Add authentication and authorization tests for the selected model.
- [ ] Replace `permitAll()` with endpoint-specific authorization rules.
- [ ] Keep CSRF and credential handling appropriate for the selected auth model.
- [ ] For future loan routes, derive the borrower from the authenticated
  principal rather than a client-supplied user ID.

## Verification and documentation

- [x] Run `mvn test` after user-domain tests are added.
- [x] Add an opt-in PostgreSQL 18 Testcontainers integration suite for Flyway,
  JPA schema validation, repository queries/projections, service dirty checking,
  and a database constraint (`mvn -Pintegration verify`).
- [ ] Run `mvn -Pintegration verify` with Docker available and confirm the
  PostgreSQL tests pass; the current environment has no Docker daemon.
- [ ] Verify that V2 has not already been applied to a persistent database; if
  it has, create a forward-only V3 migration instead of editing V2.
- [x] Apply [the API Documentation Guideline](../docs/api-documentation-guideline.md)
  to book and user operation descriptions, request/response DTO schemas, and
  expected status/error responses.
- [ ] Inspect the generated `/v3/api-docs` and Swagger UI with the application
  and database running; verify parameter defaults, schemas, statuses, errors,
  and examples. `mvn test` passes (104 tests), but the live endpoint timed out
  and Docker Compose could not connect because the Docker Desktop engine is
  unavailable in this environment.
- [x] Update `README.md` with the live user API routes.
- [ ] Document loan routes in `README.md` when those routes are implemented.
- [ ] Review entity, migration, DTO, and database naming for consistency.

## Recommended next slice

Inspect the generated Swagger UI against the project guideline, then add user
controller/repository tests. Before any untrusted deployment, choose and
implement the authentication model and endpoint-specific authorization. Loan
routes remain future work and should use the authenticated principal as the
borrower. Controllers should translate HTTP requests and responses, not contain
business logic.
