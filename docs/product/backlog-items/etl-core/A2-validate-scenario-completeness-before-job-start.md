# A2 - Validate scenario completeness before job start

## Summary

Add fail-fast startup validation so a selected job cannot begin execution when required config files, named step bindings, or minimum orchestration inputs are missing.

## Current board status

- Epic: **[Epic A](../../epics/epic-a-runtime-contract-and-model-governance.md)**
- Priority: **P0**
- Status: **Done**
- Milestone: **M1**
- Dependency: **A1**

> Keep these fields synchronized with the row in [`product-backlog.md`](../../product-backlog.md). The execution board remains the canonical source for changing status values.

Use the linked `Epic` entry above to navigate to the shared epic-level product context for this backlog item.

## Problem

Without early completeness checks, selected jobs could progress into late wiring/runtime failures that were harder for operators to diagnose.

## Goal

Reject incomplete selected-job bundles during startup, before batch wiring or record processing begins.

## Scope

- validate presence of required job-config structure
- validate named step/source/target/processor references
- validate referenced config-file existence for selected jobs

## Out of scope

- deep transformation-rule validation beyond the active config contracts
- retry/skip behavior
- control-plane scheduling concerns

## Proposed approach

Centralize startup validation in config-loading/orchestration boundaries so broken selected jobs fail before downstream runtime setup.

## Operator / runtime impact

- clearer startup failures
- less late runtime drift from missing config pieces
- preserved bundles become safer executable documentation

## Acceptance criteria

- [x] selected jobs fail fast when `steps` are missing or empty
- [x] unknown named step bindings fail before execution
- [x] missing referenced config files fail before downstream job wiring

## Related docs

- [`Product backlog`](../../product-backlog.md)
- [`Job config reference`](../../../config/job-config.md)
- [`Runtime flow`](../../../architecture/etl-core/runtime-flow.md)

## Implementation notes

A2 is the startup guardrail baseline that later A3 and A4 work depend on.

## Status notes

Shipped baseline: startup now validates selected-job completeness before normal job execution proceeds.


