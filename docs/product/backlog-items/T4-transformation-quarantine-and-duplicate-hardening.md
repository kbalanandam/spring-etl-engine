# T4 — Transformation quarantine and duplicate hardening

## Summary

Extend the first shipped validation and reject-handling slice without redesigning the active processor contract, focusing on broader quarantine behavior, future duplicate storage options, and the remaining XML-native duplicate gap.

## Current board status

- Epic: **[Epic T](../epics/epic-t-transformation-capability.md)**
- Priority: **P1**
- Status: **Deferred**
- Milestone: **M2**
- Dependency: **T1, T2, T3**

> Keep these fields synchronized with the row in [`product-backlog.md`](../product-backlog.md). The execution board remains the canonical source for changing status values.

Use the linked `Epic` entry above to navigate to the shared epic-level product context for this backlog item.

## Problem

The current shipped slice already provides meaningful processor validation, rejected-record output, and duplicate handling, but several follow-on needs remain deferred:

- broader quarantine handling beyond the current reject-output baseline
- explicit client-selectable duplicate storage mode
- XML-native duplicate identity when flat mapped fields are not enough

Without a clear follow-on item, later work risks either stretching the current processor contract too far or accidentally redesigning the shipped baseline instead of hardening it.

## Goal

Add the next hardening layer for rejected-record and duplicate behavior while preserving the current active runtime contract and keeping the strongest operator-facing behavior explicit and testable.

## Scope

This item covers:

- broader quarantine / rejected-record follow-on direction beyond the first slice
- explicit future duplicate storage-mode selection such as `memory` vs later disk-backed behavior
- documentation of when XML-native duplicate identity should remain separate from the shared processor-level `duplicate` rule
- preserving the current shipped duplicate baseline while extending it carefully

## Out of scope

This item does not cover:

- a full redesign of `duplicate` rule semantics
- moving duplicate handling into source validation by default
- a generic rule engine or broad transformation platform rewrite
- target-aware deduplication as part of the first follow-on slice

## Proposed approach

The preferred direction is:

1. preserve the current shipped baseline as the default contract
2. extend quarantine/reject handling only where operators gain clearer control or evidence
3. keep duplicate handling on the active processor-rule seam unless a source-native case truly requires otherwise
4. introduce any future storage-mode choice as an additive operator-visible option rather than a hidden runtime switch
5. document the XML-specific exception clearly so nested/source-structure duplicate cases do not distort the shared processor rule

## Operator / runtime impact

Expected impact when this item ships:

- reject and quarantine behavior becomes more intentional and predictable for operators
- duplicate handling becomes more tunable for larger runs
- XML scenarios with non-flat duplicate identity get a clearer extension boundary
- the shipped duplicate baseline remains stable for CSV, flat XML, relational, and similar record-oriented flows

## Acceptance criteria

- [ ] the follow-on duplicate scope is documented without weakening the current shipped contract
- [ ] any new quarantine or reject-output behavior remains explicit and operator-visible
- [ ] future storage-mode selection is defined clearly if introduced
- [ ] XML-native duplicate identity remains separated unless flat mapped fields are genuinely insufficient
- [ ] at least one preserved scenario or focused regression test proves the new behavior when implementation begins

## Related docs

- [`Product backlog`](../product-backlog.md)
- [`File ingestion hardening`](../../architecture/file-ingestion-hardening.md)
- [`Validation extension architecture`](../../architecture/validation-extension-architecture.md)
- [`Default processor reference`](../../config/processor/default-processor.md)

## Implementation notes

This item should harden and clarify the existing baseline, not replace it. The board's current checkpoint already states that follow-on work should stay scoped to deferred storage-mode and XML-native identity concerns rather than a redesign of the shipped duplicate baseline.

First thin-slice implementation direction for this item:

- add operator-visible ordered-duplicate resolver evidence (`resolverMode`, `resolverReason`) before introducing any client-selectable storage-mode YAML
- keep duplicate semantics unchanged while making resolver choice explicit in logs and step-level evidence

## Status notes

Deferred for now, but important enough to justify a detail page because the backlog row compresses several distinct but related future concerns into one short note.

Larger duplicate-state scalability redesign choices are now tracked separately in [`T7`](T7-duplicate-tracking-scalability-redesign-deferment.md) so this item can stay focused on quarantine and contract-boundary hardening.

