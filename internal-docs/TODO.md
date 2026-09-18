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

## User domain

- [x] Add `UserCreateRequestDTO`.
- [x] Add `UserResponseDTO`; never include the raw password or `passwordHash`.
- [x] Add the initial update DTO (`UserCreateUpdateDTO`).
- [x] Add the `UserService` foundation for create, read, and delete operations.
- [ ] Complete the update operation: make the intended public service contract
  explicit, add `@Transactional`, validate the update DTO, and fix duplicate
  email handling.
- [x] Validate university ID format and normalize input deliberately.
- [x] Validate duplicate university IDs and emails in the service.
- [x] Enforce academic rules in the service:
  - [x] Faculty must have `course = null` and `major = null`.
  - [x] EMC and IS students must have `major = null`.
  - [x] IT students must have an approved major.
- [x] Add a `PasswordEncoder` bean.
- [x] Hash the supplied four-digit prototype PIN before persistence; never store it directly.
- [ ] Add `UserController` after the service contract is stable.
- [ ] Add repository, service, validation, and controller tests.
- [ ] Add getters/setters or a record implementation to `ChangePasswordDTO`
  before using it in a controller or service.

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

- [ ] Replace the prototype four-digit PIN approach with the institution's
  approved authentication or SSO solution before production use.
- [ ] Learn `UserDetailsService`, `PasswordEncoder`, authentication, and
  authorization fundamentals.
- [ ] Implement `UserDetailsService` using `universityId` as the username.
- [ ] Decide between school SSO, sessions, or token-based authentication.
- [ ] Add login and authentication tests.
- [ ] Replace any temporary client-supplied `userId` in loan requests with the
  authenticated user from the security context.
- [ ] Replace `permitAll()` with endpoint-specific authorization rules.
- [ ] Keep CSRF and credential handling appropriate for the selected auth model.

## Verification and documentation

- [ ] Run `mvn test` after user-domain tests are added.
- [ ] Add integration coverage for Flyway, JPA mappings, and PostgreSQL checks.
- [ ] Verify that V2 has not already been applied to a persistent database; if
  it has, create a forward-only V3 migration instead of editing V2.
- [ ] Update `README.md` to document the users and loans API.
- [ ] Review entity, migration, DTO, and database naming for consistency.
- [ ] Commit focused changes and push with `git push` from `user-service`.
- [ ] Open a pull request from `user-service` into `main`.

## Recommended next slice

Complete and test the user update/password-change contracts, then add the
`UserController`. After the user API is stable, implement the loan entity and
service. Controllers should translate HTTP requests and responses, not contain
business logic.
