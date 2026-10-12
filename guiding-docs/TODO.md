# Internal Engineering Roadmap

This is the working implementation roadmap. It is not the public project overview or an API contract. The role/JWT design is specified in [Security Roles and JWT Guide](security-roles-and-jwt-guide.md); this checklist tracks implementation status.

## Current state

| Area | Implemented now |
| --- | --- |
| Books | CRUD, search, pagination, sorting, filtering, statistics, DTO validation |
| Accounts | Student signup, librarian-created faculty, student directory, owner/staff profile and password management, restricted deletion |
| Roles in Java and the current schema | STUDENT, FACULTY, ADMINISTRATOR, LIBRARIAN (Flyway V3) |
| Authentication | HTTP Basic using university ID, UserDetailsService, and BCrypt |
| Authorization | Book reads for all roles; ADMINISTRATOR creates books; ADMINISTRATOR/LIBRARIAN update or delete books; librarian faculty creation; service-level account ownership and target-role checks |
| Loans | Not implemented |
| JWT login and Bearer validation | Not implemented |
| LIBRARIAN role and target owner/directory rules | Implemented with HTTP Basic |

Account profile and password operations enforce owner access, with administrator/librarian access to student and faculty accounts. Staff cannot manage another administrator or librarian account. Profile reads and the paginated directory expose students to staff only.

## Recommended implementation order

### 1. Role and account boundaries (implemented)

- [x] Add LIBRARIAN to the Java role enum and database constraint using Flyway V3.
- [x] Keep public signup pinned to STUDENT.
- [x] Restrict faculty creation to LIBRARIAN and student/faculty deletion to ADMINISTRATOR and LIBRARIAN.
- [x] Restrict the student directory and cross-account student profile reads to ADMINISTRATOR and LIBRARIAN.
- [x] Restrict profile and password changes to the owner, except staff may manage student/faculty accounts.
- [x] Implement book permissions: ADMINISTRATOR can add, update, and delete books; LIBRARIAN can update and delete books.
- [ ] Add authorization tests for allowed and denied requests, including cross-account profile/password attempts.

### 2. Add loans

- [ ] Decide the loan period before implementing due dates.
- [ ] Add the Loan entity, service, controller, DTOs, and persistence migration.
- [ ] Identify borrowers only from the authenticated principal.
- [ ] Enforce at most one active loan for each physical Book copy in application logic and PostgreSQL.
- [ ] Allow a borrower to return only their own active loan.
- [ ] Preserve loan history and block deletion of an account referenced by loans.
- [ ] Add service, MVC/security, and PostgreSQL concurrency/invariant coverage.

### 3. Replace HTTP Basic with local JWT authentication

- [ ] Add public POST /app/auth/login using Spring Security authentication, UserDetailsService, and BCrypt.
- [ ] Issue short-lived signed access tokens and validate Bearer tokens with Spring Security OAuth2 Resource Server/JWT support.
- [ ] Configure the signing secret outside source control; use stateless request security and the documented header-only token transport.
- [ ] Mark signup and login public in the filter chain and OpenAPI. Remove HTTP Basic after the Bearer flow is verified.
- [ ] Test absent, malformed, expired, wrong-signature, and wrong-issuer tokens, plus valid-token role denials.

### 4. Keep the public and internal docs aligned

- [ ] Update README status, endpoint summary, and architecture diagrams after behavior lands.
- [ ] Update AGENTS.md and Current System Engineering Reference with the new current facts and contributor rules.
- [ ] Update OpenAPI and the API documentation guide only where their contracts or conventions change.
- [ ] Review the generated OpenAPI document and Swagger UI against runtime behavior.

## Database migration rule

Before changing a migration, establish whether that version has been applied to a persistent database. If it has, add the next forward-only migration. Keep Flyway SQL, JPA mappings, and integration tests aligned. Do not destroy a database volume to resolve migration drift.

## Verification queue

- [ ] Run the PostgreSQL-backed integration profile when Docker is available.
- [ ] Perform the generated OpenAPI/Swagger UI review with the application running.
- [ ] Review each security change against the allowed and denied cases in the target specification.

## Source documents

- [Security Roles and JWT Guide](security-roles-and-jwt-guide.md): target behavior and acceptance checks.
- [Current System Engineering Reference](engineering-reference.md): current repository facts and technical conventions.
- [API Documentation Guideline](../docs/api-documentation-guideline.md): OpenAPI/controller documentation rules.
- [Domain Decisions](../docs/domain-decisions.md): physical-copy model and future loan invariant.
