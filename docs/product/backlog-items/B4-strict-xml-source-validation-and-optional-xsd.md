# B4 â€” Strict XML source validation and optional XSD checks

## Summary

Capture the shipped XML reader-adjacent hardening step: preserve the current lightweight structural XML validation baseline, while allowing selected XML sources to opt into strict XSD validation before reading starts.

## Current board status

- Epic: **[Epic B](../epics/epic-b-runtime-hardening-and-file-behavior.md)**
- Priority: **P2**
- Status: **Done**
- Milestone: **M2**
- Dependency: **none**

> Keep these fields synchronized with the row in [`product-backlog.md`](../product-backlog.md). The execution board remains the canonical source for changing status values.

Use the linked `Epic` entry above to navigate to the shared epic-level product context for this backlog item.

## Problem

The current XML source-validation path already checks file existence, readability, well-formedness, configured `rootElement`, and configured `recordElement`, which is the right lightweight baseline for most runs.

What is still missing is an explicit strict-contract option for scenarios where XML schema compliance matters. Without that option, teams that need schema-level safety may either rely on downstream JAXB/runtime failures or implement one-off validation outside the shared source-validation SPI.

## Goal

Add a reviewable, opt-in XML strict-validation contract that keeps lightweight structural checks as the default path while allowing selected XML scenarios to fail fast on XSD/schema violations before the reader begins normal record processing.

## Scope

This item covers:

- an explicit XML strict-validation contract in the active source-validation SPI
- optional XSD/schema path support in `XmlSourceConfig`
- fail-fast validation in `XmlSourceValidator`
- focused tests for valid, invalid, and missing-schema cases
- config and architecture documentation updates plus at least one preserved scenario example

## Out of scope

This item does not cover:

- moving XML source-contract checks into `XmlDynamicReader`
- XPath-driven source-native duplicate identity or business-rule rejection
- broad XML namespace/query authoring features beyond the strict-validation contract
- a full redesign of the current XML flattening strategy seams

## Proposed approach

Preferred implementation direction:

1. keep the current lightweight XML checks as the default baseline
2. extend `XmlSourceConfig.ValidationConfig` with an optional strict-schema setting such as `schemaPath`
3. run XSD validation only when the strict contract is explicitly configured
4. keep failures on the source-validation path so operator-facing errors remain scenario-aware and fail fast before read/flatten/write stages
5. document the cost tradeoff clearly: schema validation is opt-in because it adds a fuller document-validation pass

## Operator / runtime impact

Expected impact when this item ships:

- XML scenarios that require schema compliance gain a supported fail-fast mode
- operators see clearer source-level failures before reader/processor stack traces dominate the evidence
- default XML runs keep the lighter structural validation baseline for performance-sensitive flows
- config docs gain an explicit strict-vs-lightweight XML validation story

## Acceptance criteria

- [x] XML source validation keeps the current lightweight default behavior when no schema contract is configured
- [x] selected XML scenarios can opt into strict schema validation through source config
- [x] XSD validation failures surface through the active source-validation SPI with scenario-aware error messages
- [x] focused automated tests cover pass/fail and missing-schema cases
- [x] docs and at least one preserved scenario bundle show how the strict contract is intended to be used

## Related docs

- [`Product backlog`](../product-backlog.md)
- [`Validation extension architecture`](../../architecture/etl-core/validation-extension-architecture.md)
- [`File ingestion hardening`](../../architecture/etl-core/file-ingestion-hardening.md)
- [`XML source reference`](../../config/source/xml-source.md)

## Implementation notes

Keep this item narrow and architecture-aligned: source-contract validation belongs in `XmlSourceValidator` and `SourceValidationService`, not in `BatchConfig` or reader-specific branching. The current XML reader path should stay focused on streaming/flattening behavior.

## Status notes

Shipped as a narrow XML source-validation slice: `XmlSourceConfig.ValidationConfig` now supports optional `schemaPath`, `XmlSourceValidator` runs XSD validation on the active source-validation path when that setting is configured, whole-file reject behavior reuses `validation.onFailure=rejectFile`, focused tests cover pass/fail/missing-schema cases, and the preserved `xml-to-json-events` bundle now demonstrates the contract with a local XSD file.

