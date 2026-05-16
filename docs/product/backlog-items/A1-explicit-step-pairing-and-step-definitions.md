# A1 — Replace positional source-target pairing with explicit step pairing or step definitions

## Summary

Replace implicit source-target pairing by list position with one explicit step contract so selected jobs declare the exact ordered `source -> processor -> target` plan the runtime should execute.

## Current board status

- Epic: **[Epic A](../epics/epic-a-runtime-contract-and-model-governance.md)**
- Priority: **P0**
- Status: **Done**
- Milestone: **M1**
- Dependency: **none**

> Keep these fields synchronized with the row in [`product-backlog.md`](../product-backlog.md). The execution board remains the canonical source for changing status values.

Use the linked `Epic` entry above to navigate to the shared epic-level product context for this backlog item.

## Problem

Positional pairing was fragile. Adding, reordering, or partially reusing source and target entries could change runtime behavior without one explicit operator-facing step contract.

## Goal

Make step order and source/target pairing explicit in `job-config.yaml` so one selected job run is deterministic and reviewable.

## Scope

- explicit `steps[]` definitions in `job-config.yaml`
- ordered execution from named source/processor/target bindings
- fail-fast validation when referenced step components are missing

## Out of scope

- scheduler/control-plane concerns
- inferred multi-flow runtime redesign
- richer transformation behavior

## Proposed approach

Keep one flat ordered Spring Batch plan, but require each runnable selected job to declare that plan explicitly through named steps.

## Operator / runtime impact

- startup becomes clearer because the run plan is explicit
- step order is no longer inferred from list position
- preserved examples and tests can prove orchestration directly

## Acceptance criteria

- [x] selected jobs declare execution order through explicit `steps[]`
- [x] runtime resolves step bindings by configured names instead of positional pairing
- [x] missing or invalid step references fail fast before execution starts

## Related docs

- [`Product backlog`](../product-backlog.md)
- [`Job config reference`](../../config/job-config.md)
- [`Runtime flow`](../../architecture/runtime-flow.md)

## Implementation notes

This item established the selected-job execution contract that later orchestration, naming, and validation work now builds on.

## Status notes

Shipped baseline: explicit `steps` orchestration is now the active contract for preserved selected-job runs.

