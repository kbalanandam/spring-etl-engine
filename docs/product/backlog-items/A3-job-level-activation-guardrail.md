# A3 — Job-level activation guardrail

## Summary

Add a small job-level enable/disable contract so a selected `job-config.yaml` can be marked inactive and startup fails before the runtime resolves downstream configs or wires executable steps.

## Current board status

- Epic: **Epic A**
- Priority: **P1**
- Status: **Ready**
- Milestone: **M1**
- Dependency: **A2**

> Keep these fields synchronized with the row in [`product-backlog.md`](../product-backlog.md). The execution board remains the canonical source for changing status values.

## Problem

The current runtime selects exactly one explicit job through `etl.config.job -> job-config.yaml`, but it has no first-class way to mark a known job bundle as intentionally disabled.

That leaves an avoidable operator gap:

- a disabled bundle can still be selected for execution
- startup continues deeper into config resolution than necessary
- the product does not surface a clear job-aware configuration failure for inactive jobs

## Goal

Add a simple contract so a selected job can declare `isActive: false` and fail fast with a clear startup-time configuration error before normal runtime wiring begins.

## Scope

This item covers:

- optional top-level `isActive` support in `job-config.yaml`
- default behavior when `isActive` is omitted
- fail-fast validation for inactive jobs in `ConfigLoader`
- operator-friendly error handling for inactive selected jobs
- matching documentation and regression coverage

## Out of scope

This item does not cover:

- `steps[].isActive`
- schedule pause/resume controls
- force-run overrides for inactive jobs
- job discovery, registries, or folder scanning

## Proposed approach

The preferred first implementation is:

1. add optional `isActive` to `JobConfig`
2. default missing `isActive` to `true`
3. validate the flag in `ConfigLoader` immediately after parsing the selected `job-config.yaml`
4. stop before resolving referenced source/target/processor configs
5. fail with a clear config/startup exception that names the selected job and config path

## Operator / runtime impact

Expected impact when this item ships:

- inactive jobs fail before `BatchConfig` assembles steps
- startup errors become clearer for intentionally disabled bundles
- observability remains honest because blocked jobs do not emit normal execution events
- docs for `job-config.yaml` and runtime flow must be updated together with the implementation

## Acceptance criteria

- [ ] a selected job with `isActive: false` fails during explicit job-config startup
- [ ] missing `isActive` preserves current behavior
- [ ] `isActive: true` preserves current behavior
- [ ] failure happens before downstream config resolution and step wiring
- [ ] the failure message includes job/scenario identity and the resolved `job-config.yaml` path
- [ ] documentation and tests ship in the same change

## Related docs

- [`Product backlog`](../product-backlog.md)
- [`Job activation and startup guardrails`](../../architecture/job-level-activation-and-startup-guardrails.md)
- [`Job config reference`](../../config/job-config.md)
- [`Use explicit job-config for business-scenario selection`](../../adr/0004-use-explicit-job-config-for-business-scenario-selection.md)

## Implementation notes

Keep the first slice job-level only. Do not push activation decisions down into `BatchConfig`, reader/writer factories, or scheduler concerns.

## Status notes

Drafted as the first backlog drill-down page so the execution board can stay compact while still linking to fuller implementation detail.

