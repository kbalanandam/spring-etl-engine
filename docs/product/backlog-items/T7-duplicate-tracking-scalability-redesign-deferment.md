# T7 - Duplicate-tracking scalability redesign deferment

## Summary

Capture the larger duplicate-tracking scalability redesign as a separate deferred track so current transformation hardening can proceed without reopening the shipped duplicate baseline contract in the same item.

## Current board status

- Epic: **[Epic T](../epics/epic-t-transformation-capability.md)**
- Priority: **P2**
- Status: **Deferred**
- Milestone: **M3**
- Dependency: **T4**

> Keep these fields synchronized with the row in [`product-backlog.md`](../product-backlog.md). The execution board remains the canonical source for changing status values.

Use the linked `Epic` entry above to navigate to the shared epic-level product context for this backlog item.

## Problem

The active duplicate handling path now has correctness and guardrail improvements, but long-run scalability design choices are still open (memory bounds, storage strategy, state representation, and restart-aware behavior). Keeping these redesign decisions inside `T4` risks blurring scope and delaying near-term hardening.

## Goal

Define a future-ready duplicate-state scalability direction that can be activated later without destabilizing the current shipped processor-rule contract.

## Scope

This item covers:

- defining scalability constraints and sizing goals for duplicate-state tracking
- evaluating state backends and representations for high-volume duplicate keys
- defining clear activation criteria for when the redesign should move from deferred to active
- preserving compatibility expectations for existing `duplicate` rule usage

## Out of scope

This item does not cover:

- immediate runtime behavior changes in the current milestone
- replacing the current keep-first and ordered-winner semantics
- moving duplicate handling from processor rules to source validation as a default
- broad transformation-platform redesign work unrelated to duplicate tracking scale

## Proposed approach

Treat this as a design-first deferred item:

1. document current baseline behavior and limits
2. define the decision matrix for memory-only, bounded-memory, and staged-disk paths
3. specify observability expectations for duplicate-state pressure and fallback behavior
4. stage implementation only after milestone priorities above this item are complete

## Operator / runtime impact

When activated, expected impact should include:

- clearer duplicate-state sizing guidance for operators
- better predictability for high-volume duplicate workloads
- explicit evidence when duplicate-state limits or fallback modes are used
- stable behavior for existing low-to-medium volume jobs that do not require redesign paths

## Acceptance criteria

- [ ] a documented scalability decision record exists for duplicate-state tracking options
- [ ] activation criteria and non-goals are explicit in product docs
- [ ] compatibility expectations for existing duplicate-rule behavior are documented
- [ ] proposed observability and guardrail signals are defined before implementation starts

## Related docs

- [`Product backlog`](../product-backlog.md)
- [`T4 - Transformation quarantine and duplicate hardening`](T4-transformation-quarantine-and-duplicate-hardening.md)
- [`Validation extension architecture`](../../architecture/etl-core/validation-extension-architecture.md)
- [`File ingestion hardening`](../../architecture/etl-core/file-ingestion-hardening.md)

## Implementation notes

This page intentionally records deferment and scope boundaries now so future implementation work can start from explicit constraints instead of re-litigating the baseline contract.

## Status notes

Deferred by design. Revisit after `T3` and the higher-priority runtime hardening sequence, or earlier only if production evidence shows duplicate-state pressure beyond the current bounded baseline.

