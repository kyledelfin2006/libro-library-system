# OpenAPI browser review — 2026-11-08

Reviewed the current build in a temporary local app at `/swagger-ui.html` and `/v3/api-docs` with `LIBRO_API_DOCS_ENABLED=true`. Compared the 32 generated operations with `SecurityConfig`, controller descriptions, DTO validation, and the shared response types. The review used the local app and database; it did not submit API requests or change application data.

## Matches observed

- All four tags and the new `GET /app/users/me` and `GET /app/loans` operations appear. The displayed account and loan role descriptions match the route gates and service role scope.
- Global HTTP Bearer/JWT security produces authorization controls for protected operations. Signup and login have empty operation security and no lock control.
- Login and signup show 429; relevant loan and deletion operations show 409; the new staff list shows status, overdue, borrower, page, size, and sort parameters. Existing paged routes expose page, size, and sort.

## Mismatches to fix

1. **Success responses are omitted for 17 operations.** The generated spec lists only errors for `GET/PUT/PATCH/DELETE /app/users/{universityId}`, `PUT /app/users/{universityId}/password`, `GET/PUT/PATCH/DELETE /app/books/{id}`, `GET /app/users`, `GET /app/loans/me`, `GET /app/loans/users/{universityId}`, and `GET /app/books/{all,sorted,search,replacement-cost,budget}`. The runtime returns success envelopes for these paths; add explicit success response codes and schemas so a generated client can model them.
2. **401 is omitted on 28 protected operations.** Only the newly documented `/app/users/me` and `/app/loans` operations list 401, although every protected route can reject a missing or invalid Bearer token. Add a shared 401 response description or equivalent endpoint declarations.
3. **One shared error example is shown for unrelated statuses.** Swagger displays `statusCode: 400`, `replacementCost` field errors, and a replacement-cost message for 401, 403, 404, 409, and 429 responses, including login and staff loans. The shared `ErrorResponse` schema is correct, but the field-level examples in that schema imply the wrong status and cause. Use status-specific examples where helpful or remove misleading field examples.
4. **The login request example uses `"password": "string"`.** That value cannot authenticate, and the signup request has no usable whole-body example; its generated password value also fails the complexity rule. Provide small illustrative request bodies that are valid for DTO validation without publishing a real credential.
5. **The loan response example combines incompatible states.** It shows `status: ACTIVE` with both `returnedAt` and `lostAt` populated. The schema also declares those timestamps only as strings even though runtime responses use `null` outside their matching terminal state. Show one coherent state and express nullable fields for generated clients. The generated page example also combines zero totals with one content row and contradictory sort flags.
6. **Most page-size schemas omit the enforced maximum of 100.** Only `/app/loans` and `/app/books/query` set `maximum: 100`; the other eight paged GET operations describe the limit in prose but expose no schema maximum. Align the parameter schemas with the service clamp.

The staff-list pagination parameters were missing in the first generated check and were added before this live review. No remaining role-gate or Bearer-lock mismatch was observed.
