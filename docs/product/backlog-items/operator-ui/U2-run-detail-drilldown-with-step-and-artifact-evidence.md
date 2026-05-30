# U2 - Add job run detail drill-down with step outcomes and evidence links

## Summary

Add run-detail drill-down in the Operator UI so users can inspect one run's step outcomes, counts, failure summaries, and artifact references without manually correlating multiple log files.

## Current board status

- Epic: **[Epic U](../../epics/operator-ui/epic-u-operator-ui-monitoring-first-mvp.md)**
- Priority: **P1**
- Status: **Ready**
- Milestone: **M2**
- Dependency: **U1, C2**

> Keep these fields synchronized with the row in [`product-backlog.md`](../../product-backlog.md). The execution board remains the canonical source for changing status values.

Use the linked `Epic` entry above to navigate to the shared epic-level product context for this backlog item.

## Problem

Users can see that a run succeeded or failed, but cannot quickly inspect why without digging through scenario logs and correlating step-level records manually.

## Goal

Provide one run-detail page that exposes step outcomes and evidence links from existing monitoring projections.

## Scope

- run-detail page route and model
- step-level status and count rendering
- failure summary visibility
- artifact/evidence link rendering (where available)
- clear handling when optional evidence fields are absent

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

- [ ] run-detail page resolves by job execution id
- [ ] step outcomes and counts are displayed consistently
- [ ] failure summary is visible when present
- [ ] artifact/evidence links are displayed when provided
- [ ] missing optional fields are handled gracefully

## Related docs

- [`Product backlog`](../../product-backlog.md)
- [`Job history and operational observability`](../../../architecture/control-plane/job-history-and-operational-observability.md)
- [`Runtime flow`](../../../architecture/etl-core/runtime-flow.md)
- [`Operator UI MVP API surface`](../../../architecture/control-plane/operator-ui-mvp-api-surface.md)

## Implementation notes

Keep this slice read-only and evidence-first. Any action buttons should remain disabled/hidden until separate action items are accepted.

## Status notes

Planned as the second monitoring-first UI slice after jobs/runs overview.

