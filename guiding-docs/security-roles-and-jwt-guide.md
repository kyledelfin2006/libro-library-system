# Libro Roles and JWT Guide

This guide records the live role, account, loan, and JWT policy. The detailed as-built JWT design and validation matrix is in [JWT Authentication Design](jwt-authentication-design.md).

## Current state

- `POST /app/auth/login` authenticates through Spring Security, `LibroUserDetailsService`, and BCrypt, then issues a 15-minute HS256 access token.
- Protected routes accept JWT Bearer tokens only. The filter chain is stateless and CSRF is disabled for the header-only API. Signup and login are the only public application POST routes.
- `UserRole` contains `STUDENT`, `FACULTY`, `ADMINISTRATOR`, and `LIBRARIAN`; Flyway V3 adds the librarian value to the accepted database role constraint.
- Role and account boundaries below remain in force under JWT. No librarian is seeded; first-account provisioning is deferred.
- Loan routes and persistence are implemented under JWT. A `Book` row represents one physical copy; only one active loan may reference a copy, and a copy marked lost cannot be loaned again.

## Role policy

Role names describe library access levels, not school job titles:

- `LIBRARIAN` is the librarian of the library and has the highest library-management authority.
- `ADMINISTRATOR` means a library assistant, such as fellow faculty, student assistants, or volunteers. It does not mean a system administrator.
- `FACULTY` means school faculty using the library as patrons.
- `STUDENT` means students using the library as patrons.

| Role | Can do | Cannot do | Loan permissions |
|---|---|---|---|
| `STUDENT` | View/search books; manage and delete their own account; create a student account through public signup | View/manage other users; add, update, or delete books; create privileged accounts | Borrow and return own loans; view own loan history |
| `FACULTY` | Same patron permissions as students; manage and delete their own account | Self-register as faculty; view/manage other users; add, update, or delete books | Same loan permissions as students |
| `ADMINISTRATOR` (library assistant) | View, edit, and reset student/faculty accounts; delete student/faculty accounts; add, update, and delete books; manage own profile/password | Create accounts; manage/delete administrator or librarian accounts; delete own account | Cannot borrow or return books; view own history and student/faculty loan histories |
| `LIBRARIAN` | Create faculty and administrator accounts; view, edit, reset, and delete student/faculty/administrator accounts; add, update, and delete books; manage own profile/password | Manage/delete another librarian account; delete own account | Cannot borrow or return books; view own history and student/faculty/administrator loan histories |

`FACULTY` has the same patron permissions as `STUDENT`. Administrators inherit the patron book permissions. The librarian can manage the library collection and user groups listed above. Neither staff role can change another librarian's account. Account roles are selected by the service, never by a submitted request role.

Account boundaries:

- Public `POST /app/users/signup` always creates `STUDENT`; a submitted role must never grant privileges.
- Only `LIBRARIAN` may create a school faculty account through `POST /app/users/faculty` or a library assistant through `POST /app/users/administrators`.
- `GET /app/users` lists student and faculty accounts for administrators; the librarian can also list administrator accounts.
- `GET /app/users/{universityId}` is available to the owner. Administrators may additionally read student/faculty profiles. Librarians may additionally read student/faculty/administrator profiles, but no staff role may read another librarian's profile.
- Profile PATCH/PUT and password change are available to the owner, administrators, and librarians. Administrators may manage student/faculty accounts; librarians may also manage administrator accounts. Neither staff role may manage another librarian's profile/password.
- `DELETE /app/users/{universityId}` allows students/faculty to delete themselves, administrators to delete students/faculty, and librarians to delete students/faculty/administrators. Administrator and librarian accounts cannot delete themselves; librarian accounts cannot be deleted through this route.
- To preserve loan history and its foreign-key references, account deletion returns HTTP 409 while any loan record references that user.

Use Spring authorities named `ROLE_STUDENT`, `ROLE_FACULTY`, `ROLE_ADMINISTRATOR`, and `ROLE_LIBRARIAN`. Keep authorization rules in the security layer and ownership checks in the service layer, where the authenticated university ID is passed explicitly. Do not trust a user ID in a request body to identify the caller.

## Loan API and persistence

Loans are implemented as a separate feature under `app.loan`, following the existing feature-oriented package structure.

| Method | Path | Purpose | Access |
|---|---|---|---|
| POST | `/app/loans/{bookId}` | Borrow one physical copy for the authenticated user | Students/faculty only; at most three active loans and no overdue active loan |
| PUT | `/app/loans/{loanId}/return` | Return the caller's active loan | Students/faculty only; own loan only |
| PUT | `/app/loans/{loanId}/lost` | Staff closes an active loan as lost and marks the copy lost | Library assistants: student/faculty; librarians: student/faculty/library assistants |
| GET | `/app/loans/me` | View the caller's loan history, including active and terminal loans | All roles |
| GET | `/app/loans/users/{universityId}` | View a patron's loan history | Administrators: students/faculty; librarians: students/faculty/administrators |

The borrower is always derived from the authenticated principal. The client supplies a book ID to borrow, never a borrower ID. Loan responses contain a DTO, not JPA entities.

Only students and faculty may create loans or return their own loans. Administrators (library assistants) and librarians cannot borrow or return books, but can view their own history through `/app/loans/me`. They may also view other patrons' histories within the role boundaries above. A borrower may have at most three active loans. A borrower with any overdue active loan cannot borrow another copy. Overdue is calculated when needed and is not stored. Library assistants and librarians can mark permitted active patron loans as lost; the action records who and when and marks the physical copy unavailable. Penalties and repeat-offender rules are out of scope for the current loan implementation.

The `Loan` entity references one `User` and one `Book`, with `borrowedAt`, `dueAt`, `status`, nullable `returnedAt`, and optional loss audit fields. `ACTIVE`, `RETURNED`, and `LOST` are event-driven states; overdue is calculated at read time. Writes run in `LoanService` transactions. The service checks availability for a clear error, and PostgreSQL enforces the concurrency invariant with a partial unique index on `book_id` where `status = 'ACTIVE'`. Loan rows are preserved; user and book deletion are blocked while loan history exists.

The approved loan period is 14 calendar days with no weekend or holiday extension. The service obtains timestamps using `LocalDateTime.now` with an Asia/Manila `Clock`, independent of the server timezone. `LocalDateTime` itself carries no zone or UTC offset; the API treats those values as Manila-local time. A book copy with any loan history cannot be deleted; return HTTP 409 and preserve its loan rows. Marking a loan lost records the staff member and time, closes that loan, and marks the physical copy `LOST`; lost copies cannot be borrowed or deleted. Monetary penalties and borrower suspension are out of scope.

## JWT login and request validation

| Boundary | Live rule |
| --- | --- |
| Login | Public `POST /app/auth/login`; `universityId` and `password` only; BCrypt verification goes through `AuthenticationManager` |
| Password boundary | 8–72 characters with the required complexity; no more than 72 UTF-8 bytes before BCrypt at signup, password change, and login |
| Credential failure | Unknown university ID and incorrect password return the same generic HTTP 401 |
| Token | HS256; 15-minute maximum; issuer and audience are configurable; subject is university ID; exactly one known role is included |
| Bearer transport | `Authorization: Bearer <token>` only; query strings, form fields, cookies, and HTTP Basic are rejected |
| Validation | Signature, HS256 algorithm, issuer, single audience, time claims, non-blank subject, and one known role are checked before authorization |
| Route authorization | Existing role matchers remain in `SecurityConfig`; ownership and target-role checks remain in `UserService` and `LoanService` |
| Runtime state | Stateless; no session, refresh token, token table, or denylist; CSRF is disabled while only Authorization-header credentials are accepted |
| Secret | `LIBRO_JWT_SECRET_BASE64` is required and must decode to at least 32 bytes; no default or source-controlled secret is used |
| Stale claims | A role/password change or account deletion may leave an existing token usable through its expiry plus 30 seconds of clock skew |
| Deployment | Require HTTPS and log redaction; gateway throttling and monitoring for repeated login attempts are required before public internet exposure |

The login response contains `accessToken`, `tokenType: "Bearer"`, and `expiresIn: 900`, with no-store cache headers. Detailed claim rules, configuration keys, error responses, flow ownership, and negative-case coverage are in [JWT Authentication Design](jwt-authentication-design.md). Spring Security references: [Resource Server](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/index.html), [DAO password authentication](https://docs.spring.io/spring-security/reference/servlet/authentication/passwords/dao-authentication-provider.html), and [CSRF protection](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html).

## Verification boundaries

- `SecurityFlowMvcTest` exercises login through the real `AuthenticationManager` and BCrypt provider, then passes the issued compact token through the real Resource Server filter and decoder.
- The security MVC suite covers missing, malformed, altered-signature, wrong-key, wrong-algorithm, expired, future-`nbf`, wrong-issuer/audience, invalid-role, query/cookie transport, Basic rejection, generic login failures, and role denials.
- `OpenApiMvcTest` checks the generated Bearer scheme and the public signup/login declarations.
- Loan/account service tests continue to cover identity ownership and target-role rules. PostgreSQL integration tests remain for database behavior; no JWT schema migration is required.
