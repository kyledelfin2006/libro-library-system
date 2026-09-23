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

- [ ] Add `UserController` after the authentication model and endpoint access
  rules are understood and selected.
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
- [ ] Learn the existing `SecurityFilterChain` and the roles of
  `UserDetailsService`, `PasswordEncoder`, `Authentication`, and
  `SecurityContext` before building user endpoints.
- [ ] Implement `UserDetailsService` using `universityId` as the username.
- [ ] Choose an authentication model: institutional SSO, sessions, or
  token-based authentication. Confirm whether local password authentication
  and `UserDetailsService` are part of that model.
- [ ] Add login and authentication tests.
- [ ] Replace any temporary client-supplied `userId` in loan requests with the
  authenticated user from the security context.
- [ ] Replace `permitAll()` with endpoint-specific authorization rules.
- [ ] Keep CSRF and credential handling appropriate for the selected auth model.

## Verification and documentation

- [x] Run `mvn test` after user-domain tests are added.
- [ ] Add integration coverage for Flyway, JPA mappings, and PostgreSQL checks.
- [ ] Verify that V2 has not already been applied to a persistent database; if
  it has, create a forward-only V3 migration instead of editing V2.
- [ ] Update `README.md` to document the users and loans API.
- [ ] Review entity, migration, DTO, and database naming for consistency.
- [ ] Commit focused changes and push with `git push` from `user-service`.
- [ ] Open a pull request from `user-service` into `main`.

## Recommended next slice

Learn and decide the authentication model first, then implement and test
endpoint-specific security. Build `UserController` with those access rules and
add controller tests. After the user API is stable, implement the loan entity
and service so loan operations can use the authenticated identity. Controllers
should translate HTTP requests and responses, not contain business logic.
