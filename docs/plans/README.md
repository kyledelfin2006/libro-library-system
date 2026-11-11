# Implementation plans

Active proposals and their execution steps are kept here. The [Roadmap](../reference/ROADMAP.md) tracks their status; live behavior is documented in the code, tests, and [Engineering Reference](../reference/engineering-reference.md).

| Plan | Scope | Status |
| --- | --- | --- |
| [UI implementation](implementation-plan-ui.md) | Web interface | Proposed |
| [MVP UX improvements](implementation-plan-mvp-ux.md) | Small client-side usability additions included in the UI phases | Proposed |

**Rule:** when a plan is fully executed, delete its file and its links in the same change; executed plans stay in Git history.

The earlier pre-UI plan and the backend additions plan are complete and preserved in Git history. The layered feature-package decision (formerly ADR 0002) lives in [Architecture: Design Rules](../architecture.md#design-rules). Earlier deployment work (formerly ADR 0001) is implemented through Stage 2; its remaining items are tracked in the [Roadmap](../reference/ROADMAP.md) and [Operations Runbook](../devops/runbook.md).
