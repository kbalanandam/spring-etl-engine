# Product epic pages

Use this folder for epic-level product pages that sit between the execution board in [`../product-backlog.md`](../product-backlog.md) and the item-level drill-down pages under [`../backlog-items/`](../backlog-items/).

## Purpose

These pages exist to keep shared product intent in one place when multiple backlog items belong to the same capability track.

Use an epic page for:

- the shared problem statement behind a group of backlog items
- the scope boundary for the capability track
- related backlog items that should be read together
- links to architecture notes or ADRs that span more than one backlog item

Do **not** use epic pages as a second execution board.

The canonical place for changing item-level `Priority`, `Status`, `Milestone`, and `Dependency` remains [`../product-backlog.md`](../product-backlog.md).

Epic pages should also link back to the matching backlog item pages so the navigation stays two-way.

## Epic index

- [`Epic A — Runtime contract and generated-model governance`](epic-a-runtime-contract-and-model-governance.md)
- [`Epic T — Transformation capability`](epic-t-transformation-capability.md)
- [`Epic B — Runtime hardening and file behavior`](epic-b-runtime-hardening-and-file-behavior.md)
- [`Epic P — Source-native parser maturity`](epic-p-source-native-parser-maturity.md)
- [`Epic C — Observability and run evidence`](epic-c-observability-and-run-evidence.md)
- [`Epic D — Error taxonomy and failure categorization`](epic-d-error-taxonomy-and-failure-categorization.md)
- [`Epic E — Portability and packaged-run guidance`](epic-e-portability-and-packaged-run-guidance.md)
- [`Epic F — Restartability and recovery semantics`](epic-f-restartability-and-recovery-semantics.md)
- [`Epic G — Secret injection and secure configuration`](epic-g-secret-injection-and-secure-configuration.md)
- [`Epic S — Scheduling and control plane`](epic-s-scheduling-and-control-plane.md)
- [`Epic V — Verification evidence and reporting`](epic-v-verification-evidence-and-reporting.md)
- [`Epic X — File transport and SFTP boundary`](epic-x-file-transport-and-sftp-boundary.md)

## Maintenance rule

When you add or re-scope backlog items:

1. update [`../product-backlog.md`](../product-backlog.md) first
2. update the matching epic page only if the shared epic-level intent or boundary changed
3. update item pages under [`../backlog-items/`](../backlog-items/) only when item-specific scope or acceptance criteria changed

