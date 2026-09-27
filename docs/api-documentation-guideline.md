# API Documentation Guideline

This guide defines how to keep the book and user APIs complete in generated OpenAPI while keeping Spring MVC controller code focused on HTTP behavior. Swagger UI should be useful as the project's interactive API reference and portfolio demonstration; the OpenAPI document is the contract it renders.

## Documentation ownership

Keep each fact in the layer that already owns it:

| Concern | Source of truth |
| --- | --- |
| Routes, HTTP methods, status codes, request parameters, and response types | Spring MVC mappings and return types |
| Stable field names, types, constraints, and representative values | Request and response DTOs, with Jakarta Validation and focused `@Schema` metadata where inference is insufficient |
| Behavior that cannot be inferred from the signature | Concise `@Operation` descriptions and `@Parameter` descriptions |
| Shared success and error payload shape | `ApiResponse`, `ErrorResponse`, and their documented schemas |
| Business rules and cross-field behavior | Service implementation; summarize the client-visible rule in the relevant operation description |
| Full endpoint catalog, workflows, and setup/deployment instructions | README |

Do not copy the same prose into controller comments, annotations, README tables, and DTOs. Keep the OpenAPI contract authoritative for endpoint behavior, and let the README explain workflows, local setup, and deployment context.

## Controller rules

- Keep `@Tag` at each controller with a short resource name and purpose.
- Give every endpoint a concise `@Operation(summary = ...)` using an action and resource (for example, “List books” or “Change a user password”). Avoid repeating the route or HTTP verb in the summary.
- Add a description only for behavior that a consumer cannot infer from the method, parameters, DTO constraints, or response schema. Keep it to the contract: partial versus complete update, normalization, matching behavior, pagination defaults/limits, ordering, or an important business restriction.
- Do not add JavaDoc solely to repeat the OpenAPI annotation. JavaDoc remains appropriate for non-obvious implementation behavior and service contracts.
- Do not put mapping logic, example construction, response-building helpers, or OpenAPI-specific branches in controllers. Documentation must not change runtime behavior.
- Avoid hand-written path and schema declarations when Springdoc can derive them from Spring MVC and DTO types.

## DTO schema rules

- Treat request and response DTOs as the public contract; never document or expose JPA entities as API models.
- Use Jakarta Validation annotations for actual request validation. Add `@Schema` descriptions/examples for fields only when they clarify domain meaning, format, units/currency, normalization, or conditional applicability beyond the annotation itself.
- Keep examples small, valid, and representative. Never use real personal data, passwords, tokens, or credentials. For password fields, describe the policy but do not provide a plausible password example.
- Do not repeat basic facts already evident from a field name or constraint. Schema annotations are not a second validation system; do not claim a constraint the application does not enforce.
- For PATCH DTOs, document omission and `null` behavior explicitly when they differ. Do not add `@Valid` merely to improve the generated schema; preserve the endpoint's actual validation semantics.
- Mark sensitive or server-managed fields clearly, and exclude them from response DTOs unless the public contract requires them.

## Responses and errors

- Every operation must document its success status and response shape. Include non-success statuses that are part of that operation's expected contract, such as validation failure, missing resource, or conflict.
- Describe the existing `ErrorResponse` contract, including `fieldErrors`, so Swagger users know how to handle validation errors. Do not document exception messages or database details as stable API data.
- Use the current response envelope or direct DTO exactly as implemented. Do not imply all endpoints share one envelope while response shapes remain mixed.
- Prefer inferred schemas and short response descriptions. Add explicit examples only where they make a non-obvious shape or workflow materially easier to use.
- Avoid repeating large `@ApiResponses` blocks on every method. Start with concise endpoint-specific response annotations. If the same response definition is genuinely repeated across several operations, introduce one small reusable composed annotation or shared OpenAPI component and use it consistently. Do not build a custom annotation framework or globalizer preemptively.

## Parameters and examples

- Document parameters whose meaning is ambiguous or whose allowed values/limits are not represented elsewhere. Use typed Java parameters and validation for actual enforcement; documentation alone is not validation.
- For pagination, make the zero-based page convention, default size, maximum size, allowed sort fields, and sort direction discoverable in Swagger. Keep the rules next to the endpoint or in one reusable description only if the same behavior applies consistently.
- Add examples for representative create/update payloads and unusual query formats when DTO examples and inferred parameter schemas are not enough. Keep one source of truth where possible; avoid copying the same JSON example into multiple annotations and README sections.
- Document security requirements accurately. The current API permits unauthenticated access; do not declare bearer or cookie security in OpenAPI until the application enforces it. Before an untrusted deployment, authentication and endpoint authorization must be implemented and documented together.

## Keeping annotations readable

Use this order when documenting an endpoint:

1. Let Spring MVC provide path, verb, parameter binding, and response type.
2. Let DTO validation and types describe normal field constraints.
3. Add one `@Operation` summary and a short description only for non-inferable client behavior.
4. Add response annotations only for expected status/error details that inference cannot provide.
5. Add examples only when they remove meaningful ambiguity.

Do not compress all endpoint documentation into controller JavaDoc, create a parallel OpenAPI YAML file for the same routes, or add customizers solely to avoid a few clear annotations. If annotation blocks become repetitive or obscure the method, extract only the repeated contract detail and keep the endpoint-specific behavior beside the mapping.

## Swagger UI and deployment readiness

Swagger UI is an interactive API explorer backed by the generated OpenAPI specification. It is suitable as the project's API-facing portfolio UI, but it does not replace a custom library frontend and does not itself deploy the Spring Boot service. A deployment still needs to host the application and database, publish the intended Swagger UI/spec routes, and configure TLS, CORS, and authentication consistently with the deployed API.

Before calling the API documentation portfolio-ready, inspect the generated `/v3/api-docs` and Swagger UI for both tags and verify:

- every live route appears under the correct book or user tag;
- summaries, descriptions, parameter defaults/limits, request schemas, response schemas, status codes, and expected errors match runtime behavior;
- examples validate against the request DTOs and reveal no secrets or personal data;
- PATCH and PUT semantics are distinct where the implementation distinguishes them;
- security shown in the UI matches the actual security filter chain;
- no endpoint is accidentally documented as live when it is not implemented.

When an endpoint or public contract changes, update this guideline only if the documentation policy changes; update the OpenAPI annotations/DTO metadata, README endpoint/workflow documentation, and `AGENTS.md` endpoint inventory as appropriate. Add or update controller/API tests for contract changes. Do not claim a generated-spec or UI check unless it was actually performed.
