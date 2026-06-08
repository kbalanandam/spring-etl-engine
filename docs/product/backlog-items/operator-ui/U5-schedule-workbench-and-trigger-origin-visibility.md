# U5 - Add schedule workbench and run trigger-origin visibility (Manual, Schedule, Event)

## Summary

Add a dedicated Operator UI schedule workbench that lists existing schedules, shows recent schedule-trigger evidence, and surfaces one clear trigger-origin indicator on runs so operators can distinguish Manual vs Schedule now and Event in the next phase.

## Current board status

- Epic: **[Epic U](../../epics/operator-ui/epic-u-operator-ui-monitoring-first-mvp.md)**
- Priority: **P1**
- Status: **Ready**
- Milestone: **M2**
- Dependency: **U4, S2**

> Keep these fields synchronized with the row in [`product-backlog.md`](../../product-backlog.md). The execution board remains the canonical source for changing status values.

Use the linked `Epic` entry above to navigate to the shared epic-level product context for this backlog item.

## Problem

The current job-detail panel exposes one schedule at a time, but operators still lack one place to browse all schedules and quickly answer:

- which schedules are active/paused/disabled
- which triggers came from schedule ticks vs manual actions
- which runs were launched by Manual vs Schedule (and Event next)

Without an explicit trigger-origin model and screen, operations and hypercare workflows rely on fragmented evidence across pages and logs.

## Goal

Ship a bounded schedule workbench and trigger-origin visibility slice that keeps scheduler controls optional while making run origins explicit and auditable.

## Scope

- new schedule-centric view in Operator UI listing existing schedules and state
- bounded schedule actions in that view: open job detail, pause/resume, and guarded ad hoc trigger-now
- clear trigger-origin token rendered in Runs list and Run detail (`Manual`, `Schedule`; `Event` reserved)
- read-model and persistence contract updates needed to carry explicit trigger origin into run projections
- explicit wording that schedule controls remain optional and selected-job boundaries stay intact

## Example operator flow

- open **Schedules** view and find `customer-load-every-minute`
- verify state (`Active`) and next due in UTC/local display
- open recent schedule trigger evidence and confirm `origin=Schedule`
- switch to **Runs** and confirm launched run shows `Trigger origin: Schedule`
- trigger one ad hoc run from job detail and confirm the next run shows `Trigger origin: Manual`
- in the next phase, file-watcher triggers surface as `Trigger origin: Event` without redefining the same run-view contract

## Example low-fidelity UI sketch

```text
Schedules
-------------------------------------------------------------
Key                         Job            Status   Next due
customer-load-every-minute  customer-load  Active   2026-06-08 10:31 local

[Open job] [Pause] [Trigger now]

Recent triggers (selected schedule)
- 10:30:01  origin=Schedule  decision=ACCEPTED  triggerEventId=te-...

Runs
- jobExecutionId=42  scenario=customer-load  Trigger origin=Schedule
- jobExecutionId=43  scenario=customer-load  Trigger origin=Manual
```

## Out of scope

- full event-source/file-watcher implementation (next phase)
- broad schedule authoring beyond bounded existing controls
- making scheduler/control-plane mandatory for ETL execution
- redesigning selected-job runtime launch contracts

## Proposed approach

1. add one schedule workbench route and read-model calls for schedule list + trigger evidence
2. carry explicit trigger-origin classification from trigger events into run projections
3. render trigger-origin labels in Runs list/detail with stable token mapping
4. reserve `Event` as a first-class origin token now so future watcher triggers use the same UI/persistence contract
5. keep wording and docs explicit that this is optional control-plane behavior

## Operator / runtime impact

- operators get one central schedule operations page instead of scattered controls
- trigger and run origin diagnostics become faster during support/hypercare
- run provenance becomes clearer for audits and post-incident analysis
- future event-trigger onboarding is simplified by reusing the same origin contract

## Trade-off Snapshot

- Decision: add trigger-origin visibility now, before full event-trigger runtime implementation
- Benefit: avoids reworking run-list semantics when event triggers arrive
- Cost: one additional UI/read-model slice in the current lane
- Risk: origin labels could drift if persistence mapping is implicit
- Use when: scheduler and manual trigger paths already coexist in operations
- Avoid when: trigger identity contracts are unstable
- Default: explicit origin token in trigger/run persistence plus clear UI labels
- Evidence: schedule screen tests, run-origin projection tests, and operator route checks

## Acceptance criteria

- [ ] Operator UI has a schedule workbench listing existing schedules with state and next due
- [ ] schedule workbench shows recent schedule trigger evidence and bounded actions
- [ ] Runs list and Run detail show explicit trigger origin (`Manual`, `Schedule`)
- [ ] trigger-origin contract reserves `Event` and does not require UI redesign when event triggers ship
- [ ] persistence/read-model flow keeps run origin auditable from trigger event to run projection
- [ ] selected-job and optional-control-plane boundary wording remains explicit

## Related docs

- [`Product backlog`](../../product-backlog.md)
- [`Epic U - Operator UI monitoring-first MVP`](../../epics/operator-ui/epic-u-operator-ui-monitoring-first-mvp.md)
- [`U4 - schedule visibility and pause/resume controls`](./U4-schedule-visibility-and-pause-resume-controls.md)
- [`S2 - Time-based schedule definitions with pause/resume controls`](../scheduler/S2-time-based-schedule-definitions-with-pause-resume.md)
- [`Operator UI MVP API surface`](../../../architecture/control-plane/operator-ui-mvp-api-surface.md)

## Implementation notes

Prefer stable origin tokens in persistence (`MANUAL`, `SCHEDULE`, `EVENT`) and map them to operator labels in UI, while keeping raw token visibility available for diagnostics.

## Status notes

- Added as the next bounded Operator UI slice after `U4` to make schedule operations and run-trigger provenance operationally clear.
- Event triggers remain explicitly next-phase, but this item locks the shared UI and persistence origin contract now.

