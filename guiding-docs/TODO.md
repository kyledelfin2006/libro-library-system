# Internal Engineering Roadmap

This is the working implementation roadmap. It is not the public project overview or an API contract. Live role/JWT rules are in [Security Roles and JWT Guide](security-roles-and-jwt-guide.md) and the [JWT Authentication Design](jwt-authentication-design.md); this checklist tracks implementation status.

## Current state

| Area | Implemented now |
| --- | --- |
| Books | CRUD, optional description, replacement-cost tracking, search, pagination, sorting, filtering, statistics, DTO validation (V5) |
| Accounts | Student signup; librarian-created faculty/library assistants; role-scoped directory, profile/password management, and deletion |
| Roles in Java and the current schema | STUDENT, FACULTY, ADMINISTRATOR, LIBRARIAN (Flyway V3) |
| Authentication | Local HS256 JWT login and stateless Bearer validation; issuer/audience are configured, maximum token lifetime is 900 seconds, and clock skew is 30 seconds |
| Authorization | Book reads for all roles; ADMINISTRATOR/LIBRARIAN book CRUD; librarian creates faculty/library assistants; role-scoped service account checks |
| Loans | Implemented in application and V4 migration; service/MVC rules and key PostgreSQL invariants covered, borrower-limit checkout race remains |
| LIBRARIAN role and target owner/directory rules | Implemented with JWT Bearer authentication |

See the permissions matrix in [Security Roles and JWT Guide](security-roles-and-jwt-guide.md). Administrator means library assistant, not system administrator. The paginated directory and profile/password operations follow role and target-account boundaries; students/faculty may delete their own accounts. Loan policy and build guidance are in [Loan Implementation Guide](../docs/loan.md).

## Recommended implementation order

### 1. Role and account boundaries (implemented)

- [x] Add LIBRARIAN to the Java role enum and database constraint using Flyway V3.
- [x] Keep public signup pinned to STUDENT.
- [x] Restrict faculty and library-assistant creation to LIBRARIAN; pin public signup to STUDENT.
- [x] Limit administrators to student/faculty account management; allow librarians to manage administrator accounts too; protect librarian accounts from peer management.
- [x] Allow students/faculty to delete their own accounts and staff to delete only their permitted target roles.
- [x] Restrict the paginated directory and cross-account profile reads by caller and target roles.
- [x] Allow ADMINISTRATOR and LIBRARIAN to add, update, and delete books.
- [x] Add authorization tests for allowed and denied requests, including cross-account profile/password attempts.
- [ ] Define a controlled first-librarian provisioning workflow before using librarian-only account routes; no librarian seed is installed.

### 2. Verify and harden loans

- [x] Agree a 14-calendar-day term, Asia/Manila clock for offset-free `LocalDateTime` values, 3 active-loan limit, overdue checkout block, and student/faculty-only borrowing.
- [x] Add loan lifecycle, ownership checks, staff history and lost-copy resolution.
- [x] Add forward-only V4 schema with copy status, state constraints, and one-active-loan-per-copy index.
- [x] Preserve book and user history by returning HTTP 409 when referenced records are deleted.
- [x] Cover the PostgreSQL race where a catalog edit competes with a lost-loan resolution and must preserve `LOST` copy status.
- [x] Add focused loan service tests for borrower rules, availability, returns, lost-resolution scope, and history pagination.
- [x] Add loan MVC/security tests for route roles, authenticated identity, and stateless requests without CSRF enforcement.
- [x] Add PostgreSQL tests for returned history, the one-active-loan index, and protected deletion.
- [ ] Add a concurrent checkout test proving the three-active-loan borrower limit cannot be exceeded.
- [x] Verify fresh V1-to-V5 startup through the PostgreSQL integration profile.
- [x] Verify the V4-to-V5 upgrade path preserves existing replacement-cost values and schema validation.
- [ ] Verify the V3-to-V4 upgrade path against PostgreSQL.

### 3. Replace HTTP Basic with local JWT authentication (implemented)

- [x] Add public POST /app/auth/login using Spring Security AuthenticationManager, UserDetailsService, and BCrypt.
- [x] Issue short-lived HS256 access tokens and validate Bearer tokens with Spring Security OAuth2 Resource Server/JWT support.
- [x] Require an external Base64 signing key, validate issuer/audience/time/subject/role claims, and use stateless header-only request security.
- [x] Mark signup and login public in the filter chain and OpenAPI; remove HTTP Basic.
- [x] Add signed-token MVC coverage for absent, malformed, expired, wrong-signature/key/algorithm, wrong-issuer/audience, invalid claims, Basic rejection, transport restrictions, and role denials.
- [x] Enforce BCrypt's 72 UTF-8 byte limit consistently at signup, password change, and login.

### 4. Keep the public and internal docs aligned

- [x] Update README status, endpoint summary, and historical administrator bootstrap configuration.
- [x] Update AGENTS.md and Current System Engineering Reference with the new current facts and contributor rules.
- [x] Update OpenAPI operation descriptions to match role boundaries.
- [x] Add generated OpenAPI MVC assertions for Bearer security, public signup/login, and the login contract.
- [ ] Review the live OpenAPI document and determine an access path for Swagger UI; both currently inherit the authenticated default route policy, so anonymous browser loading is not available.

## Database migration rule

Before changing a migration, establish whether that version has been applied to a persistent database. If it has, add the next forward-only migration. Keep Flyway SQL, JPA mappings, and integration tests aligned. Do not destroy a database volume to resolve migration drift.

## Verification queue

- [x] Run the PostgreSQL-backed integration profile with Docker and a test-only BCrypt hash.
- [ ] Configure gateway throttling and monitoring for repeated login attempts before public internet exposure.
- [ ] Complete an independent security review before public internet exposure.

## Source documents

- [Security Roles and JWT Guide](security-roles-and-jwt-guide.md): live role/loan policy and JWT summary.
- [JWT Authentication Design](jwt-authentication-design.md): as-built boundaries, classes, flows, rules, exceptions, configuration, and validation matrix.
- [Current System Engineering Reference](engineering-reference.md): current repository facts and technical conventions.
- [API Documentation Guideline](../docs/api-documentation-guideline.md): OpenAPI/controller documentation rules.
- [Domain Decisions](../docs/domain-decisions.md): physical-copy model and loan invariant.
- [Loan Implementation Guide](../docs/loan.md): live loan behavior, endpoints, schema, and build flow.
