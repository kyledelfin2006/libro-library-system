# 0002 — Layered feature packages instead of hexagonal ports and adapters

Status: accepted

## Context

The code is organized by feature (`auth`, `book`, `loan`, `user`) with a shared `global` package. Inside each feature the classes are split by layer: `controller`, `service`, `repository`, `entity`, `dto`, `mapper`, `exception`. A cleanup review asked whether to move to a full hexagonal layout (domain model free of JPA, ports and adapters, separate persistence entities).

The domain is a CRUD-heavy catalog with one real workflow (loans) and a single PostgreSQL database. Every feature has one inbound adapter (REST) and one outbound adapter (JPA).

## Decision

Keep layered feature packages, and make the layer rules strict and identical in every feature:

- Controllers handle HTTP only and talk to one service.
- Services own business rules, authorization checks that depend on the loaded target, transactions, and mapping. They accept request DTOs and return response DTOs, so entities never leave a transaction.
- Repositories are the only persistence access; JPA entities stay inside the feature's service and repository.
- Cross-cutting code (error handling, response envelopes, request IDs, time, OpenAPI) lives in `global`.
- Failures use two shared base types, `ResourceNotFoundException` (404) and `InvalidRequestException` (400), so the global handler does not grow with each feature.

## Consequences

- Readers familiar with Spring recognize the layout immediately, and each feature looks the same.
- JPA annotations stay on the domain classes. Switching persistence technology would touch services and entities; with one database and no plan to change it, a port/adapter layer would be indirection without a second implementation.
- If a feature gains a second adapter (for example a message consumer, or a different store), that feature can introduce a port interface at that point without restructuring the others.
