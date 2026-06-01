# S4 - Control-plane operational data model

## Summary

Define the optional retained operational data model for the future OneFlow control plane so schedules, watchers, trigger events, run history, step history, evidence lineage, and restartability anchors can be persisted coherently without changing the independently runnable ETL-core launch contract.

## Current board status

- Epic: **[Epic S](../../epics/scheduler/epic-s-scheduling-and-control-plane.md)**
- Priority: **P1**
- Status: **Deferred**
- Milestone: **M3**
- Dependency: **S1, C1, C2**

> Keep these fields synchronized with the row in [`product-backlog.md`](../../product-backlog.md). The execution board remains the canonical source for changing status values.

Use the linked `Epic` entry above to navigate to the shared epic-level product context for this backlog item.

## Problem

The product now has a clear runtime boundary and a growing observability baseline, but it does not yet define the retained operational data model that a future optional control plane would use for scheduler history, watcher history, trigger audit, run lookup, and recovery-oriented operator views.

Without a defined model, later work may drift into:

- schedule and watcher state stored ad hoc per feature
- trigger audit records that do not align cleanly with ETL runtime evidence
- run and step history that is hard to correlate back to schedule identity, watcher identity, or selected config state
- restartability discussions that start from implementation details instead of durable operational anchors
- accidental coupling where the ETL worker appears to require control-plane persistence in order to run

## Goal

Define a narrow but durable operational data model for the optional control plane that can retain schedule, watcher, trigger, run, step, and artifact history while preserving the explicit selected-job runtime contract as the only ETL execution boundary.

## Scope

This item covers:

- schedule identity and retained schedule metadata
- watcher identity, enable/disable state, and monitored-file trigger metadata
- trigger-event history for native scheduler, watcher, manual, and external-orchestrator launches
- run ledger records that link trigger origin to one selected job/scenario run
- step ledger records that preserve ordered step outcomes under a retained run
- retained artifact/evidence lineage such as ingress artifacts, handoff artifacts, published outputs, reject outputs, and archived-source paths where relevant
- retained config identity / selected-job identity fields needed for audit and diagnosis
- restartability anchors such as prior-run linkage, attempt lineage, checkpoint references, or resumable markers without defining final restart semantics yet
- local-first relational persistence direction for early control-plane slices

## Out of scope

This item does not cover:

- making persisted control-plane data mandatory for normal ETL-core execution
- final restart/resume semantics per execution mode
- one final database vendor or final vendor-specific DDL/migration-tool choice
- a final UI, dashboard, or API contract
- replacing runtime logs as the current evidence source of truth
- changing the `etl.config.job` / `job-config.yaml` launch contract
- forcing external schedulers or orchestrators to adopt a OneFlow-native scheduler identity model just to launch jobs

## Proposed approach

The preferred direction is:

1. keep the ETL worker runtime independently runnable and able to execute without any control-plane database present
2. define the control-plane persistence model as an optional retained-history layer around the same selected-job launch contract
3. model schedule definitions, watcher definitions, and trigger events as separate but correlated operational records
4. model one retained run ledger entry per launch attempt or resolved run outcome, including skipped, blocked, failed, and completed outcomes where applicable
5. model step ledger entries under the retained run so ordered step results, counts, and failure details remain queryable without replacing raw runtime logs
6. preserve artifact and evidence references explicitly so operators can trace input files, intermediate handoffs, final outputs, reject outputs, and archived originals where the runtime exposes them
7. carry config identity and trigger origin through the retained model so later UI, audit, and support workflows can answer why a run started and which configuration it used
8. keep restartability anchors limited to retained identifiers and attempt relationships first, leaving full restart semantics to `F1`
9. allow early local or single-node implementations to use lightweight relational persistence such as SQLite, while keeping PostgreSQL, SQL Server, and MySQL targets open for later phases
10. ensure native scheduler-triggered runs and externally orchestrated runs can both be represented in the same retained model

## Operator / runtime impact

Expected impact when this item ships:

- scheduler and watcher history gain one coherent retained model instead of separate ad hoc records
- operators can navigate from schedule or watcher activity to trigger history, run history, step outcomes, and artifact evidence more directly
- later UI and API work gains a stable operational-history foundation
- restartability and rerun discussions can build on persisted attempt lineage instead of only text logs
- the optional-control-plane rule remains intact because the ETL worker still runs directly with no persistence dependency

## Acceptance criteria

- [ ] the retained control-plane entity set is documented clearly enough to distinguish schedules, watchers, trigger events, runs, steps, and artifacts
- [ ] the model preserves the independently runnable ETL-core boundary and does not require persistence for direct worker execution
- [ ] the retained model accommodates both OneFlow-native triggers and external-orchestrator trigger origins
- [ ] run and step history fields are defined well enough to support later operator search, schedule-to-run traceability, and evidence drill-down
- [ ] artifact lineage and config identity expectations are documented well enough for audit and diagnosis planning
- [ ] restartability anchors are documented without prematurely claiming one final restart/resume behavior
- [ ] local-first relational persistence direction is documented without locking the product to one permanent storage engine, while preserving later portability to PostgreSQL, SQL Server, and MySQL

## Related docs

- [`Product backlog`](../../product-backlog.md)
- [`ADR-0008: formalize control-plane and ETL worker boundary`](../../../adr/control-plane/0008-formalize-control-plane-and-etl-worker-boundary.md)
- [`Control plane and worker boundary`](../../../architecture/control-plane/control-plane-worker-boundary.md)
- [`Control-plane operational data model`](../../../architecture/control-plane/control-plane-operational-data-model.md)
- [`Control-plane local relational schema`](../../../architecture/control-plane/control-plane-local-relational-schema.md)
- [`Scheduler ER model artifact`](../../../architecture/control-plane/control-plane-local-relational-schema.md#scheduler-er-model-artifact)
- [`Scheduler architecture direction`](../../../architecture/control-plane/scheduler-architecture-direction.md)
- [`Operator UI architecture direction`](../../../architecture/operator-ui/operator-ui-architecture-direction.md)
- [`S1 - Schedule model and trigger contract`](S1-schedule-model-and-trigger-contract.md)
- [`Job history and operational observability`](../../../architecture/control-plane/job-history-and-operational-observability.md)
- [`Runtime flow`](../../../architecture/etl-core/runtime-flow.md)

## Implementation notes

This item should define the retained operational model before implementation pressure scatters schedule, watcher, and run-history persistence across unrelated tables or feature-local storage.

The first pass does not need a final schema or a large implementation. It only needs a stable enough model that later scheduler, watcher, UI, audit, and recovery work can build on the same concepts.

When this item is implemented, keep one rule explicit: persisted control-plane data augments runtime evidence and operator workflows, but it does not become a prerequisite for launching the ETL worker.

## Status notes

Deferred today, but important enough to document before optional scheduler, watcher, and retained-history work grows in multiple directions without one shared operational data model.




