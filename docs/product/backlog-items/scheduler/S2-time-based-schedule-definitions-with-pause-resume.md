# S2 - Add time-based schedule definitions with pause/resume controls

## Summary

Add the first practical built-in scheduling slice so jobs can be configured to run on time-based schedules with explicit pause/resume controls.

## Current board status

- Epic: **[Epic S](../../epics/scheduler/epic-s-scheduling-and-control-plane.md)**
- Priority: **P1**
- Status: **Deferred**
- Milestone: **M2**
- Dependency: **S1**

> Keep these fields synchronized with the row in [`product-backlog.md`](../../product-backlog.md). The execution board remains the canonical source for changing status values.

Use the linked `Epic` entry above to navigate to the shared epic-level product context for this backlog item.

## Problem

The repo preserves scheduling direction, but there is no native schedule definition baseline yet for teams that want built-in triggering.

## Goal

Introduce one time-based schedule-definition slice that remains optional and does not replace the explicit ETL worker contract.

## Scope

- time-based schedule definitions
- pause/resume controls
- alignment with the optional control-plane boundary

## Out of scope

- overlap or missed-run policy by itself
- retained operational data model implementation
- making native scheduling mandatory

## Proposed approach

Build S2 only after S1 freezes the schedule/trigger contract, and keep the ETL worker launch contract unchanged underneath.

## Operator / runtime impact

- teams gain a first native scheduling slice if they want it
- operators need clear pause/resume state and trigger evidence
- external schedulers should remain equally valid

## Acceptance criteria

- [ ] time-based schedule definitions are documented and implemented for the first slice
- [ ] pause/resume controls are explicit and observable
- [ ] built-in scheduling remains optional rather than mandatory for normal ETL use

## Related docs

- [`Product backlog`](../../product-backlog.md)
- [`Control-plane worker boundary`](../../../architecture/control-plane/control-plane-worker-boundary.md)
- [`Control-plane operational data model`](../../../architecture/control-plane/control-plane-operational-data-model.md)

## Implementation notes

Do not let S2 redefine the worker contract; it should trigger the same selected-job path.

## Status notes

Deferred until S1 stabilizes the schedule model and trigger contract.

