# T1a - Define processor transform SPI and first cleaner/normalization slice

## Summary

Add an explicit processor transform seam so record cleanup and normalization happen in one ordered contract before validation and write behavior.

## Current board status

- Epic: **[Epic T](../../epics/etl-core/epic-t-transformation-capability.md)**
- Priority: **P1**
- Status: **Done**
- Milestone: **M2**
- Dependency: **T1**

> Keep these fields synchronized with the row in [`product-backlog.md`](../../product-backlog.md). The execution board remains the canonical source for changing status values.

Use the linked `Epic` entry above to navigate to the shared epic-level product context for this backlog item.

## Problem

The product needed one clear place for cleanup and normalization without overloading validation rules or pushing business cleanup into source-specific adapters.

## Goal

Establish ordered processor-side `transforms[]` as the active cleanup extension seam.

## Scope

- processor transform SPI and ordered transform execution
- first normalization/cleanup slice through shipped transform types
- clear transform-before-validation behavior

## Out of scope

- source-native transformation redesign
- full conditional logic system
- reference-set enrichment

## Proposed approach

Run transforms between read and validation on the processor path, keeping cleanup and validation as separate concerns.

## Operator / runtime impact

- config authors gain a clearer cleanup contract
- validation runs on already-normalized values
- extension guidance becomes easier to document and test

## Acceptance criteria

- [x] processor-side transform SPI is established on the active path
- [x] transforms run before validation rules
- [x] first cleaner/normalization slice ships without redefining source contracts

## Related docs

- [`Product backlog`](../../product-backlog.md)
- [`Default processor config`](../../../config/processor/default-processor.md)
- [`Extension points`](../../../architecture/etl-core/extension-points.md)
- [`ADR 0007`](../../../adr/etl-core/0007-add-separate-processor-transform-spi-for-cleaning-and-normalization.md)

## Implementation notes

This item intentionally separated cleanup from validation so future transform growth could stay composable.

## Status notes

Shipped baseline: ordered `transforms[]` now run before validation on the active processor path.



