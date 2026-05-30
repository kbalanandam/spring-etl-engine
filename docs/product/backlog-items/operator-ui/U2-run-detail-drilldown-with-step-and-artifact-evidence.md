# U2 - Add job run detail drill-down with step outcomes, evidence links, and run-scoped log viewer

## Summary

Add run-detail drill-down in the Operator UI so users can inspect one run's step outcomes, counts, failure summaries, artifact references, and run-scoped log lines without manually correlating multiple log files.

## Current board status

- Epic: **[Epic U](../../epics/operator-ui/epic-u-operator-ui-monitoring-first-mvp.md)**
- Priority: **P1**
- Status: **In Progress**
- Milestone: **M2**
- Dependency: **U1, C2**

> Keep these fields synchronized with the row in [`product-backlog.md`](../../product-backlog.md). The execution board remains the canonical source for changing status values.

Use the linked `Epic` entry above to navigate to the shared epic-level product context for this backlog item.

## Problem

Users can see that a run succeeded or failed, but cannot quickly inspect why without digging through scenario logs and correlating step-level records manually.

## Goal

Provide one run-detail page that exposes step outcomes, evidence links, and run-instance-focused logs from existing monitoring projections.

## Subject details

Primary user subject in this slice is run-level diagnosis:

- why this run failed or succeeded
- which step failed/blocked and with what counts
- where the operator can find related evidence artifacts quickly

The page should make failure triage faster than manual log correlation.

## Scope

- run-detail page route and model
- step-level status and count rendering
- failure summary visibility
- artifact/evidence link rendering (where available)
- richer in-page log rendering (not raw plain-text fallback only)
- run-instance-scoped log extraction so one run view does not show full scenario history
- clear handling when optional evidence fields are absent

## Example UI actions

- open a run from the runs list and land on a **Run Detail** page
- expand step list to inspect status per step (`COMPLETED`, `FAILED`, `BLOCKED`)
- inspect step counts (source/written/rejected) without switching screens
- view failure summary and category to identify likely root-cause area
- click evidence/artifact links (when present) to inspect reject/output/log references

## Out of scope

- schedule controls and authoring
- restart/resume behavior changes
- persistence schema redesign
- modifying ETL runtime logging contracts in this slice

## Proposed approach

Build on the existing run-detail API response and focus on transparent, operator-readable rendering rather than new backend semantics.

## Operator / runtime impact

- faster diagnosis from one UI page
- fewer ad hoc log-correlation steps for support
- no ETL runtime contract changes

## Trade-off Snapshot

- Decision: surface backend-projected detail as-is before advanced UI enrichment
- Benefit: faster delivery and clearer contract between API and UI
- Cost: first UI may feel utilitarian
- Risk: inconsistent artifact availability across scenarios may confuse users
- Use when: operator diagnosis speed is the immediate goal
- Avoid when: polished reporting UX is mandatory for first release
- Default: render available fields with explicit "not available" labels
- Evidence: run-detail endpoint tests and UI route rendering tests

## Acceptance criteria

- [x] run-detail page resolves by job execution id
- [x] step outcomes and counts are displayed consistently
- [x] failure summary is visible when present
- [x] artifact/evidence links are displayed when provided
- [x] missing optional fields are handled gracefully
- [ ] run-detail includes richer log rendering (structured/filtered view instead of raw full-file text)
- [ ] run-detail log content is scoped to the selected run instance (for example by `jobExecutionId` and run correlation context), not full scenario-history output

## Related docs

- [`Product backlog`](../../product-backlog.md)
- [`Job history and operational observability`](../../../architecture/control-plane/job-history-and-operational-observability.md)
- [`Runtime flow`](../../../architecture/etl-core/runtime-flow.md)
- [`Operator UI MVP API surface`](../../../architecture/control-plane/operator-ui-mvp-api-surface.md)

## Implementation notes

Keep this slice read-only and evidence-first. Any action buttons should remain disabled/hidden until separate action items are accepted.

## Status notes

Completed as the second monitoring-first UI slice after jobs/runs overview.

Shipped scope:

- replaced U1 run-detail placeholder panel with read-only sections for run summary, step outcomes/counts, failure summary, artifacts, and evidence links
- preserved graceful "not available" handling for optional fields when data is absent
- kept route/API contract unchanged on `/api/v1/runs/{jobExecutionId}/detail`
- added first-pass UX polish for section readability (timestamps, richer artifact/evidence text, safer missing-link handling)

