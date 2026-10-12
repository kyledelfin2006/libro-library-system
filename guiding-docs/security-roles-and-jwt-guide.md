# Libro Roles and JWT Guide

This guide defines the target authorization policy and a staged implementation path for replacing HTTP Basic with local JWT authentication. It is a specification, not a statement that the endpoints described here are already implemented.

## Current state

- Libro currently uses HTTP Basic authentication backed by `LibroUserDetailsService` and BCrypt.
- `UserRole` contains `STUDENT`, `FACULTY`, `ADMINISTRATOR`, and `LIBRARIAN`; Flyway V3 adds the librarian value to the accepted database role constraint.
- Role and account boundaries below are implemented while HTTP Basic remains active. No librarian is seeded; first-account provisioning is deferred. JWT and loans are still planned.
- There is no loan entity or loan API yet. A `Book` row represents one physical copy, and the loan domain must prevent more than one active loan for that copy.

## Role policy

Role names describe library access levels, not school job titles:

- `LIBRARIAN` is the librarian of the library and has the highest library-management authority.
- `ADMINISTRATOR` means a library assistant, such as fellow faculty, student assistants, or volunteers. It does not mean a system administrator.
- `FACULTY` means school faculty using the library as patrons.
- `STUDENT` means students using the library as patrons.

| Role | Can do | Cannot do | Future loan permissions (not implemented) |
|---|---|---|---|
| `STUDENT` | View/search books; manage and delete their own account; create a student account through public signup | View/manage other users; add, update, or delete books; create privileged accounts | Borrow/return books and view own loan history |
| `FACULTY` | Same patron permissions as students; manage and delete their own account | Self-register as faculty; view/manage other users; add, update, or delete books | Same as students |
| `ADMINISTRATOR` (library assistant) | View, edit, and reset student/faculty accounts; delete student/faculty accounts; add, update, and delete books; manage own profile/password | Create accounts; manage/delete administrator or librarian accounts; delete own account | Borrow/return and view own history; view student/faculty loan histories |
| `LIBRARIAN` | Create faculty and administrator accounts; view, edit, reset, and delete student/faculty/administrator accounts; add, update, and delete books; manage own profile/password | Manage/delete another librarian account; delete own account | Borrow/return and view own history; view student, faculty, and administrator loan histories |

`FACULTY` has the same patron permissions as `STUDENT`. Administrators inherit the patron book permissions. The librarian can manage the library collection and user groups listed above. Neither staff role can change another librarian's account. Account roles are selected by the service, never by a submitted request role.

Account boundaries:

- Public `POST /app/users/signup` always creates `STUDENT`; a submitted role must never grant privileges.
- Only `LIBRARIAN` may create a school faculty account through `POST /app/users/faculty` or a library assistant through `POST /app/users/administrators`.
- `GET /app/users` lists student and faculty accounts for administrators; the librarian can also list administrator accounts.
- `GET /app/users/{universityId}` is available to the owner. Administrators may additionally read student/faculty profiles. Librarians may additionally read student/faculty/administrator profiles, but no staff role may read another librarian's profile.
- Profile PATCH/PUT and password change are available to the owner, administrators, and librarians. Administrators may manage student/faculty accounts; librarians may also manage administrator accounts. Neither staff role may manage another librarian's profile/password.
- `DELETE /app/users/{universityId}` allows students/faculty to delete themselves, administrators to delete students/faculty, and librarians to delete students/faculty/administrators. Administrator and librarian accounts cannot delete themselves; librarian accounts cannot be deleted through this route.
- To preserve future loan history and its foreign-key references, account deletion must return HTTP 409 while any loan record references that user.

Use Spring authorities named `ROLE_STUDENT`, `ROLE_FACULTY`, `ROLE_ADMINISTRATOR`, and `ROLE_LIBRARIAN`. Keep authorization rules in the security layer and ownership checks in the service layer, where the authenticated university ID is passed explicitly. Do not trust a user ID in a request body to identify the caller.

## Loan API and persistence

Loans are a separate feature under `app.loan`, following the existing feature-oriented package structure.

| Method | Path | Purpose | Access |
|---|---|---|---|
| POST | `/app/loans/{bookId}` | Borrow one physical copy for the authenticated user | All roles |
| PUT | `/app/loans/{loanId}/return` | Return the caller's active loan | All roles; own loan only |
| GET | `/app/loans/me` | View the caller's loan history, including active and returned loans | All roles |

The borrower is always derived from the authenticated principal. The client supplies a book ID to borrow, never a borrower ID. A loan response should contain its loan ID, book ID, borrow date, due date, and nullable return date; it must not expose JPA entities.

The `Loan` entity references one `User` and one `Book`, with `borrowedAt`, `dueAt`, and nullable `returnedAt` fields. Keep writes in `LoanService` transactions. Check availability for a clear error, then enforce the concurrency invariant in PostgreSQL with a partial unique index on `book_id` where `returned_at IS NULL`. Translate a duplicate-active-loan constraint failure to HTTP 409. Preserve loan rows; user deletion is blocked while loan history exists.

**Policy needed before coding loans:** the library must choose the loan period used to calculate `dueAt`. Do not invent a duration in the implementation.

## JWT login and request validation

### Login contract

Add public `POST /app/auth/login` with a request containing `universityId` and `password`. Authenticate through Spring Security's `AuthenticationManager`, existing `LibroUserDetailsService`, and BCrypt `PasswordEncoder`; do not compare password hashes manually. Use the same generic HTTP 401 result for an unknown university ID and an incorrect password.

Return a token response such as:

```json
{
  "accessToken": "<signed-token>",
  "tokenType": "Bearer",
  "expiresIn": 900
}
```

Mark signup and login public in Spring Security and OpenAPI. Document other routes with the HTTP Bearer security scheme.

### Token format and settings

- Use Spring Security's OAuth2 Resource Server/JWT support for Bearer-token validation; do not write a custom JWT validation filter. Use Spring Security's Nimbus-backed encoder/decoder for local token creation and validation.
- Sign tokens with HS256 and a random secret containing at least 256 bits of entropy. Load its Base64 value from `LIBRO_JWT_SECRET_BASE64`; never commit it or put it in the migration. Configure issuer as `libro-api` (overridable by deployment configuration).
- Include `iss`, `sub` (university ID), `roles` (one role name), `iat`, and `exp`. Set expiry to 15 minutes. Do not include password, password hash, or unnecessary profile fields.
- Validate signature, issuer, and expiry on every Bearer request. Map the `roles` claim to the corresponding `ROLE_...` authority before authorization rules run.
- Require `Authorization: Bearer <token>`. Do not accept tokens in query strings, form fields, or cookies.
- Use stateless sessions. Since credentials are sent explicitly in the Authorization header and are not browser-attached cookies, disable CSRF for this API-only Bearer flow. If the client later stores tokens in cookies, revisit CSRF protection. Require HTTPS outside local development.
- Use access tokens only in the first iteration. Do not add refresh tokens or a token denylist. A role change, password change, or account deletion can leave an already-issued token usable until its 15-minute expiry; document and accept that limit for this version.

Spring Security documents Resource Server support for JWT Bearer tokens and the `UserDetailsService`/`PasswordEncoder` password provider used for login: [Resource Server](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/index.html), [DAO password authentication](https://docs.spring.io/spring-security/reference/servlet/authentication/passwords/dao-authentication-provider.html). Review [CSRF protection](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html) if token transport changes.

## Implementation sequence

1. **Roles and access rules:** implemented in `UserRole`, `SecurityConfig`, `UserService`, and Flyway V3. Public signup remains pinned to `STUDENT`; directory and ownership boundaries are enforced while Basic authentication remains active.
2. **Loans:** add the entity, repository, next forward-only migration, partial unique index, DTOs, service, and controller. Resolve the loan-period policy first. Keep user deletion blocked while loan history references the account.
3. **JWT:** add the Spring Boot OAuth2 Resource Server starter, JWT encoder/decoder configuration, secret/issuer settings, login DTO/controller/service, Bearer authorization, stateless session policy, and OpenAPI scheme. Remove HTTP Basic after the Bearer flow is working.
4. **Documentation:** update `AGENTS.md`, README endpoint/security inventories, and OpenAPI annotations when the implementation lands. Keep this guide as the detailed policy reference.

## Acceptance checks

- Anonymous requests can reach only signup and login. Missing, malformed, expired, wrong-signature, and wrong-issuer tokens receive HTTP 401; a valid token without the required role receives HTTP 403.
- Each role can perform exactly the operations in the role matrix. Students/faculty cannot list or read other accounts, update another user's profile/password, or create privileged accounts. Administrators manage student/faculty accounts; librarians may also manage library assistant accounts. Neither staff role may manage another librarian account.
- Signup with `FACULTY`, `ADMINISTRATOR`, or `LIBRARIAN` in the payload still creates a student. Only librarians create faculty and library assistant accounts. Students/faculty may delete themselves, administrators may delete student/faculty accounts, and librarians may also delete library assistant accounts; staff cannot delete their own account.
- Login uses the stored BCrypt hash and does not reveal whether the university ID exists.
- Token claims contain the correct subject and role, use the configured issuer and 15-minute expiry, and contain no credential/profile secrets.
- A loan always uses the authenticated caller as borrower. A borrower cannot return another user's loan. Two concurrent borrow attempts cannot create two active loans for one book copy.
- Account deletion with loan history returns HTTP 409 and leaves the account and loan rows intact.
- Generated `/v3/api-docs` shows Bearer security on protected operations and no security requirement on signup/login. Run the normal Maven test suite and PostgreSQL integration profile where Docker is available.
