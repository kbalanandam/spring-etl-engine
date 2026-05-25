# B2 â€” Introduce configurable retry policy support where appropriate

## Summary

Add retry behavior only where the runtime can distinguish transient failures from deterministic configuration or data failures.

## Current board status

- Epic: **[Epic B](../epics/epic-b-runtime-hardening-and-file-behavior.md)**
- Priority: **P1**
- Status: **Deferred**
- Milestone: **M1**
- Dependency: **B1**

> Keep these fields synchronized with the row in [`product-backlog.md`](../product-backlog.md). The execution board remains the canonical source for changing status values.

Use the linked `Epic` entry above to navigate to the shared epic-level product context for this backlog item.

## Problem

A retry feature is useful only when it improves resilience. Applied too broadly, it can hide permanent failures, duplicate side effects, or delay clearer fail-fast diagnosis.

## Goal

Define where retry behavior is appropriate and observable in the selected-job runtime.

## Scope

- retry-policy contract for supported transient failure classes
- operator-visible retry evidence
- boundaries that prevent retry from masking deterministic config/data errors

## Out of scope

- retry-everything defaults
- scheduler-level re-trigger policy
- full restartability design

## Proposed approach

Build retry only after skip/failure semantics are clearer, and limit it to runtime boundaries where repeated attempts are safe and meaningful.

## Operator / runtime impact

- operators need clear retry counts and final outcomes
- some transient failures could self-recover
- unsafe retry paths should remain fail-fast

## Acceptance criteria

- [ ] one documented retry-policy contract exists for supported failure classes
- [ ] retry behavior emits clear evidence and final outcome state
- [ ] deterministic config/data failures are not silently retried as if they were transient

## Related docs

- [`Product backlog`](../product-backlog.md)
- [`File ingestion hardening`](../../architecture/etl-core/file-ingestion-hardening.md)
- [`Job history and operational observability`](../../architecture/control-plane/job-history-and-operational-observability.md)

## Implementation notes

Deferred until skip/failure behavior is explicit enough to prevent unsafe retry expansion.

## Status notes

Add retry only where side effects and idempotency expectations are understood.

