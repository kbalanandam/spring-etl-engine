# U3 - Add guarded trigger-now action from job details without scheduler coupling

## Summary

Add a guarded "trigger now" action from job details so operators can request one run from the UI while preserving explicit selected-job boundaries and avoiding mandatory scheduler coupling.

## Current board status

- Epic: **[Epic U](../../epics/operator-ui/epic-u-operator-ui-monitoring-first-mvp.md)**
- Priority: **P1**
- Status: **Ready**
- Milestone: **M2**
- Dependency: **U1, S1**

> Keep these fields synchronized with the row in [`product-backlog.md`](../../product-backlog.md). The execution board remains the canonical source for changing status values.

Use the linked `Epic` entry above to navigate to the shared epic-level product context for this backlog item.

## Problem

Operators need a simple way to request a run from the same UI used for monitoring, but early UI action design can accidentally blur scheduling, triggering, and execution-boundary responsibilities.

## Goal

Introduce a constrained trigger-now action that records/returns a trigger decision and keeps the selected-job execution contract explicit.

## Scope

- trigger-now button/action in job-detail view
- confirmation and response feedback UX
- request/response mapping to control-plane trigger endpoint
- explicit guardrails in UI wording and docs

## Out of scope

- schedule CRUD and pause/resume controls
- overlap policy and missed-run behavior
- changing worker launch semantics
- converting UI into a mandatory launch path

## Proposed approach

Integrate the existing trigger-now API in a guarded manner and keep messaging explicit that this is an optional launcher around the same selected-job contract.

## Operator / runtime impact

- operators can initiate controlled ad hoc runs from UI
- better centralized operations experience
- no requirement for UI/scheduler to launch ETL jobs in existing paths

## Trade-off Snapshot

- Decision: allow trigger-now before full scheduler management
- Benefit: useful action capability with limited scope and risk
- Cost: partial control-plane UX until schedule features arrive
- Risk: users may infer scheduler completeness too early
- Use when: ad hoc trigger convenience is needed
- Avoid when: governance requires full schedule policy controls first
- Default: guarded action + explicit contract wording
- Evidence: endpoint tests and UI action flow tests

## Acceptance criteria

- [ ] job-detail view supports guarded trigger-now action
- [ ] action feedback includes a traceable trigger-event identifier
- [ ] UI messaging preserves selected-job contract boundaries
- [ ] action failures are categorized and visible to operator
- [ ] docs call out that scheduler capability remains optional

## Related docs

- [`Product backlog`](../../product-backlog.md)
- [`S1 - Schedule model and trigger contract`](../scheduler/S1-schedule-model-and-trigger-contract.md)
- [`Control plane and worker boundary`](../../../architecture/control-plane/control-plane-worker-boundary.md)
- [`Operator UI MVP API surface`](../../../architecture/control-plane/operator-ui-mvp-api-surface.md)

## Implementation notes

Treat this as an optional launcher convenience, not as a replacement for existing explicit-job execution paths.

## Status notes

Planned after U1 baseline visibility is in place.

