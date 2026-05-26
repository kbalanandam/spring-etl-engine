# A6 - Retire remaining internal generated-model package bridge

## Summary

Follow the shipped A4 selected-job contract by removing the remaining internal compatibility bridge that still caches or backfills generated-model package identity inside runtime config objects and legacy helper paths. This item is intentionally parked for later, so future work should avoid expanding that bridge while it remains deferred.

## Current board status

- Epic: **[Epic A](../epics/epic-a-runtime-contract-and-model-governance.md)**
- Priority: **P2**
- Status: **Deferred**
- Milestone: **M2**
- Dependency: **A4**

> Keep these fields synchronized with the row in [`product-backlog.md`](../product-backlog.md). The execution board remains the canonical source for changing status values.

Use the linked `Epic` entry above to navigate to the shared epic-level product context for this backlog item.

## Problem

A4 completed the product-facing generated-model contract:

- selected source/target YAML is package-free
- runtime/build-time selected-job paths derive package identity internally
- authored `packageName` fails fast before deserialization
- generated-model package validation/resolution is centralized

But the repo still carries technical bridge behavior behind that contract:

- `SourceConfig` / `TargetConfig` still cache resolved package identity internally
- demo/direct-config fallback still injects legacy `com.etl.model.source|target` values for preserved compatibility
- some compatibility labels and helper paths still describe model resolution as a bridge rather than a settled runtime contract
- the standalone XML spike loader still derives fallback package names from definition paths for the preserved spike path

## Goal

Retire the remaining internal package bridge without reopening authored `packageName` as part of the supported config contract.

## Scope

This item covers:

- removing config-object `packageName` storage when downstream callers no longer need it
- replacing remaining bridge-era backfill or compatibility labels with the settled resolver contract where practical
- keeping demo fallback and standalone XML spike behavior only if they remain intentionally supported compatibility seams
- updating docs/tests if those remaining internal seams are narrowed or removed

## Out of scope

This item does not cover:

- changing the shipped explicit selected-job YAML contract
- adding new transformation or orchestration features
- redesigning standalone XML spike behavior unless that path is intentionally retired

## Suggested implementation direction

1. continue routing runtime/generator callers through centralized package resolution instead of config fields
2. remove config-object `packageName` state only after all downstream callers can resolve package identity without it
3. decide whether demo fallback keeps internal `com.etl.model.source|target` compatibility defaults or moves to a more explicit separate contract
4. retire bridge-era status labels only after descriptor/runtime wording still matches what operators see

## Acceptance criteria

- [ ] internal generated-model package resolution no longer depends on `SourceConfig` / `TargetConfig` storing `packageName`
- [ ] any preserved compatibility defaults that remain are explicitly documented as compatibility seams, not as the normal contract
- [ ] remaining bridge-era runtime/doc labels are narrowed or retired where they no longer describe shipped behavior
- [ ] focused runtime/generation tests stay green after the bridge reduction

## Related docs

- [`Product backlog`](../product-backlog.md)
- [`A4 - Standardize generated-model naming and package derivation`](A4-standardize-generated-model-naming-and-package-derivation.md)
- [`Generated model naming standard`](../../architecture/etl-core/generated-model-naming-standard.md)

