# S1 - Schedule model and trigger contract

## Summary

Define the first schedule model and trigger contract for scenario-based execution so an optional OneFlow control plane can grow around the ETL core without creating a second orchestration model outside the explicit selected-job runtime.

That contract must also preserve first-class interoperability with external schedulers and orchestrators for adopters that do not want to use a OneFlow-native scheduler.

## Current board status

- Epic: **[Epic S](../../epics/scheduler/epic-s-scheduling-and-control-plane.md)**
- Priority: **P1**
- Status: **Ready**
- Milestone: **M2**
- Dependency: **A1, C1**

> Keep these fields synchronized with the row in [`product-backlog.md`](../../product-backlog.md). The execution board remains the canonical source for changing status values.

Use the linked `Epic` entry above to navigate to the shared epic-level product context for this backlog item.

## Problem

The runtime already supports explicit selected-job execution, but it does not yet define how a scheduler or trigger should bind to that execution model.

Without a clear contract, future scheduler work may:

- introduce a second orchestration model outside `job-config.yaml`
- blur the boundary between trigger, execution, retry, and restart behavior
- make operator evidence for why a run started or skipped inconsistent
- accidentally turn the built-in scheduler into a product prerequisite instead of an optional layer

## Goal

Define a narrow schedule and trigger contract that starts scheduled execution from the same explicit selected-job boundary already used for manual runs.

The ETL core must remain directly runnable without this scheduler/control-plane layer.

External schedulers/orchestrators must be able to launch that same contract without adopting OneFlow-native scheduling features first.

## Subject details

Primary user and system subjects in this slice are:

- scheduler/control-plane contributors defining one schedule identity and trigger contract
- external orchestrator adopters launching the same selected-job boundary without OneFlow-native coupling
- operators investigating why a run started, and which launcher initiated it

This slice must freeze the launch contract boundary, not the full scheduler feature set.

## Scope

This item covers:

- the first schedule identity and trigger contract for scenario/job execution
- how a schedule points to one selected runnable job bundle
- trigger-origin expectations for operational evidence
- the boundary between scheduling, orchestration, retry, and restartability
- the interoperability rule that native and external schedulers both target the same selected-job execution boundary

## Out of scope

This item does not cover:

- pause/resume controls themselves
- overlap policy implementation
- missed-run handling implementation
- detailed timezone behavior beyond what the contract must reserve
- replacing direct core ETL execution with a scheduler-only launch path
- an unrelated orchestration platform that bypasses the selected-job contract
- forcing external schedulers to emulate a OneFlow-specific orchestration model just to launch a job

## Proposed approach

The preferred direction is:

1. keep one selected `etl.config.job`-style execution boundary as the runtime contract
2. let schedule definitions point to that same selected job boundary instead of inventing a second orchestration model
3. let external schedulers/orchestrators call that same boundary through a stable launch contract rather than through a separate scheduler-only path
4. allow the first scheduler/control-plane implementation to persist local metadata in a lightweight relational store when that helps developer-laptop and single-node usage, while keeping stronger relational deployment targets open for later phases
5. record trigger origin and schedule identity in run evidence, including when the trigger comes from an external orchestrator
6. keep retry and restart semantics separate so schedule definition does not quietly redefine runtime recovery behavior
7. defer advanced controls such as pause/resume, overlap, and missed-run policy to `S2` and `S3`

## Trigger origin contract matrix

| Trigger source | Launch contract target | Required evidence shape | Guardrail |
|---|---|---|---|
| OneFlow native scheduler | selected job path (`etl.config.job` equivalent) | trigger origin + schedule identity + trigger decision/event id | must not bypass selected-job contract |
| External scheduler/orchestrator | same selected job path | trigger origin marked external + external trigger reference when available | no OneFlow-only orchestration model required |
| Operator ad hoc trigger (UI/API) | same selected job path | trigger origin marked operator ad hoc + trigger decision/event id | remains convenience launcher, not scheduler replacement |

## Operator / runtime impact

Expected impact when this item ships:

- scheduler work stays aligned with the current explicit-job runtime model while the ETL core remains independently runnable
- built-in scheduling remains optional for adopters that prefer external orchestration
- operators can tell why a run started and which schedule triggered it
- later schedule features gain a stable contract instead of layering on ad hoc trigger behavior
- scheduling remains inside the main product roadmap as an optional control-plane capability instead of becoming a second pseudo-product

## Acceptance criteria

- [ ] schedule identity resolves to one selected job execution contract and does not introduce a second orchestration model
- [ ] trigger-origin evidence contract is explicit for native scheduler, external orchestrator, and operator ad hoc launcher paths
- [ ] launch contract keeps scheduling concerns separate from retry/restart semantics already governed by ETL runtime contracts
- [ ] follow-on items `S2`, `S3`, and `S4` can extend controls/history without re-deciding launch boundary semantics
- [ ] `U3` can consume the same trigger contract as an optional launcher without scheduler-only coupling
- [ ] related backlog/architecture documentation is updated to reflect the frozen boundary checkpoint

## Related docs

- [`Product backlog`](../../product-backlog.md)
- [`ADR-0008: formalize control-plane and ETL worker boundary`](../../../adr/control-plane/0008-formalize-control-plane-and-etl-worker-boundary.md)
- [`Control plane and worker boundary`](../../../architecture/control-plane/control-plane-worker-boundary.md)
- [`Scheduler architecture direction`](../../../architecture/control-plane/scheduler-architecture-direction.md)
- [`S4 - Control-plane operational data model`](S4-control-plane-operational-data-model.md)
- [`Scenario-driven runtime direction`](../../../architecture/etl-core/scenario-driven-runtime-direction.md)
- [`Runtime flow`](../../../architecture/etl-core/runtime-flow.md)
- [`U3 - Add guarded trigger-now action from job details without scheduler coupling`](../operator-ui/U3-guarded-trigger-now-from-job-details.md)

## Implementation notes

This item should define the contract, not over-implement it. The key guardrail is that scheduling must launch the same explicit selected-job execution model already used by the runtime today.

The built-in scheduler should therefore be treated as one optional launcher of that contract, not as the only supported launcher.

For early local control-plane work, lightweight relational persistence such as SQLite is acceptable if it helps contributors build scheduler features on a personal laptop without adding infrastructure first. Broader retained OneFlow history and concurrent operator usage can move to stronger relational deployment targets later.

## Status notes

Ready for parallel planning with monitoring-first Operator UI work because `S1` defines the trigger/launch boundary that `U3` depends on.

Contract freeze checkpoint for cross-track work:

- one selected-job launch boundary is fixed across native, external, and ad hoc launchers
- trigger-origin and trigger-identity evidence fields are fixed for run diagnostics
- retry/restart ownership remains on ETL runtime contracts, not schedule definitions


