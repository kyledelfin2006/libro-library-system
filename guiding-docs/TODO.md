# Internal Engineering Roadmap

This is the working implementation roadmap. It is not the public project overview or an API contract. The role/JWT design is specified in [Security Roles and JWT Guide](security-roles-and-jwt-guide.md); this checklist tracks implementation status.

## Current state

| Area | Implemented now |
| --- | --- |
| Books | CRUD, search, pagination, sorting, filtering, statistics, DTO validation |
| Accounts | Student signup, administrator-created faculty, profiles, password change, listing, lookup, deletion |
| Roles in Java and the current schema | STUDENT, FACULTY, ADMINISTRATOR |
| Authentication | HTTP Basic using university ID, UserDetailsService, and BCrypt |
| Authorization | Book reads for all current roles; book writes for FACULTY and ADMINISTRATOR; faculty creation and account deletion for ADMINISTRATOR; other protected routes require authentication |
| Loans | Not implemented |
| JWT login and Bearer validation | Not implemented |
| LIBRARIAN role and target owner/directory rules | Specified, not implemented |

Current account routes do not yet enforce the target owner and administrator/librarian account-management boundaries or student-directory restrictions. Keep this distinction visible in documentation and tests until implementation changes.

## Recommended implementation order

### 1. Implement role and account boundaries

- [ ] Add LIBRARIAN to the Java role enum and database constraint, using the migration rule below.
- [ ] Keep public signup pinned to STUDENT.
- [ ] Restrict faculty account creation and student/faculty deletion to LIBRARIAN.
- [ ] Restrict the student directory and student profile access to ADMINISTRATOR and LIBRARIAN.
- [ ] Restrict profile and password changes to the account owner.
- [ ] Implement the target book permissions: ADMINISTRATOR can add, update, and delete books; LIBRARIAN can update and delete books but cannot add them.
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
