# Implementation Plan: Remaining Quality Improvements

This plan tracks the remaining OpenAPI improvement identified for Libro, excluding Spring Security. Completed work, including paginated book queries and test-documentation cleanup, is recorded in [Development Problems Solved](development-problems-solved.md). Keep the current feature-oriented, layered architecture: controllers own HTTP binding, services own business rules, repositories own persistence, DTOs own public payloads, and shared API documentation stays concise.

## 3. Reduce repeated OpenAPI response annotation noise

### Problem

Both controllers repeat `@ApiResponses` blocks that declare the same `ErrorResponse` schema. Operation-specific explanations are valuable, but the repeated annotation scaffolding can make route methods harder to scan and invites drift.

### Solution

Keep concise endpoint-specific operation descriptions and response meanings, and extract only response declarations that are truly identical. Do not create a general OpenAPI framework or move endpoint behavior out of the controller.

### Implementation

1. Inventory repeated response declarations and separate shared schema/content metadata from endpoint-specific descriptions and status behavior.
2. Prototype the smallest Springdoc-supported reuse mechanism for identical responses, such as a narrowly scoped composed annotation or named shared response component.
3. Keep the shared type in the existing global OpenAPI/configuration area only if it is reused enough to reduce total code and improve readability.
4. Compare generated `/v3/api-docs` before and after; confirm that status codes, descriptions, and the `ErrorResponse` schema remain equivalent.
5. If abstraction adds more indirection than it removes, retain the local annotations and instead shorten redundant descriptions.

### Architecture

Controllers remain the source of operation-specific HTTP documentation. Shared metadata is cross-cutting and belongs under `app.global` only when genuinely shared. DTO constraints and service rules remain in their current layers.

### Done when

- Endpoint methods are easier to scan without losing expected status/error details.
- Generated OpenAPI output is unchanged in meaning and still matches runtime behavior.
- No generic annotation registry, customizer framework, or duplicate YAML contract is introduced.

## Suggested next steps

1. Revisit response-annotation reuse after inspecting the resulting OpenAPI and controller readability.
