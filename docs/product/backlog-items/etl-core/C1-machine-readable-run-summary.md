# C1 - Emit machine-readable run summary with scenario, status, and duration

## Summary

Add one machine-readable run summary baseline so each selected job emits a concise operator-facing record of what ran, whether it finished, and how long it took.

## Current board status

- Epic: **[Epic C](../../epics/epic-c-observability-and-run-evidence.md)**
- Priority: **P1**
- Status: **Done**
- Milestone: **M1**
- Dependency: **none**

> Keep these fields synchronized with the row in [`product-backlog.md`](../../product-backlog.md). The execution board remains the canonical source for changing status values.

Use the linked `Epic` entry above to navigate to the shared epic-level product context for this backlog item.

## Problem

Without a stable run-level summary, operators had to infer overall job outcome from scattered lifecycle logs.

## Goal

Emit one structured run-level evidence surface with scenario identity, status, and duration.

## Scope

- machine-readable run summary events
- stable scenario/status/duration reporting
- run-level evidence that complements step lifecycle events

## Out of scope

- full retained run-history persistence
- richer reconciliation rollups beyond the initial baseline
- HTML reporting by itself

## Proposed approach

Add run-level evidence directly on the active runtime path so logs and later reporting can build on one shared baseline.

## Operator / runtime impact

- operators gain a clearer end-of-run summary
- reporting and verification can consume one shared run-level outcome surface
- future observability features have a stronger baseline

## Acceptance criteria

- [x] selected runs emit machine-readable run summary evidence
- [x] run summary includes scenario identity, status, and duration
- [x] step lifecycle evidence remains aligned with the run-level summary surface

## Related docs

- [`Product backlog`](../../product-backlog.md)
- [`Runtime flow`](../../../architecture/etl-core/runtime-flow.md)
- [`Job history and operational observability`](../../../architecture/control-plane/job-history-and-operational-observability.md)

## Implementation notes

C1 established the observability baseline that later C2, D1, V1, and scheduler-facing work depend on.

## Status notes

Shipped baseline: `RUN_EVENT` / `RUN_SUMMARY` now provide the machine-readable run-level view.

