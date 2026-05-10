# S1 — Schedule model and trigger contract

## Summary

Define the first schedule model and trigger contract for scenario-based execution so scheduling can grow inside the product without creating a second orchestration model outside the explicit selected-job runtime.

## Current board status

- Epic: **Epic S**
- Priority: **P1**
- Status: **Deferred**
- Milestone: **M2**
- Dependency: **A1, C1**

> Keep these fields synchronized with the row in [`product-backlog.md`](../product-backlog.md). The execution board remains the canonical source for changing status values.

## Problem

The runtime already supports explicit selected-job execution, but it does not yet define how a scheduler or trigger should bind to that execution model.

Without a clear contract, future scheduler work may:

- introduce a second orchestration model outside `job-config.yaml`
- blur the boundary between trigger, execution, retry, and restart behavior
- make operator evidence for why a run started or skipped inconsistent

## Goal

Define a narrow schedule and trigger contract that starts scheduled execution from the same explicit selected-job boundary already used for manual runs.

## Scope

This item covers:

- the first schedule identity and trigger contract for scenario/job execution
- how a schedule points to one selected runnable job bundle
- trigger-origin expectations for operational evidence
- the boundary between scheduling, orchestration, retry, and restartability

## Out of scope

This item does not cover:

- pause/resume controls themselves
- overlap policy implementation
- missed-run handling implementation
- detailed timezone behavior beyond what the contract must reserve
- a separate scheduler product or unrelated orchestration platform

## Proposed approach

The preferred direction is:

1. keep one selected `etl.config.job`-style execution boundary as the runtime contract
2. let schedule definitions point to that same selected job boundary instead of inventing a second orchestration model
3. record trigger origin and schedule identity in run evidence
4. keep retry and restart semantics separate so schedule definition does not quietly redefine runtime recovery behavior
5. defer advanced controls such as pause/resume, overlap, and missed-run policy to `S2` and `S3`

## Operator / runtime impact

Expected impact when this item ships:

- scheduler work stays aligned with the current explicit-job runtime model
- operators can tell why a run started and which schedule triggered it
- later schedule features gain a stable contract instead of layering on ad hoc trigger behavior
- scheduling remains inside the main product roadmap without becoming a second pseudo-product

## Acceptance criteria

- [ ] the first schedule model is defined in terms of one selected job/scenario execution contract
- [ ] trigger identity and origin are defined clearly enough for operational evidence
- [ ] the contract separates scheduling from retry/restart semantics
- [ ] follow-on items `S2` and `S3` can build from this contract without re-deciding the execution boundary
- [ ] related runtime-direction or backlog documentation is updated accordingly

## Related docs

- [`Product backlog`](../product-backlog.md)
- [`Scenario-driven runtime direction`](../../architecture/scenario-driven-runtime-direction.md)
- [`Runtime flow`](../../architecture/runtime-flow.md)

## Implementation notes

This item should define the contract, not over-implement it. The key guardrail is that scheduling must launch the same explicit selected-job execution model already used by the runtime today.

## Status notes

Deferred for now, but important enough to document because scheduler work can easily drift into a second orchestration path if the contract is not defined early.

