# B1 - Introduce configurable skip policy support

## Summary

Add one explicit skip-policy contract so selected jobs can continue past approved classes of failure only when that tradeoff is intentional, bounded, and visible to operators.

## Current board status

- Epic: **[Epic B](../../epics/epic-b-runtime-hardening-and-file-behavior.md)**
- Priority: **P1**
- Status: **Done**
- Milestone: **M1**
- Dependency: **A1**

> Keep these fields synchronized with the row in [`product-backlog.md`](../../product-backlog.md). The execution board remains the canonical source for changing status values.

Use the linked `Epic` entry above to navigate to the shared epic-level product context for this backlog item.

## Problem

The current baseline favors fail-fast behavior. Some workloads will eventually need controlled skip semantics, but without one explicit contract that can easily drift into silent data loss.

The immediate risk is policy ambiguity: teams may treat processor validation rejects, reader parse failures, and target-write exceptions as the same "skip" concept even though they have very different operational consequences.

## Goal

Define configurable skip behavior that preserves evidence and keeps operators aware of what the runtime intentionally ignored.

## Scope

- documented skip-policy contract for supported runtime boundaries
- operator-visible evidence for skipped work
- guardrails that keep unsupported scenarios fail-fast
- educational examples that show when skip is acceptable vs unsafe

## Out of scope

- blanket exception swallowing
- restartability semantics by itself
- scheduler retry design
- rewriting validation `rules[]` semantics that already handle rejectable data-quality outcomes

## Proposed approach

Add skip behavior only after orchestration and failure categories are explicit enough to keep the tradeoff understandable.

Start with one narrow contract tied to explicit `job-config.yaml` `steps[]` execution order:

1. define skip at step scope, not scenario-global implicit behavior
2. allow skip only for explicitly approved failure categories
3. preserve fail-fast defaults when no skip policy is authored
4. surface skip counts/reasons in run evidence and step evidence

## Educational examples (implemented boundary behavior)

### Example A - CSV row-level validation reject is not B1 skip

Context:

- source: CSV customer file
- processor: built-in validation `rules[]` already route bad records to rejected output (where configured)
- target: relational or CSV target write

Teaching point:

- this is normal data-quality handling in the existing processor flow (`read -> transforms -> rules -> write`)
- B1 should not duplicate that behavior by introducing a second "skip invalid row" mechanism
- B1 should focus on step-level runtime exceptions where continuing is an explicit tradeoff

### Example B - Optional reader skip for malformed line classes

Context:

- step `ingest-customers` reads CSV with occasional malformed trailer lines from partner feeds
- product owner accepts skipping up to a small bounded count for that specific malformed-line category

Teaching point:

- policy must be explicit and bounded (for example, max skip count)
- once the threshold is exceeded, step must fail fast
- evidence must include skipped count and category so operators can decide whether source-provider remediation is required

### Example C - Target write exception must remain fail-fast by default

Context:

- target write fails due to connectivity or constraint errors

Teaching point:

- these failures can cause partial side effects and should not be silently skipped
- unless and until a narrowly justified rule exists, B1 contract keeps this path fail-fast

## Decision boundary for B1

Use this simple boundary until D1 taxonomy is finalized:

- prefer existing reject behavior for deterministic record-level validation failures
- consider bounded skip only for explicitly categorized, non-business-critical runtime exceptions
- never skip configuration-resolution failures for the selected job
- default to fail-fast whenever failure class is unknown or unmapped

## Trade-off snapshot

- **Benefit:** improves operational continuity for known noisy-but-tolerable error classes
- **Cost:** can hide deteriorating data/source quality if thresholds and evidence are weak
- **Guardrail:** require explicit category + max skip budget + operator-visible evidence

## Operator / runtime impact

- operators need clear counts and reasons for skipped work
- selected jobs could continue past approved failure classes
- reporting/evidence should reflect skip behavior explicitly
- step-level outcomes must distinguish `completed_with_skips` style states from clean success

## Acceptance criteria

- [x] one documented skip-policy contract exists (`docs/config/job-config.md`, `src/main/java/com/etl/config/job/JobConfig.java`)
- [x] skipped work is surfaced through logs/evidence/reporting (`target/skip-proof-lines.txt`, `logs/2026-05-28/customer-load-skip-policy-category-unclassified.log`)
- [x] ambiguous or unsupported skip scenarios still fail fast (`src/test/java/com/etl/config/ConfigLoaderJobConfigTest.java`, `src/test/java/com/etl/config/BatchConfigStepOrchestrationTest.java`)
- [x] examples prove boundary between processor reject handling and B1 step-level skip handling (Examples A/B/C in this page, plus `docs/config/processor/default-processor.md`)
- [x] skip-threshold breach behavior is deterministic and test-covered (`configuredSkipPolicyStopsSkippingWhenSkipLimitIsReached` in `src/test/java/com/etl/config/BatchConfigStepOrchestrationTest.java`)

## Related docs

- [`Product backlog`](../../product-backlog.md)
- [`File ingestion hardening`](../../../architecture/etl-core/file-ingestion-hardening.md)
- [`Job history and operational observability`](../../../architecture/control-plane/job-history-and-operational-observability.md)
- [`Default processor reference`](../../../config/processor/default-processor.md)
- [`Runtime flow`](../../../architecture/etl-core/runtime-flow.md)

## Implementation notes

Keep first implementation narrow and educationally clear:

- preserve current validation-reject semantics
- introduce skip only where failure category and threshold are explicit
- align final category names with `D1` once taxonomy is stable

## Status notes

First runtime slice is complete and evidence-backed:

- step-scoped `steps[].skipPolicy` contract in selected `job-config.yaml`
- category-first matching through `skippableCategories[]` with `skippableExceptions[]` compatibility fallback
- guardrails for tasklet-to-chunk override and ordered-duplicate incompatibility
- preserved proof bundle `src/main/resources/config-jobs/customer-load-skip-policy-category-unclassified/` with non-zero skip evidence

Keep follow-on scope narrow and evidence-first while remaining B1 acceptance criteria are closed.

Acceptance evidence map:

- runtime proof: `target/skip-proof-lines.txt`
- preserved scenario: `src/main/resources/config-jobs/customer-load-skip-policy-category-unclassified/`
- orchestration tests: `src/test/java/com/etl/config/BatchConfigStepOrchestrationTest.java`
- config-contract tests: `src/test/java/com/etl/config/ConfigLoaderJobConfigTest.java`
- verification snapshot: `target/verification-report.md`


