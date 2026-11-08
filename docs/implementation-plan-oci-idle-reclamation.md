# Implementation Plan: OCI Always Free Idle Mitigation

Status: proposed, not built. This plan changes the production Docker Compose stack only. Libro's local stack and application code stay as they are.

## Goal and limit

Keep the Libro demo reachable on an OCI Always Free A1 VM without upgrading to Pay As You Go or using billable resources. A quiet portfolio API may qualify as idle. A Compose service that generates bounded CPU activity is the proposed mitigation. It cannot guarantee continuous availability: Oracle may still reclaim the VM, free A1 capacity may be unavailable, and other outages can occur. Do not claim an uptime guarantee.

As checked on 2026-11-09, [Oracle's Always Free documentation](https://docs.oracle.com/en-us/iaas/Content/FreeTier/freetier_topic-Always_Free_Resources.htm) says an instance may be deemed idle when **all** listed conditions hold over seven days: CPU utilization at the 95th percentile below 20%, network utilization below 20%, and, for A1, memory utilization below 20%. Recheck the policy and Oracle's terms for deliberate CPU activity before implementation. If this approach is prohibited or the policy changes materially, stop and revise the plan.

## The fix

Add one `keepalive` service to [`infra/docker-compose.prod.yml`](../infra/docker-compose.prod.yml). The production override is already loaded by `scripts/common.sh` and `scripts/deploy.sh`, so the service starts with the existing deploy command. It does not appear in ordinary local `docker compose up`. Do not add a second Compose file, custom image, script, profile, flag, or application scheduler for this fix.

Use an official Alpine image pinned to a verified ARM64-compatible digest. Its Compose `command` uses BusyBox `timeout`, `yes`, and `sleep`: run one CPU worker for 720 seconds, then sleep for 2880 seconds, and repeat. This is a 20% duty cycle. On the planned two-OCPU VM, one fully busy worker should produce roughly 50% host CPU during the active period; verify OCI's actual measurement before relying on it. Keep these two durations fixed in Compose initially. Tune them only if the seven-day OCI metric shows insufficient margin or the app is affected.

Set the service's resource and security boundaries in Compose: `cpus: "1.0"`, low `cpu_shares`, `mem_limit`, `pids_limit`, `user: "65534:65534"`, `read_only: true`, `cap_drop: [ALL]`, `security_opt: [no-new-privileges:true]`, `network_mode: none`, and `restart: unless-stopped`. Copy the base file's small log-rotation settings into this service; YAML anchors cannot cross Compose files. Do not add ports, mounts, secrets, or a dependency on `app` or `db`. Keep the worker's CPU priority below the API and database; if the load measurably degrades them, stop and report it.

The keep-alive is a production-only operational service. It must not gate app health, deployment, rollback, or database backup. `docker compose ... stop keepalive` is the emergency stop; the next deploy will start it again. A permanent disable requires removing the service from the production override.

## Implementation and checks

1. Read [`AGENTS.md`](../AGENTS.md), the [engineering reference](reference/engineering-reference.md), the [runbook](devops/runbook.md), and the existing Compose and deploy files. Verify the current Oracle policy, terms, image tag and digest, and that the proposed VM and storage remain within Always Free allowances. No production or account action is part of this implementation.
2. Edit only `infra/docker-compose.prod.yml` for the runtime fix. Keep its existing comments and style. Avoid Compose interpolation in the inline shell command, or escape any literal `$` as `$$`. The service should be present when the production override is loaded and absent from the base local stack.
3. Run `docker compose -f docker-compose.yml -f infra/docker-compose.prod.yml config` with the required nonsecret placeholder values to check the merged configuration. Confirm the `keepalive` service has no ports, network, mounts, or secrets, and that the base Compose configuration does not contain it.
4. On a local Docker host, start the service using the merged files. Confirm it consumes no more than its CPU and memory limits, stops cleanly, and resumes after a normal restart. If the local API stack is available, run its existing smoke check during an active CPU period and compare with an idle period. Do not delete volumes. Report exactly what was and was not measured.
5. Update the [runbook](devops/runbook.md) with the one-command deployment behavior, `docker compose ... ps keepalive`, `docker compose ... logs keepalive`, the emergency stop command, and how to read OCI's seven-day CPU 95th percentile. Replace the current Pay As You Go recommendation, which conflicts with the user's zero-payment constraint. Update the [engineering reference](reference/engineering-reference.md) and [roadmap](reference/ROADMAP.md) only for facts that changed. Leave `.env.example`, `AGENTS.md`, and README alone unless their owned facts require an update.
6. After the user authorizes deployment to a provisioned VM, observe OCI CPU utilization for a full seven-day window. Record the 95th percentile and whether the API remains healthy during active periods. If CPU does not clear the threshold with margin, or the app suffers, adjust only the two Compose durations or stop the approach and report the result. Until this check is complete, describe the fix as **designed to reduce idle reclamation**, not verified to prevent it.

## Done when

- One production Compose service provides bounded CPU activity and runs through the existing deployment path; local development requires no new step.
- The merged Compose configuration and local resource checks pass, and the API is not measurably degraded.
- The runbook explains operation and emergency shutdown in a few commands, with no paid path.
- OCI results, when available, are recorded honestly. No documentation promises uninterrupted availability on Always Free.

Never run `docker compose down -v` or change PostgreSQL storage while carrying out this plan. Commit only if the user asks.
