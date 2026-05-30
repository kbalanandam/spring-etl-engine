# Epic C - Observability and run evidence

## Summary

Epic C covers machine-readable operational evidence for planned runs, executing steps, and finished jobs. It keeps operator-facing visibility and reconciliation behavior aligned with the selected-job runtime contract.

## Scope

This epic is the home for work that:

- emits structured run and step evidence
- rolls step-level counts into meaningful run-level summaries
- improves reconciliation and operator-facing diagnostics from runtime evidence

This epic is **not** the place for full scheduler/control-plane persistence, though it informs that future direction.

## Related backlog items

- [`C1 - Emit machine-readable run summary with scenario, status, and duration`](../backlog-items/etl-core/C1-machine-readable-run-summary.md)
- [`C2 - Complete run-level source / written / rejected count rollup`](../backlog-items/etl-core/C2-run-level-count-rollup-and-reconciliation.md)

## Related docs

- [`../../architecture/etl-core/runtime-flow.md`](../../architecture/etl-core/runtime-flow.md)
- [`../../architecture/control-plane/job-history-and-operational-observability.md`](../../architecture/control-plane/job-history-and-operational-observability.md)

## Maintenance note

Use [`../product-backlog.md`](../product-backlog.md) for live board tracking. Use this page for the shared observability intent across the Epic C items.

