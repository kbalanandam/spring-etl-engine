# D1 — Stable error taxonomy and categories

## Summary

Define a stable operator-facing error taxonomy so failures can be grouped, searched, reported, and reasoned about consistently instead of relying on raw stack traces alone.

## Current board status

- Epic: **Epic D**
- Priority: **P1**
- Status: **Deferred**
- Milestone: **M2**
- Dependency: **C1**

> Keep these fields synchronized with the row in [`product-backlog.md`](../product-backlog.md). The execution board remains the canonical source for changing status values.

## Problem

The runtime already emits meaningful lifecycle evidence, but the product still lacks a stable, well-documented error-category model that operators can use consistently across runs.

Without this item, failures may still be interpreted mainly through:

- exception class names
- stack traces
- ad hoc wording in log messages

That makes operator investigation harder and weakens later search, job-history, and reporting capabilities.

## Goal

Define a stable error taxonomy that classifies common ETL failures into clear, operator-usable categories and aligns that taxonomy with runtime evidence, logs, and future observability/reporting work.

## Scope

This item covers:

- stable high-level error categories for the active runtime
- operator-readable categorization guidance in logs and structured events
- alignment between runtime exceptions and product-facing failure categories
- documentation of the main category boundaries used for diagnosis and reporting

## Out of scope

This item does not cover:

- every low-level exception mapping for every library
- final dashboard or UI implementation
- full long-term job-history persistence by itself
- broader release-gating rules or provenance reporting

## Proposed approach

The preferred direction is:

1. define a small stable category set first
2. align runtime logging/evidence to emit those categories consistently
3. preserve more detailed exception information underneath the stable category layer
4. document category meanings so operators can distinguish likely next actions

The initial category family should at least distinguish areas such as:

- configuration failures
- validation failures
- transformation failures
- source-read failures
- target-write failures
- runtime/infrastructure failures

## Operator / runtime impact

Expected impact when this item ships:

- operators can identify failure type faster without reading full stack traces first
- logs and events become more searchable and comparable across runs
- future job-history and reporting work gains a stable error dimension
- exception details remain available, but no longer act as the only diagnostic entry point

## Acceptance criteria

- [ ] a stable set of runtime error categories is defined and documented
- [ ] runtime evidence emits those categories consistently where failures are surfaced
- [ ] category wording is operator-readable and not only developer-oriented
- [ ] documentation distinguishes category meanings and likely investigation direction
- [ ] tests or focused verification prove representative category mapping behavior

## Related docs

- [`Product backlog`](../product-backlog.md)
- [`Job history and operational observability`](../../architecture/job-history-and-operational-observability.md)
- [`Runtime flow`](../../architecture/runtime-flow.md)

## Implementation notes

Keep the taxonomy intentionally small at first. It should stabilize cross-run meaning before it tries to capture every technical nuance of every exception type.

## Status notes

Deferred today, but important enough to deserve a detail page because it directly affects observability, job history, and operator usability across multiple future items.

