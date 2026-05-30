# U1 - Stand up independent monitoring-first Operator UI shell with jobs and runs list views

## Summary

Create the first independent Operator UI shell so users can view jobs and recent runs in one centralized interface, while keeping ETL-core execution unchanged and optional control-plane usage additive.

## Current board status

- Epic: **[Epic U](../../epics/operator-ui/epic-u-operator-ui-monitoring-first-mvp.md)**
- Priority: **P1**
- Status: **In Progress**
- Milestone: **M2**
- Dependency: **C1**

> Keep these fields synchronized with the row in [`product-backlog.md`](../../product-backlog.md). The execution board remains the canonical source for changing status values.

Use the linked `Epic` entry above to navigate to the shared epic-level product context for this backlog item.

## Problem

Operators currently depend on logs and script output to understand what jobs exist and what recent runs happened, which slows triage and makes day-to-day visibility harder for non-developer users.

## Goal

Provide one minimal centralized UI entry point that lists jobs and recent runs from existing control-plane read models without introducing runtime coupling.

## Subject details

Primary user subject in this slice is the operations user who asks:

- what jobs are currently known to the platform
- which runs happened recently and what their high-level status is
- whether a run needs deeper investigation in the next step (`U2`)

The page should prioritize quick situational awareness over deep drill-down.

## Scope

- independent UI shell and route structure
- jobs list view
- recent runs list view
- read-only status and timestamp visibility
- basic API error handling and empty-state behavior

## Example UI actions

- open **Jobs** page and view all known job bundles with readiness/status hints
- filter jobs by text search (job name/key)
- open **Runs** page and view recent runs with status, start time, and duration summary
- sort runs by newest first to identify current incidents quickly
- select one run row to navigate into detailed diagnosis flow (`U2`)

## Out of scope

- run-detail drill-down (handled in `U2`)
- trigger, schedule, or authoring actions
- scheduler persistence redesign
- changing ETL worker launch/runtime boundaries

## Proposed approach

Use the monitoring-first API starter as the data source and ship a read-only UI slice first. Keep all launch/trigger behavior unchanged.

## Operator / runtime impact

- operators get faster visibility into what ran and current status
- no ETL runtime contract changes
- no new required config for direct selected-job execution

## Trade-off Snapshot

- Decision: ship a read-only UI shell first
- Benefit: quick user-visible value with low runtime risk
- Cost: action workflows remain outside the UI initially
- Risk: users may expect scheduler controls before backend contracts are ready
- Use when: team needs centralized observability before control features
- Avoid when: schedule/trigger governance is the immediate priority
- Default: read-only views backed by existing API projections
- Evidence: UI routes and API integration tests for jobs/runs listing

## Acceptance criteria

- [ ] UI shell launches independently from ETL worker runtime
- [ ] jobs list view renders from control-plane API read model
- [ ] recent runs list view renders status and key timestamps
- [ ] empty/error states are explicit and non-blocking
- [ ] documentation links users from UI pages to runtime evidence sources

## Related docs

- [`Product backlog`](../../product-backlog.md)
- [`Operator UI architecture direction`](../../../architecture/operator-ui/operator-ui-architecture-direction.md)
- [`Angular UI MVP structure`](../../../architecture/operator-ui/angular-ui-mvp-structure.md)
- [`Operator UI MVP API surface`](../../../architecture/control-plane/operator-ui-mvp-api-surface.md)

## Implementation notes

Keep this slice focused on observability. Do not merge trigger/scheduler controls into the same delivery item.

## Status notes

Started with a first thin shell slice: `/operator` entry route, Jobs/Runs read-only list rendering from existing control-plane APIs, explicit empty/error states, and a run-row click-through placeholder route (`#/runs/{jobExecutionId}`) for U2 preparation.

