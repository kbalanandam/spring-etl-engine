# B1 - Introduce configurable skip policy support

## Summary

Add one explicit skip-policy contract so selected jobs can continue past approved classes of failure only when that tradeoff is intentional, bounded, and visible to operators.

## Current board status

- Epic: **[Epic B](../epics/epic-b-runtime-hardening-and-file-behavior.md)**
- Priority: **P1**
- Status: **Deferred**
- Milestone: **M1**
- Dependency: **A1**

> Keep these fields synchronized with the row in [`product-backlog.md`](../product-backlog.md). The execution board remains the canonical source for changing status values.

Use the linked `Epic` entry above to navigate to the shared epic-level product context for this backlog item.

## Problem

The current baseline favors fail-fast behavior. Some workloads will eventually need controlled skip semantics, but without one explicit contract that can easily drift into silent data loss.

## Goal

Define configurable skip behavior that preserves evidence and keeps operators aware of what the runtime intentionally ignored.

## Scope

- documented skip-policy contract for supported runtime boundaries
- operator-visible evidence for skipped work
- guardrails that keep unsupported scenarios fail-fast

## Out of scope

- blanket exception swallowing
- restartability semantics by itself
- scheduler retry design

## Proposed approach

Add skip behavior only after orchestration and failure categories are explicit enough to keep the tradeoff understandable.

## Operator / runtime impact

- operators need clear counts and reasons for skipped work
- selected jobs could continue past approved failure classes
- reporting/evidence should reflect skip behavior explicitly

## Acceptance criteria

- [ ] one documented skip-policy contract exists
- [ ] skipped work is surfaced through logs/evidence/reporting
- [ ] ambiguous or unsupported skip scenarios still fail fast

## Related docs

- [`Product backlog`](../product-backlog.md)
- [`File ingestion hardening`](../../architecture/etl-core/file-ingestion-hardening.md)
- [`Job history and operational observability`](../../architecture/control-plane/job-history-and-operational-observability.md)

## Implementation notes

Deferred until the broader failure-taxonomy and orchestration surfaces are mature enough.

## Status notes

Keep future skip support narrow and evidence-first.

