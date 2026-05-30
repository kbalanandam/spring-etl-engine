# U3 - Add guarded trigger-now action from job details without scheduler coupling

## Summary

Add a guarded "trigger now" action from job details so operators can request one run from the UI while preserving explicit selected-job boundaries and avoiding mandatory scheduler coupling.

## Current board status

- Epic: **[Epic U](../../epics/operator-ui/epic-u-operator-ui-monitoring-first-mvp.md)**
- Priority: **P1**
- Status: **In Progress**
- Milestone: **M2**
- Dependency: **U1, S1**

> Keep these fields synchronized with the row in [`product-backlog.md`](../../product-backlog.md). The execution board remains the canonical source for changing status values.

Use the linked `Epic` entry above to navigate to the shared epic-level product context for this backlog item.

## Problem

Operators need a simple way to request a run from the same UI used for monitoring, but early UI action design can accidentally blur scheduling, triggering, and execution-boundary responsibilities.

## Goal

Introduce a constrained trigger-now action that records/returns a trigger decision and keeps the selected-job execution contract explicit.

## Subject details

Primary user subject in this slice is controlled ad hoc execution:

- request one run intentionally from the UI
- capture a traceable trigger decision/event id for auditability
- avoid implying full scheduler governance is already shipped

This action must remain clearly scoped as optional operator convenience.

## Scope

- trigger-now button/action in job-detail view
- confirmation and response feedback UX
- request/response mapping to control-plane trigger endpoint
- explicit guardrails in UI wording and docs

## Example UI actions

- open job detail page and click **Trigger now**
- review confirmation dialog that explains this is an ad hoc trigger, not schedule management
- submit trigger request and receive trigger-event identifier in success toast/panel
- open trigger events for the job and confirm the new event is visible
- handle failed trigger request with categorized error feedback (validation/config/runtime)

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
- [ ] trigger-now request/response contract aligns with the `S1` trigger-origin boundary

## Related docs

- [`Product backlog`](../../product-backlog.md)
- [`S1 - Schedule model and trigger contract`](../scheduler/S1-schedule-model-and-trigger-contract.md)
- [`Control plane and worker boundary`](../../../architecture/control-plane/control-plane-worker-boundary.md)
- [`Operator UI MVP API surface`](../../../architecture/control-plane/operator-ui-mvp-api-surface.md)

## Implementation notes

Treat this as an optional launcher convenience, not as a replacement for existing explicit-job execution paths.

## Status notes

Started after U1/U2 monitoring baseline delivery with a guarded UI-first trigger-now kickoff.

Current progress:

- added guarded `Trigger now` action in job-detail UI with explicit ad hoc wording and selected-job boundary note
- wired POST request mapping to `/api/v1/jobs/{jobKey}:trigger-now` with confirmation and decision feedback including `triggerEventId`
- added categorized failure feedback (`validation` / `config` / `runtime`) in the UI without introducing scheduler-management controls

Handshake with `S1`:

- proceed when S1 trigger contract freeze checkpoint is documented
- consume the same selected-job launch boundary used by scheduler and external orchestrator launchers
- preserve explicit operator wording that this remains ad hoc trigger convenience, not full scheduler governance

