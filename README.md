<h1 align="center">Libro - Library Management System</h1>

<p align="center">
  <img src="assets/LIBRO_LOGO.png" alt="Libro system logo" width="180">
</p>

![Java](https://img.shields.io/badge/Java-25-orange?logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1.2-6DB33F?logo=springboot&logoColor=white)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-18-336791?logo=postgresql&logoColor=white)
![Docker](https://img.shields.io/badge/Docker-Containerized-2496ED?logo=docker&logoColor=white)
[![CI](https://github.com/kyledelfin2006/libro-library-system/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/kyledelfin2006/libro-library-system/actions/workflows/ci.yml)

## What this is

Libro is a Spring Boot REST API for the library of Aklan State University – College of Computer Studies (ASU-CCS). It manages physical book copies, user accounts, and loan history. Each catalog record is one physical copy, which keeps loan history accurate per copy (see [Domain Decisions](docs/guides/domain-decisions.md)).

- **Catalog:** search, filter, sort, and paginate copies by title, author, genre, or replacement cost; collection and genre statistics.
- **Loans:** students and faculty borrow for 14 days, up to three active loans each; overdue loans block new checkouts. See the [Loan Implementation Guide](docs/guides/loan.md).
- **Accounts and roles:** public signup creates students; librarians create faculty and library-assistant accounts. Four roles (`STUDENT`, `FACULTY`, `ADMINISTRATOR`, `LIBRARIAN`) are enforced at the route and service layers.
- **Security:** stateless HS256 JWT Bearer authentication, BCrypt passwords, per-IP rate limits on login and signup, and a uniform JSON error contract. See the [JWT Authentication Design](docs/reference/jwt-authentication-design.md).
- **Operations:** Flyway-owned schema, Docker Compose, public `/livez` and `/readyz` probes, and Prometheus metrics on a private management port.

| Layer | Technology |
| --- | --- |
| Language and framework | Java 25, Spring Boot 4.1.2 (Web, Data JPA, Security, OAuth2 Resource Server, Validation) |
| Data | PostgreSQL 18, Flyway 13.3.0, Hibernate with schema validation |
| Build and test | Maven, JUnit 5, Mockito, Testcontainers, JaCoCo |
| Delivery | Docker, Docker Compose, Caddy, GitHub Actions, Trivy |

My team, **CyberGuild**, placed among the top five teams in the Hack4Gov 5 Regional Qualifying Round, which focused on ethical hacking and cybersecurity principles. Main developer: **Aldrin Kyle Delfin**.

## Run locally

Requires Docker with Compose. The stack builds the API from source and starts PostgreSQL.

1. Create the environment file and fill in the required values:

   ```powershell
   Copy-Item .env.example .env
   ```

   Set the PostgreSQL credentials, `LIBRO_ADMIN_PASSWORD_HASH` (a BCrypt hash of a password you choose), and `LIBRO_JWT_SECRET_BASE64` (`openssl rand -base64 32`). `.env` is git-ignored.

2. Start the stack:

   ```bash
   docker compose up --build
   ```

3. Check readiness at `http://localhost:8080/readyz`, then log in with `POST /app/auth/login` and send the returned token as `Authorization: Bearer <token>`.

Swagger UI and `/v3/api-docs` are off unless `LIBRO_API_DOCS_ENABLED=true`, which you can set in your local `.env` (the template leaves it `false`). To run the app outside Docker, enable the TLS proxy profile, or provision the first librarian, see [Local Setup and Configuration](docs/guides/local-setup.md). Run the tests with `./mvnw verify`; add `-Pintegration` for the PostgreSQL suite, which needs Docker.

## How it deploys

Every pull request and every push to `main` runs three parallel GitHub Actions checks:

| Check | What it proves |
| --- | --- |
| `test` | Unit and MVC tests, with an 80% line-coverage floor |
| `integration` | Flyway migrations, schema validation, and loan concurrency against PostgreSQL 18 |
| `security` | Trivy finds no fixable HIGH or CRITICAL issues in dependencies or the container image |

The production target is a single **OCI Always Free Ampere A1 VM** running the API, PostgreSQL, and a Caddy TLS proxy under Docker Compose. After CI passes on `main`, the CD workflow builds and scans one immutable ARM64 image on a native ARM runner, publishes it to GitHub Container Registry by commit SHA, and deploys that digest through an approved `production` environment once the VM exists. `scripts/deploy.sh` already does the deploy step by hand: it backs up the database, starts the new image, runs a smoke test (readiness, login, authenticated read, anonymous rejection), and restarts the previous image if anything fails. `backup.sh`, `restore.sh`, and `seed-demo.sh` cover recovery and demo data. The pipeline stages, environments, secrets handling, rollback, and monitoring are in [CI/CD and DevOps](docs/devops/ci-cd.md); provisioning steps are in the [Operations Runbook](docs/devops/runbook.md).

**The service is not deployed yet:** the scripts were rehearsed against a local Docker stack and the image-publishing workflow is in place, but no VM is provisioned, so the deploy job is switched off.

## Repo map

```text
src/             Spring Boot application (controllers, services, repositories, Flyway migrations) and tests
infra/           Production Compose override and the Caddy reverse-proxy configuration
scripts/         Deploy, rollback, smoke-test, backup, restore, and demo-seed scripts
proposals/       Design proposals (0001: pre-OCI deployment improvements)
docs/
  architecture.md  Design, request flows, API inventory, data model, troubleshooting, tests
  devops/          CI/CD, environments, secrets, rollback, monitoring, operations runbook
  guides/          Domain decisions, loan guide, local setup, institutional context, API documentation standard
  reference/       Engineering reference, JWT design, role policy, roadmap
.github/         CI workflow and Dependabot configuration
Dockerfile, docker-compose.yml, pom.xml, mvnw   Build and runtime entry points
AGENTS.md        Contributor and AI-agent guide
```

Start with [Architecture and Reference](docs/architecture.md) for how the system works, the [Engineering Reference](docs/reference/engineering-reference.md) for contracts and configuration, and [Development Problems Solved](docs/guides/development-problems-solved.md) for the engineering decisions behind it. [Institutional Context](docs/guides/institutional-context.md) explains the ASU-CCS user domain.

## Status

| Area | State |
| --- | --- |
| Catalog, accounts, roles, JWT authentication, loans | Implemented and covered by unit, MVC, and PostgreSQL tests |
| CI (`test`, `integration`, `security`) | Running on every pull request and push to `main` |
| Container image and Compose stack | Implemented, with health checks |
| Deploy, rollback, backup, and smoke-test scripts; runbook | Implemented and rehearsed locally |
| CD workflow (ARM64 build, scan, publish, gated deploy) | Implemented; deploy job off until the VM exists |
| OCI VM | Not provisioned; see the [proposal](proposals/0001-pre-oci-deployment-improvements.md) |
| Open items | V3-to-V4 migration upgrade test on PostgreSQL; browser review of the OpenAPI/Swagger output; TLS with a real domain, login-failure and rate-limit alerts, and an independent security review before public exposure |

The [Development Roadmap](docs/reference/ROADMAP.md) tracks the detail.

## License

This project is licensed under the [MIT License](LICENSE).
