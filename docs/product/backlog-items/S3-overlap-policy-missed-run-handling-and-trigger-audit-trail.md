# S3 - Add overlap policy, missed-run handling, and basic trigger audit trail

## Summary

Add the next scheduler-credibility slice after basic schedule definitions so native scheduling can explain overlap decisions, missed-run behavior, and trigger history.

## Current board status

- Epic: **[Epic S](../epics/epic-s-scheduling-and-control-plane.md)**
- Priority: **P1**
- Status: **Deferred**
- Milestone: **M3**
- Dependency: **S1, S2, F1**

> Keep these fields synchronized with the row in [`product-backlog.md`](../product-backlog.md). The execution board remains the canonical source for changing status values.

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

## Operator / runtime impact

- operators gain clearer scheduling decisions and auditability
- schedule behavior becomes more predictable during contention or downtime
- future control-plane persistence has clearer semantics to encode

## Acceptance criteria

- [ ] overlap policy options are defined clearly
- [ ] missed-run behavior is documented and observable
- [ ] a basic trigger audit trail exists or is explicitly defined for the first native scheduler slice

## Related docs

- [`Product backlog`](../product-backlog.md)
- [`Control-plane worker boundary`](../../architecture/control-plane/control-plane-worker-boundary.md)
- [`Scheduler architecture direction`](../../architecture/control-plane/scheduler-architecture-direction.md)
- [`Control-plane operational data model`](../../architecture/control-plane/control-plane-operational-data-model.md)

## Implementation notes

S3 should not outrun F1; overlap and missed-run behavior depend on clearer restart/recovery semantics.

## Status notes

Deferred until schedule basics and restart direction are clearer.

