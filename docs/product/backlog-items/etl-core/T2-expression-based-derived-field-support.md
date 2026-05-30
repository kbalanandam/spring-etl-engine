# T2 - Add expression-based derived field support

## Summary

Add processor-side expressions so mappings can derive new target values from source data without requiring custom per-job Java code.

## Current board status

- Epic: **[Epic T](../../epics/etl-core/epic-t-transformation-capability.md)**
- Priority: **P1**
- Status: **Done**
- Milestone: **M2**
- Dependency: **T1a**

> Keep these fields synchronized with the row in [`product-backlog.md`](../../product-backlog.md). The execution board remains the canonical source for changing status values.

Use the linked `Epic` entry above to navigate to the shared epic-level product context for this backlog item.

## Problem

Simple one-to-one mappings were not enough for many practical jobs that need derived values, constants, or computed fields.

## Goal

Support expression-driven derived fields inside the active processor transform contract.

## Scope

- expression transform support on the processor path
- derived target fields that may omit a physical `from` field when expression is primary
- focused config and runtime validation for invalid expressions

## Out of scope

- full conditional rule language
- lookup/enrichment joins
- source-level scripting systems

## Proposed approach

Keep expressions inside processor transforms so derivation stays explicit, ordered, and testable within the existing processor contract.

## Operator / runtime impact

- jobs can derive fields without custom Java
- invalid expressions fail earlier and more clearly
- mapping docs/examples can cover richer target derivation

## Acceptance criteria

- [x] processor-side expression transforms are supported
- [x] derived fields can omit `from` when expression is first
- [x] invalid expressions fail fast through focused validation/tests

## Related docs

- [`Product backlog`](../../product-backlog.md)
- [`Default processor config`](../../../config/processor/default-processor.md)
- [`Transformation capability roadmap`](../../../architecture/etl-core/transformation-capability-roadmap.md)

## Implementation notes

T2 builds on T1a rather than introducing a second derivation seam.

## Status notes

Shipped baseline: `transforms[].type: expression` is now part of the active processor contract.



