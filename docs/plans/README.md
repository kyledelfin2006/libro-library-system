# Implementation plans

Active proposals and their execution steps are kept here. The [Roadmap](../reference/ROADMAP.md) tracks their status; live behavior is documented in the code, tests, and [Engineering Reference](../reference/engineering-reference.md).

| Plan | Scope | Status |
| --- | --- | --- |
| [UI implementation](implementation-plan-ui.md) | Web interface | Proposed |
| [Backend additions](implementation-plan-backend.md) | API changes needed by the UI | Proposed |
| [OCI idle reclamation](implementation-plan-oci-idle-reclamation.md) | Production VM idle mitigation | Proposed |

The earlier pre-UI plan is complete and preserved in Git history. The layered feature-package decision (formerly ADR 0002) lives in [Architecture: Design Rules](../architecture.md#design-rules). Earlier OCI deployment work (formerly ADR 0001) is implemented through Stage 2; its remaining items are tracked in the [Roadmap](../reference/ROADMAP.md) and [Operations Runbook](../devops/runbook.md).
