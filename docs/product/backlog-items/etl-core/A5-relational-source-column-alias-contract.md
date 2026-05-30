# A5 - Relational source column alias contract

## Summary

Capture the next relational reader contract improvement as a deferred backlog item: preserve the current phase-1 assumption that `fields[].name` matches both selected SQL column names and generated model property names, but define a follow-on config contract for scenarios where source column names and runtime property names must differ.

## Current board status

- Epic: **[Epic A](../../epics/etl-core/epic-a-runtime-contract-and-model-governance.md)**
- Priority: **P2**
- Status: **Deferred**
- Milestone: **M2**
- Dependency: **none**

> Keep these fields synchronized with the row in [`product-backlog.md`](../../product-backlog.md). The execution board remains the canonical source for changing status values.

Use the linked `Epic` entry above to navigate to the shared epic-level product context for this backlog item.

## Problem

The shipped phase-1 relational reader path assumes that each configured field name is both:

- the selected SQL column name
- the generated source model property name

That is a good first baseline, but it becomes restrictive when relational sources expose database naming that does not line up cleanly with the generated runtime contract. Without an explicit alias contract, teams must either reshape SQL manually everywhere or preserve awkward property names just to satisfy the current reader assumption.

## Goal

Add a deliberate, reviewable relational source alias contract so selected database column names can differ from runtime property names without introducing ad hoc query-only workarounds or scattering custom row-mapping logic.

## Scope

This item covers:

- a source-config contract for relational column aliasing or source-column naming
- relational reader support that maps database columns to generated/runtime property names explicitly
- focused relational tests for alias-driven reads
- documentation updates to the relational source contract and current limitations

## Out of scope

This item does not cover:

- a broad relational query-builder redesign
- reusable named connection registries
- stored procedure support
- multi-vendor relational feature expansion beyond the alias contract itself

## Proposed approach

Preferred implementation direction:

1. preserve the phase-1 field-name-equals-column-name baseline as the default
2. add an explicit source-column/alias field to the relational source contract instead of overloading unrelated properties
3. use that contract in `RelationalDynamicReader` row mapping and generated-field selection logic
4. keep vendor-specific SQL behavior behind the existing dialect/resolution seams rather than embedding new conditionals in `BatchConfig`
5. document the phase-1 default and the opt-in alias path together so authors know when each applies

## Operator / runtime impact

Expected impact when this item ships:

- relational scenarios can read more realistic database schemas without awkward runtime model naming
- config intent becomes more explicit than relying on handwritten SQL aliasing conventions alone
- the relational source contract remains centralized and reviewable
- automated proof becomes clearer for cross-schema/cross-naming scenarios

## Acceptance criteria

- [ ] the current relational phase-1 default continues to work unchanged when no alias contract is configured
- [ ] relational source config exposes a documented field-level alias or source-column contract
- [ ] `RelationalDynamicReader` reads aliased columns into the expected runtime properties
- [ ] focused automated tests cover both table-based and query-based alias scenarios where practical
- [ ] relational source docs describe the new contract and any remaining limitations clearly

## Related docs

- [`Product backlog`](../../product-backlog.md)
- [`Relational DB support`](../../../architecture/etl-core/relational-db-support.md)
- [`Extension points`](../../../architecture/etl-core/extension-points.md)
- [`Relational source reference`](../../../config/source/relational-source.md)

## Implementation notes

Keep this improvement additive. The first goal is an explicit config contract for source-column-to-property mapping, not a larger relational redesign. Preserve the existing phase-1 baseline and avoid spreading alias logic across unrelated runtime seams.

## Status notes

Intentionally parked while the current XML-to-JSON real-time scenario work takes priority. Revisit after that scenario is stable so the relational alias contract can be reviewed with a concrete use case in mind.


