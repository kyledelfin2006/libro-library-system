# CI/CD and DevOps

This page describes how Libro validates proposed changes and what is in place for container operations. It distinguishes the automated continuous-integration workflow from deployment automation: **CI is implemented; the GitHub Actions workflow does not currently publish or deploy the application.**

## Delivery pipeline

The workflow is [`.github/workflows/ci.yml`](../../.github/workflows/ci.yml). It uses GitHub-hosted Ubuntu runners and has two triggers:

| Event | What starts the workflow |
| --- | --- |
| `pull_request` | Runs when a pull request is opened, updated with new commits, or reopened. There is no base-branch filter, so PRs into any target branch are covered. A push to a branch with no open PR does not trigger this event. |
| `push` to `main` | Runs after each push event to `main`, including a merge that updates `main`. Direct pushes to other branches do not match this trigger. |

When a source branch has an open PR, pushing commits to it updates the PR and starts a new run. The `concurrency` setting cancels an older in-progress run for the same PR or branch when a newer run starts. The three jobs run independently and can execute in parallel.

## Quality and security gates

| Job | Timeout | What it checks |
| --- | ---: | --- |
| `test` | 15 minutes | Runs `./mvnw -B -ntp verify` for the Docker-free unit and MVC suite, Maven verification, and the JaCoCo 80% line-coverage floor. Uploads test and coverage reports. |
| `integration` | 25 minutes | Runs `./mvnw -B -ntp -Pintegration verify`, activating the Failsafe PostgreSQL integration suite with Testcontainers. This Maven lifecycle also runs the regular unit/MVC tests. Uploads Surefire, Failsafe, and coverage reports. |
| `security` | 20 minutes | Uses Trivy to scan dependency manifests and the built application image. It fails on fixable HIGH or CRITICAL vulnerabilities; findings without an available fix are ignored. |

The integration profile uses PostgreSQL 18 through Testcontainers, so the CI runner must provide Docker. The security job builds the production Dockerfile as `libro-api:ci` before scanning it. That image stays on the runner: the workflow does not push it to a registry.

Test report artifacts are retained for 14 days. Each job has its own timeout, so the limits are not added together; overall elapsed time is driven by the slowest job plus runner startup and queue time. If any job fails, the workflow fails.

## Runner and workflow controls

- Workflow permissions are limited to `contents: read`; CI has no deployment credentials.
- Java 25 is installed from Temurin, with Maven dependency caching enabled.
- Third-party GitHub Actions are pinned to commit SHAs. Trivy runs from its versioned official container image.
- Superseded runs for a matching ref are canceled to avoid spending runner time on stale commits.
- Trivy ignores unfixed findings. Accepted, time-bounded exceptions are recorded in [`.trivyignore`](../../.trivyignore) with an expiry comment.

The workflow reports check results to GitHub. Requiring `test`, `integration`, and `security` before merging is a separate repository branch-protection or ruleset setting; it is not configured by this workflow file. The engineering reference recommends requiring all three checks for `main`.

## Container operations

The repository includes a multi-stage [Dockerfile](../../Dockerfile) that compiles with JDK 25 and runs the packaged application on a JRE-only Alpine image as an unprivileged user. The image defines a readiness health check. [Docker Compose](../../docker-compose.yml) runs PostgreSQL with a persistent named volume, waits for database health before starting the API, and checks the API readiness endpoint.

An optional Caddy profile terminates TLS and keeps the API and database behind the proxy. It is a local/reference deployment setup, not a hosted production deployment. The app and database bind to loopback in the default Compose configuration; only the optional proxy binds public HTTP/HTTPS ports.

## CI versus CD

CI is the automated verification described above. The current workflow neither publishes a release artifact nor deploys to a server. Adding continuous delivery or deployment requires choosing the hosting target and container registry, setting up protected deployment environments and credentials, and deciding how to promote an immutable image and handle health checks, database migrations, and rollback.

The internal roadmap also records operational work before public exposure: deploy behind TLS with a real domain, alert on login-failure and rate-limit metrics, add gateway-level throttling before scaling beyond one instance, and complete an independent security review. See the [Development Roadmap](../../guiding-docs/TODO.md) for the current status.
