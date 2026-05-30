# T3 - Add conditional transformation rule support

## Summary

Add a conditional transformation slice so jobs can apply mapping or cleanup behavior only when configured conditions match the current record.

## Current board status

- Epic: **[Epic T](../../epics/epic-t-transformation-capability.md)**
- Priority: **P1**
- Status: **Done**
- Milestone: **M2**
- Dependency: **T2**

> Keep these fields synchronized with the row in [`product-backlog.md`](../../product-backlog.md). The execution board remains the canonical source for changing status values.

Use the linked `Epic` entry above to navigate to the shared epic-level product context for this backlog item.

## Problem

Expression support improves derivation, but many real jobs also need condition-based mapping or cleanup choices that remain readable to config authors.

## Goal

Introduce one explicit conditional-processing contract after expression semantics are stable.

## Scope

- conditional transform/rule contract on the processor path
- readable config for record-level condition checks
- behavior that composes with the existing transform-before-validation order

## Out of scope

- general scripting engine
- source-side branching orchestration
- scheduler/control-plane logic

## Proposed approach

Add conditional behavior only after the expression baseline is stable, and keep it within the active processor path rather than introducing a separate rule engine.

## Operator / runtime impact

- jobs could express conditional value handling without custom code
- reviewers need one clear condition syntax and execution order
- docs/tests must explain how conditions interact with transforms and validation

## Acceptance criteria

- [x] one documented conditional-processing contract exists on the processor path
- [x] condition evaluation composes predictably with existing transform/validation order
- [x] focused tests cover match, non-match, and validation/error behavior

## Related docs

- [`Product backlog`](../../product-backlog.md)
- [`Transformation capability roadmap`](../../../architecture/etl-core/transformation-capability-roadmap.md)
- [`Default processor config`](../../../config/processor/default-processor.md)

## Implementation notes

Delivered on the processor transform seam with ordered `cases[]` first-match behavior and optional `defaultValue` fallback.

## Status notes

Shipped after the expression baseline (`T2`) stabilized; next transformation hardening focus moves to `T4`.


