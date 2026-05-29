# T4 - Transformation quarantine and duplicate hardening

## Summary

Extend the first shipped validation and reject-handling slice without redesigning the active processor contract, focusing on broader quarantine behavior, future duplicate storage options, and the remaining XML-native duplicate gap.

## Current board status

- Epic: **[Epic T](../../epics/epic-t-transformation-capability.md)**
- Priority: **P1**
- Status: **Done**
- Milestone: **M2**
- Dependency: **T1, T2, T3**

> Keep these fields synchronized with the row in [`product-backlog.md`](../../product-backlog.md). The execution board remains the canonical source for changing status values.

Use the linked `Epic` entry above to navigate to the shared epic-level product context for this backlog item.

## Problem

The current shipped slice already provides meaningful processor validation, rejected-record output, and duplicate handling, but several follow-on needs remain deferred:

- broader quarantine handling beyond the current reject-output baseline
- explicit and documented duplicate resolver selection behavior (`auto` adaptive default with optional deterministic overrides)
- XML-native duplicate identity when flat mapped fields are not enough

With follow-on scope now split to `T15`, later work can avoid stretching the current processor contract or accidentally redesigning the shipped baseline.

Why duplicate storage modes were introduced in this item:

- operators needed predictable behavior for ordered duplicate winner selection in specific high-volume or controlled environments
- teams still needed a safe default (`auto`) so most scenarios do not require frequent YAML retuning as input size changes
- runtime needed explicit evidence (`resolverMode`, `resolverReason`) so resolver decisions are auditable during startup planning and step execution

Terminology guardrail for this item:

- processor config value: `storageMode: memory|embeddedDb|auto`
- runtime evidence value: `resolverMode=inMemory|embeddedDb`

## Goal

Add the next hardening layer for rejected-record and duplicate behavior while preserving the current active runtime contract and keeping the strongest operator-facing behavior explicit and testable.

## Scope

This item covers:

- broader quarantine / rejected-record follow-on direction beyond the first slice
- duplicate storage-mode hardening for ordered winner selection (`duplicate` + `orderBy`), including `auto` default and optional `memory` / `embeddedDb` overrides
- documentation of when XML-native duplicate identity should remain separate from the shared processor-level `duplicate` rule
- preserving the current shipped duplicate baseline while extending it carefully

## Out of scope

This item does not cover:

- a full redesign of `duplicate` rule semantics
- moving duplicate handling into source validation by default
- a generic rule engine or broad transformation platform rewrite
- changing keep-first duplicate behavior when `orderBy` is not configured
- target-aware deduplication as part of the first follow-on slice

## Proposed approach

The preferred direction is:

1. preserve the current shipped baseline as the default contract
2. extend quarantine/reject handling only where operators gain clearer control or evidence
3. keep duplicate handling on the active processor-rule seam unless a source-native case truly requires otherwise
4. keep `auto` as the baseline and keep `memory` / `embeddedDb` as additive operator-visible overrides rather than hidden runtime switches
5. document the XML-specific exception clearly so nested/source-structure duplicate cases do not distort the shared processor rule

## Operator / runtime impact

Expected impact when this item ships:

- reject and quarantine behavior becomes more intentional and predictable for operators
- duplicate handling becomes more tunable for larger runs
- XML scenarios with non-flat duplicate identity get a clearer extension boundary
- the shipped duplicate baseline remains stable for CSV, flat XML, relational, and similar record-oriented flows

## Acceptance criteria

- [x] the follow-on duplicate scope is documented without weakening the current shipped contract
- [x] any new quarantine or reject-output behavior remains explicit and operator-visible
- [x] storage-mode rationale is documented clearly: why `auto` is default, when to use `memory`, and when to use `embeddedDb`
- [x] storage-mode scope remains constrained to ordered winner selection (`duplicate` + `orderBy`)
- [x] XML-native duplicate identity remains separated unless flat mapped fields are genuinely insufficient
- [x] at least one preserved scenario or focused regression test proves the new behavior when implementation begins

## Related docs

- [`Product backlog`](../../product-backlog.md)
- [`File ingestion hardening`](../../../architecture/etl-core/file-ingestion-hardening.md)
- [`Validation extension architecture`](../../../architecture/etl-core/validation-extension-architecture.md)
- [`Runtime flow`](../../../architecture/etl-core/runtime-flow.md)
- [`CSV to XML runtime flow`](../../../architecture/etl-core/csv-to-xml-runtime-flow.md)
- [`Default processor reference`](../../../config/processor/default-processor.md)

Suggested reading order for this topic:

1. [`Default processor reference`](../../../config/processor/default-processor.md) for the shipped YAML contract (`storageMode`)
2. [`File ingestion hardening`](../../../architecture/etl-core/file-ingestion-hardening.md) for resolver behavior and operational rationale
3. [`Runtime flow`](../../../architecture/etl-core/runtime-flow.md) and [`CSV to XML runtime flow`](../../../architecture/etl-core/csv-to-xml-runtime-flow.md) for emitted evidence events and execution context

## Implementation notes

This item should harden and clarify the existing baseline, not replace it. The board's current checkpoint already states that follow-on work should stay scoped to deferred storage-mode and XML-native identity concerns rather than a redesign of the shipped duplicate baseline.

First thin-slice implementation direction for this item:

- add operator-visible ordered-duplicate resolver evidence (`resolverMode`, `resolverReason`) before introducing any client-selectable storage-mode YAML
- keep duplicate semantics unchanged while making resolver choice explicit in logs and step-level evidence

Second thin-slice implementation direction for this item:

- add optional duplicate `storageMode` override for `orderBy` winner selection (`auto`, `memory`, `embeddedDb`)
- keep `auto` as the default so existing behavior remains unchanged unless operators explicitly opt in

Third thin-slice implementation direction for this item:

- add optional `rejectHandling.quarantinePath` so completed steps with rejected records publish a quarantined copy of the finalized reject artifact
- keep quarantine publication additive and operator-visible (no behavior change when `quarantinePath` is omitted)

## Status notes

Resolver evidence, optional ordered duplicate `storageMode`, and additive reject-quarantine publication are now shipped on the active processor/file-ingestion path.

The remaining deferred concern is still XML-native duplicate identity when keys cannot be expressed cleanly through flat mapped fields; keep that boundary explicit and separate from the shipped processor-level duplicate contract.

Larger duplicate-state scalability redesign choices are now tracked separately in [`T7`](T7-duplicate-tracking-scalability-redesign-deferment.md) so this item can stay focused on quarantine and contract-boundary hardening.


