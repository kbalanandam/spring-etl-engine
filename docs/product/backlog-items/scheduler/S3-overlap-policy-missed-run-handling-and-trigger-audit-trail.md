# S3 - Add overlap policy, missed-run handling, and basic trigger audit trail

## Summary

Add the next scheduler-credibility slice after basic schedule definitions so native scheduling can explain overlap decisions, missed-run behavior, and trigger history.

## Current board status

- Epic: **[Epic S](../../epics/scheduler/epic-s-scheduling-and-control-plane.md)**
- Priority: **P1**
- Status: **Deferred**
- Milestone: **M3**
- Dependency: **S1, S2, F1**

> Keep these fields synchronized with the row in [`product-backlog.md`](../../product-backlog.md). The execution board remains the canonical source for changing status values.

Use the linked `Epic` entry above to navigate to the shared epic-level product context for this backlog item.

## Problem

A native scheduler is not enterprise-credible if it cannot explain what happens when runs overlap, a scheduled time is missed, or operators need a trigger history.

## Goal

Define overlap policy, missed-run handling, and basic trigger audit behavior for the control-plane direction.

## Scope

- overlap policy options
- missed-run handling rules
- basic trigger audit trail

## Out of scope

- full persisted control-plane schema implementation by itself
- worker runtime redesign
- transport-specific trigger behavior

## Proposed approach

Treat S3 as the first scheduler-governance slice after S2, and keep it aligned with F1 restart semantics rather than inventing recovery behavior independently.

### Phase-1 governance baseline (current)

The first S3 baseline keeps existing scheduler behavior as the default while making policy choices explicit:

- overlap policy is now explicit with `controlplane.scheduler.overlap-policy`
  - `ALLOW` (default): process all resolved due instants for the tick (subject to missed-run policy)
  - `SERIALIZE`: process only one due instant per schedule per tick to drain backlog gradually
- missed-run policy remains explicit with `controlplane.scheduler.missed-run-policy`
  - `SKIP` (default): evaluate only the latest due instant within the active lookback window
  - `CATCH_UP_ONCE`: advance to one latest due instant after `lastAcceptedDueAt`
  - `CATCH_UP_ALL`: drain all due instants after `lastAcceptedDueAt` within bounded iteration limits
- trigger audit trail remains on the accepted-trigger path with schedule-origin metadata (`scheduleId`, `reason`, `requestedBy`) and dedup through `lastAcceptedDueAt`

This baseline intentionally does not yet add run-state-aware overlap governance (for example, "skip if prior run still executing").

## Operator / runtime impact

- operators gain clearer scheduling decisions and auditability
- schedule behavior becomes more predictable during contention or downtime
- future control-plane persistence has clearer semantics to encode

## Acceptance criteria

- [x] overlap policy options are defined clearly
- [x] missed-run behavior is documented and observable
- [x] a basic trigger audit trail exists or is explicitly defined for the first native scheduler slice

## Related docs

- [`Product backlog`](../../product-backlog.md)
- [`Control-plane worker boundary`](../../../architecture/control-plane/control-plane-worker-boundary.md)
- [`Scheduler architecture direction`](../../../architecture/control-plane/scheduler-architecture-direction.md)
- [`Control-plane operational data model`](../../../architecture/control-plane/control-plane-operational-data-model.md)

## Implementation notes

S3 should not outrun F1; overlap and missed-run behavior depend on clearer restart/recovery semantics.

The phase-1 baseline keeps this guardrail by implementing tick-time due-instant governance only and deferring run-state semantics to follow-on S3/F1 work.

## Status notes

Phase-1 baseline started: overlap and missed-run policies are now explicit with safe defaults that preserve prior behavior.

