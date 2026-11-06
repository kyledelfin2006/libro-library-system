# 0001 — Pre-OCI deployment improvements

Status: accepted (Stages 0–2 implemented; Stage 3 open)

## Context

Libro's API, schema, tests, and CI are in place, but nothing is deployed. The target is one OCI Always Free Ampere A1 VM running Docker Compose with Caddy and PostgreSQL ([CI/CD and DevOps](../docs/devops/ci-cd.md)). A review of the pipeline, Dockerfile, and Compose stack found no deploy path, no backups, no smoke test, and several settings that were safe locally but unsafe on a public host.

## Goals

- Make the first deployment safe, repeatable, and reversible.
- Keep every secret out of the repository and off the public internet.
- Give recruiters a working demo: patron logins, synthetic data, and Swagger UI.
- Keep the CI workflow; extend it, don't replace it.

## Non-goals

- High availability, autoscaling, a managed database, or Kubernetes.
- A browser UI (planned after the first deploy as a separate project).
- New API features, refresh tokens, or JWT revocation.

## Decisions

| Question | Decision |
| --- | --- |
| Staff accounts on the live demo | The librarian and the seeded `0000-0001` administrator get strong, unique secrets stored only on the VM. The old `Librarian@0000` / `Assistant@0001` passwords stay documented for local use only. |
| Public demo logins | A seeded demo student and demo faculty account with no staff powers; their password is published on purpose. |
| Idle reclaim | Document Pay As You Go with a budget alert, staying inside Always Free limits. |
| Hostname | A free DNS name tied to the VM's public IP, set through `LIBRO_DOMAIN`. |
| Swagger UI | Enabled on the demo instance only; the template default stays `false`. |

## Recommendations

| Area | Recommendation | Rationale | Effort | Priority |
| --- | --- | --- | --- | --- |
| Config | `.env.example` defaults `LIBRO_API_DOCS_ENABLED=false` | Copying the template must not expose Swagger | S | Before deploy |
| Compose | Log rotation, memory limit for `db`, image digests, `ExitOnOutOfMemoryError` | Bounded disk and memory; reproducible images; the restart policy can recover an OOM | S | Before deploy |
| Production override | `infra/docker-compose.prod.yml`: required domain, native forwarded headers, database on a dedicated block volume, no published database port | Removes unsafe defaults and keeps data off the boot volume | M | Before deploy |
| Backups | `scripts/backup.sh` and `restore.sh` with Object Storage upload and a rehearsed restore | A single VM is a single point of failure | M | Before deploy |
| Deploy and rollback | `scripts/deploy.sh` records the previous image, backs up, deploys, smoke-tests, and rolls back on failure | Roll back an application without a manual runbook | M | Before deploy |
| Smoke test | `scripts/smoke.sh` checks `/readyz`, login, and an authenticated read | `/readyz` alone only proves database connectivity | M | Before deploy |
| Demo data | `scripts/seed-demo.sh` creates demo patrons and synthetic books | A demo needs something to try | S | Before deploy |
| Runbook | `docs/devops/runbook.md` covers provisioning, firewall, DNS, secrets, backups, and recovery | Anyone can rebuild the host | M | Before deploy |
| Image updates | Dependabot `docker-compose` ecosystem | Digest pins need update PRs | S | Before deploy |
| Delivery | Build and scan `linux/arm64` on a free ARM runner, publish by SHA, approved deploy job | Same artifact tested and deployed | L | After first deploy |
| CI | Run unit tests once, cache Docker layers and Trivy, group Dependabot, dependency review | Faster, cheaper runs | S–M | After first deploy |
| Tests and docs | V3→V4 upgrade test; browser review of Swagger output | Open roadmap items | M | After first deploy |
| UI | Separate SPA hosted on free static hosting | Recruiter-visible | L | Nice to have |
| Secrets | Move from a `0600` `.env` to OCI Vault | Removes plaintext at rest | M | Nice to have |

## Sequenced rollout

1. **Before first deploy (this change):** Stages 0–1 above. Provision the VM by hand, run `scripts/deploy.sh`, and rehearse a backup, restore, and rollback.
2. **After first deploy:** automate delivery (Stage 2), then CI polish and the open tests (Stage 3). Switch to automatic deploys only after a rollback has been rehearsed on the live host.
3. **Nice to have:** the UI, OCI Vault, and image provenance (SBOM, signing).

## Risks and open questions

- **Idle reclaim and capacity.** Oracle can reclaim an idle A1 VM or have no A1 capacity. Pay As You Go reduces the first risk; capacity needs retries across availability domains. Verify current eligibility before provisioning.
- **Untested on OCI.** The scripts are tested against a local Docker stack, not an OCI host. The first provisioning is the real test.
- **Public demo credentials.** Demo patron accounts are published by design. They hold no staff rights, and their data is synthetic and resettable from the seed script.
- **Seeded administrator.** Flyway V2 always creates `0000-0001`. Production gives it a unique hash; changing that is a separate migration decision.
- **Open:** which branch-protection rules `main` requires (verify in repository settings).
- **Resolved:** Stage 2 deploys over SSH with a dedicated key and a pinned host key, not OCI workload identity, because the pipeline needs no OCI API access.
