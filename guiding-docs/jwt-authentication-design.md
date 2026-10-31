# JWT Authentication: Implemented Design

**Status:** implemented. This document describes the running application; code and tests remain authoritative.
**Scope:** local login and short-lived JWT Bearer authentication for the existing Libro API.
**Related policy:** [Roles and JWT Guide](security-roles-and-jwt-guide.md), [Engineering Reference](engineering-reference.md).

## Design boundaries

| Concern | Owning implementation | Boundary |
| --- | --- | --- |
| Login HTTP contract | `AuthAPI`, `LoginRequestDTO`, `TokenResponseDTO` | Accept university ID and password only; no caller-selected role or claims |
| Credential verification | `AuthService`, `AuthenticationManager`, `LibroUserDetailsService`, `PasswordConfig` | Spring Security loads the stored account and verifies the BCrypt hash |
| Token issuance | `AuthService`, `JwtConfiguration` | Nimbus encoder signs one HS256 access token from the authenticated name and one `ROLE_...` authority |
| Token validation | Spring Security Resource Server and `JwtConfiguration` | Decoder validates signature, issuer, audience, time claims, required subject, and one known role before route authorization |
| Route-level authorization | `SecurityConfig` | Exact public POST routes; existing role matchers; unmatched routes require authentication |
| Ownership and target-role rules | `UserService`, `LoanService` | Uses the authenticated university ID and authorities; a valid token does not bypass service rules |
| Login errors | `GlobalExceptionHandler` | Unknown account and wrong password share one generic 401 response |
| Bearer errors | Spring Security Resource Server | Invalid/missing credentials are rejected in the filter chain before MVC advice |
| API schema | `OpenApiConfig`, controller annotations | Protected operations use HTTP Bearer/JWT; signup and login are public |
| Persistence | None added | JWTs are stateless; no token table, Flyway migration, refresh-token store, or denylist |

## HTTP contract

| Method and path | Authentication | Contract |
| --- | --- | --- |
| `POST /app/users/signup` | Public | Existing signup contract; service always assigns `STUDENT` |
| `POST /app/auth/login` | Public | Validated credentials in; 200 token response on success; 400 for invalid request shape; generic 401 for rejected credentials |
| All other routes | Bearer token required | Existing role, ownership, and response contracts continue to apply |

Login request:

```json
{"universityId":"2025-4321","password":"<user supplied password>"}
```

Successful response:

```json
{"accessToken":"<signed-token>","tokenType":"Bearer","expiresIn":900}
```

The login response includes `Cache-Control: no-store` and `Pragma: no-cache`. Send the token only as `Authorization: Bearer <token>`. Query parameters, form fields, cookies, and HTTP Basic are not accepted as authentication. Use HTTPS outside local development and redact Authorization headers from access logs.

## Token rules

| Element | Issued value | Validation rule |
| --- | --- | --- |
| Algorithm | `HS256` | Decoder is pinned to HS256; no algorithm negotiation or fallback |
| `iss` | `libro-api` by default | Must exactly match configured issuer |
| `sub` | Authenticated university ID | Must be present and non-blank; becomes `Authentication.getName()` |
| `aud` | One configured API audience, `libro-api` by default | Must contain exactly the configured audience |
| `roles` | One-element array, e.g. `["STUDENT"]` | Must contain exactly one known `UserRole`; maps to `ROLE_STUDENT` and equivalent authorities |
| `iat` | UTC issuance instant | Required and may not be more than 30 seconds in the future |
| `exp` | `iat` plus configured lifetime | Required, after `iat`, and no more than 900 seconds after it |
| `nbf` | Omitted by the issuer | If present, Spring timestamp validation enforces it with 30 seconds of clock skew |
| Other profile or credential claims | Omitted | Password, password hash, name, email, course, and major are never included |

The default access lifetime is 900 seconds. `LIBRO_JWT_ACCESS_TOKEN_TTL_SECONDS` may lower it but cannot raise it above 900. Timestamp validation permits 30 seconds of clock skew, so a token may remain valid up to 930 seconds after issuance when clocks are synchronized.

## Authorization boundaries

`SecurityConfig` preserves the established route-role policy. `UserService` and `LoanService` apply finer ownership and target-role rules after the filter chain authenticates the caller.

| Route group | Filter-chain rule | Service-level rule |
| --- | --- | --- |
| `POST /app/users/signup`, `POST /app/auth/login` | Public | Signup pins role to `STUDENT`; login derives role from authenticated account |
| `POST /app/users/faculty`, `POST /app/users/administrators` | `LIBRARIAN` | Each operation pins the created account to its intended role |
| User directory | `ADMINISTRATOR` or `LIBRARIAN` for `GET /app/users` | Administrators see student/faculty accounts; librarians may also see administrators |
| User profile/password routes | Authenticated library roles | Owner access plus staff-to-target-role limits; another librarian cannot be managed |
| User deletion | Authenticated library roles | Student/faculty owners may delete themselves; staff may delete permitted target roles; librarian accounts and staff self-deletion are blocked; loan history causes 409 |
| Book reads | Any of the four roles | Existing catalog rules |
| Book writes | `ADMINISTRATOR` or `LIBRARIAN` | Existing catalog validation and copy-state rules |
| Loan checkout and return | `STUDENT` or `FACULTY` | Borrower is `Authentication.getName()`; only own active loan may be returned |
| Loan lost resolution | `ADMINISTRATOR` or `LIBRARIAN` | Target borrower role remains scoped by `LoanService` |
| Own loan history | Any of the four roles | History is selected by authenticated subject |
| Another patron's loan history | `ADMINISTRATOR` or `LIBRARIAN` | Target-role visibility remains scoped in `LoanService` |
| Any route added without a matcher | Authenticated by default | Add an explicit rule only after reviewing the boundary |

Full role meanings and account-operation details are in the [role policy](security-roles-and-jwt-guide.md#role-policy).

## Runtime settings

| Environment variable | Application property | Default | Rule |
| --- | --- | --- | --- |
| `LIBRO_JWT_SECRET_BASE64` | `libro.security.jwt.secret-base64` | None | Required; valid Base64 that decodes to at least 32 bytes; missing, malformed, or short keys stop startup |
| `LIBRO_JWT_ISSUER` | `libro.security.jwt.issuer` | `libro-api` | Non-blank exact issuer |
| `LIBRO_JWT_AUDIENCE` | `libro.security.jwt.audience` | `libro-api` | Non-blank single audience |
| `LIBRO_JWT_ACCESS_TOKEN_TTL_SECONDS` | `libro.security.jwt.access-token-ttl-seconds` | `900` | Positive and at most 900 seconds |

Generate the key from cryptographically secure randomness and provide it through an untracked local environment file or deployment secret manager. For example, `openssl rand -base64 32` generates 32 random bytes and encodes them. Do not commit a real key, put one in a migration, or log one. Every application replica must share the same active key and issuer/audience settings. Replacing the shared HS256 key invalidates all existing tokens.

## Software design and request flows

| Flow stage | Component | Rule and result |
| --- | --- | --- |
| 1. Bind login | `AuthAPI` → `LoginRequestDTO` | Validates `####-####` university ID and non-blank password, capped at 72 characters; password is write-only and DTO has no role field |
| 2. Check BCrypt input boundary | `AuthService` and `PasswordPolicy` | Passwords must satisfy the 8–72 character complexity policy and stay at or below 72 UTF-8 bytes; signup, password change, and login apply the byte rule before BCrypt work |
| 3. Authenticate | `AuthenticationManager` → `DaoAuthenticationProvider` → `LibroUserDetailsService` → `UserRepository` | Looks up the account by university ID and verifies its stored hash using the existing BCrypt `PasswordEncoder` |
| 4. Build trusted claims | `AuthService` | Takes subject and one role from the successful `Authentication`; sets configured issuer/audience and UTC `iat`/`exp` |
| 5. Sign response | `JwtEncoder` from `JwtConfiguration` | Nimbus signs HS256; controller returns Bearer token with no-store cache headers |
| 6. Read protected request | Spring Security Resource Server | Reads Authorization header only; decoder validates signature and claims; converter maps role to existing authority |
| 7. Apply permissions | `SecurityConfig` → controller → service | Route role gate runs first; service receives the verified subject and enforces ownership/target-role rules |

The protected-request path does not load the account from PostgreSQL again. This keeps requests stateless and means role or account changes do not immediately revoke an already-issued token.

## Error and exception boundaries

| Condition | HTTP result | Response boundary |
| --- | --- | --- |
| Missing or malformed login JSON; invalid university ID; blank or over-72-character password | 400 | Shared `ErrorResponse` validation shape; password values are not echoed |
| Password exceeds 72 UTF-8 bytes at login | 401 | Same generic response as other rejected credentials |
| Password exceeds 72 UTF-8 bytes at signup or password change | 400 | Shared validation response; no password value is echoed |
| Unknown university ID or incorrect password | 401 | `{"error":"Authentication failed","details":"University ID or password is incorrect",...}` for both cases |
| Missing, malformed, expired, not-yet-valid, wrong-signature, wrong-key, wrong-algorithm, wrong-issuer, wrong-audience, or invalid-claims Bearer token | 401 | Spring Security response; it occurs before MVC `GlobalExceptionHandler` |
| Valid token without required route role | 403 | Spring Security authorization response |
| Valid token but owner/target role rule fails | Existing service/API response | JWT does not change service-level access checks; selected resources may retain the existing 404 concealment behavior |
| Authentication provider/database failure | 500 generic server response | Not disguised as invalid credentials; internal details are not returned |
| Missing, malformed, or undersized JWT secret at startup | Application startup fails | No fallback development key exists |

Login errors use the shared JSON error envelope; Resource Server 401/403 errors are generated before MVC and may have a different body. The filter response does not include decoder exception detail, key material, or claim values.

## Validation matrix

| Case | Expected result | Coverage |
| --- | --- | --- |
| Public signup and login | Reach their controllers without a Bearer token | `SecurityFlowMvcTest` |
| Correct credentials | Login returns a token that authenticates a protected book request | `SecurityFlowMvcTest` |
| Unknown account vs wrong password | Same generic 401 details | `SecurityFlowMvcTest` |
| 72 UTF-8 password bytes vs multibyte overflow | Exactly 72 accepted by the shared boundary; overflow rejected before BCrypt | `UserServiceTest`, `SecurityFlowMvcTest` |
| Issued token claim set | Subject/role/lifetime correct; no password or profile claims | `SecurityFlowMvcTest` |
| Basic credentials | 401; no repository/controller call | `SecurityFlowMvcTest` |
| Missing or malformed Bearer token | 401 | `SecurityFlowMvcTest` |
| Altered signature or different key | 401 | `SecurityFlowMvcTest` |
| `alg=none` or HS384 | 401 | `SecurityFlowMvcTest` |
| Expired beyond 30-second skew; future `nbf` or `iat` | 401 | `SecurityFlowMvcTest` |
| Wrong issuer/audience, extra audience, absent subject/role, unknown/duplicate role, excessive lifetime | 401 | `SecurityFlowMvcTest` |
| Token supplied in query string or cookie | 401 | `SecurityFlowMvcTest` |
| Valid student token writes book; librarian/administrator writes book | 403 / allowed | `SecurityFlowMvcTest` |
| Unsafe Bearer API operation without CSRF token | Reaches route authorization/controller | `LoanSecurityMvcTest` |
| Generated OpenAPI | Bearer on protected operations; no scheme on signup/login; login schema and errors documented | `OpenApiMvcTest` |

The normal Maven test suite exercises the real filter and decoder with signed compact tokens. PostgreSQL integration tests remain separate and are not required for JWT behavior because no schema changed.

## Accepted limits and deployment requirements

| Limit | Current decision |
| --- | --- |
| Revocation | No refresh tokens, token registry, or denylist. Password/role changes and account deletion can leave a token usable through expiry plus 30 seconds of skew. |
| Login abuse | Before public internet exposure, the gateway must throttle repeated `POST /app/auth/login` attempts and monitor abusive traffic. Thresholds are deployment-owned. |
| Token theft | A bearer token can be replayed by anyone holding it until expiry. Use HTTPS, short lifetime, header-only transport, no-store responses, and log redaction. |
| Browser storage | The API does not accept cookies. If a client later stores tokens in cookies, revisit CSRF; prefer a deliberate browser storage design and mitigate XSS. |
| Key rotation | One shared HS256 key is used for issuing and validating. Coordinated rotation invalidates existing tokens; use asymmetric keys only if verification moves to separate services. |
| External identity | This API issues its own tokens; it is not an OAuth authorization server and does not implement institutional SSO or third-party consent flows. |
