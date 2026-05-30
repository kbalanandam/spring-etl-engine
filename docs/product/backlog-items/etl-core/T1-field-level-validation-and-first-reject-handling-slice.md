# T1 - Add field-level validation rules and first reject-handling slice for file scenarios

## Summary

Introduce the first production-oriented processor validation slice for file-backed runs, including rule-driven rejection and controlled rejected-record output.

## Current board status

- Epic: **[Epic T](../../epics/epic-t-transformation-capability.md)**
- Priority: **P1**
- Status: **Done**
- Milestone: **M1**
- Dependency: **A1**

> Keep these fields synchronized with the row in [`product-backlog.md`](../../product-backlog.md). The execution board remains the canonical source for changing status values.

Use the linked `Epic` entry above to navigate to the shared epic-level product context for this backlog item.

## Problem

The runtime could map records, but lacked a shared item-level way to reject bad data consistently and emit evidence about what failed.

## Goal

Support first-slice processor-side validation rules and rejected-record handling without reviving the deprecated legacy validation path.

## Scope

- processor validation rules such as `notNull` and `timeFormat`
- rejected-record output for validation-aware CSV flows
- first duplicate-handling baseline through the processor path

## Out of scope

- full quarantine workflow redesign
- source-level legacy validation revival
- advanced enrichment or conditional transforms

## Proposed approach

Keep validation on the active processor seam, emit rejected-record artifacts in the first CSV-focused slice, and preserve extension growth behind the rule SPI.

## Operator / runtime impact

- operators gain clearer rejected-record output
- runtime can separate accepted and rejected records more safely
- docs/tests can describe a shared validation path

## Acceptance criteria

- [x] first processor validation rules are supported on the active path
- [x] rejected records can be emitted for validation-aware CSV runs
- [x] duplicate handling begins on the processor-rule seam rather than legacy validation config

## Related docs

- [`Product backlog`](../../product-backlog.md)
- [`Default processor config`](../../../config/processor/default-processor.md)
- [`File ingestion hardening`](../../../architecture/etl-core/file-ingestion-hardening.md)
- [`Validation extension architecture`](../../../architecture/etl-core/validation-extension-architecture.md)

## Implementation notes

T1 shipped as a deliberately narrow CSV-first slice so rule semantics could stabilize before broader quarantine or XML-native duplicate handling.

## Status notes

Shipped baseline: validation-aware processing now supports `notNull`, `timeFormat`, duplicate handling, and rejected-record output on the active path.


