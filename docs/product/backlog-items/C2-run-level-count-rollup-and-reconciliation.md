# C2 — Run-level count rollup and reconciliation

## Summary

Complete the run-level count model so operators can understand overall ETL outcomes without reconstructing them manually from step-level evidence.

## Current board status

- Epic: **Epic C**
- Priority: **P1**
- Status: **In Progress**
- Milestone: **M1**
- Dependency: **C1**

> Keep these fields synchronized with the row in [`product-backlog.md`](../product-backlog.md). The execution board remains the canonical source for changing status values.

## Problem

The runtime already emits meaningful step-level evidence and a run summary, but it does not yet provide the full run-level source / written / rejected rollup that operators need for quick reconciliation.

Today that means an operator may still need to inspect:

- multiple `STEP_EVENT` or step-finished records
- step-local counts such as `readCount`, `writeCount`, and `rejectedCount`
- scenario logs plus startup/planning evidence

before they can answer the simple question: *What happened overall in this run?*

## Goal

Expose a clear run-level rollup that summarizes the overall outcome of one selected job run and supports reliable input-vs-output reconciliation.

## Scope

This item covers:

- run-level source / written / rejected count rollup
- clearer overall reconciliation expectations for one selected run
- operator-facing interpretation of run totals versus step totals
- documentation of what the run summary should mean in multi-step scenarios

## Out of scope

This item does not cover:

- persisted long-term job history storage by itself
- dashboard technology or external observability platforms
- final enterprise reconciliation workflows for every target type
- scheduler-trigger audit or replay controls

## Proposed approach

The preferred near-term direction is:

1. preserve the existing step-level evidence as the ground truth
2. calculate run-level totals from the selected job's executed steps
3. publish those totals in the run summary / run-finished evidence path
4. document how totals should be interpreted for multi-step scenarios, especially where one step writes an intermediate artifact that becomes the next step's source
5. keep the semantics operator-friendly and deterministic

## Operator / runtime impact

Expected impact when this item ships:

- operators can answer overall run outcome questions without reading every step log entry
- successful vs partial vs rejected-heavy runs become easier to interpret quickly
- multi-step scenarios gain clearer reconciliation guidance
- run-level evidence becomes a stronger base for later job-history and reporting work

## Acceptance criteria

- [ ] run-level source / written / rejected totals are emitted for the selected run
- [ ] totals are documented clearly enough for operators to interpret them in multi-step scenarios
- [ ] step-level evidence remains available and is not replaced by the rollup
- [ ] tests cover at least one multi-step scenario and verify the expected run summary totals
- [ ] related observability docs are updated in the same change

## Related docs

- [`Product backlog`](../product-backlog.md)
- [`Job history and operational observability`](../../architecture/job-history-and-operational-observability.md)
- [`Runtime flow`](../../architecture/runtime-flow.md)

## Implementation notes

Be explicit about whether run-level totals are:

- raw sum-of-step counts, or
- operator-oriented rollups that distinguish intermediate handoff steps from final published-output steps

That distinction matters most for multi-step jobs where a CSV or XML artifact is both an output of one step and an input to another.

## Status notes

The board already marks this item as `In Progress`. This page exists so the reconciliation rules and intended rollup semantics can be reviewed without expanding the execution board row.

