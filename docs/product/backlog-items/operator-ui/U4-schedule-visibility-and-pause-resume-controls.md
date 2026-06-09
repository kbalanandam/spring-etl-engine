# U4 - Add schedule visibility and pause/resume controls in Operator UI without making native scheduling mandatory

## Summary

Add the first bounded schedule-management surface to the Operator UI so operators can see native schedule state and invoke explicit pause/resume controls without changing the selected-job ETL runtime contract or making OneFlow-native scheduling a prerequisite.

## Current board status

- Epic: **[Epic U](../../epics/operator-ui/epic-u-operator-ui-monitoring-first-mvp.md)**
- Priority: **P1**
- Status: **Ready**
- Milestone: **M2**
- Dependency: **S2, U3**

> Keep these fields synchronized with the row in [`product-backlog.md`](../../product-backlog.md). The execution board remains the canonical source for changing status values.

Use the linked `Epic` entry above to navigate to the shared epic-level product context for this backlog item.

## Problem

The Operator UI can already monitor jobs and runs and can issue guarded ad hoc triggers, but it does not yet expose the state of native schedules or let operators pause/resume those schedules from the same bounded control surface.

Without that slice, scheduler growth and UI growth can drift apart:

- scheduler contracts may evolve without operator-facing proof that the controls remain understandable
- UI work may stay monitoring-only and fail to exercise the first native-scheduling boundary in a realistic operator flow
- teams cannot validate in one release lane that ETL, scheduler, and UI capabilities still grow together without collapsing boundaries

## Goal

Provide one explicit UI slice that exposes schedule state and guarded pause/resume actions for native schedules while preserving:

- the same selected-job launch boundary frozen by `S1`
- the control-plane boundary rule from `S2`
- the existing UI guardrail that direct ETL execution remains valid without the UI

## Scope

- schedule-state visibility in the Operator UI for jobs that have native schedule definitions
- operator-readable rendering of schedule status such as active/paused and next-run intent when available
- guarded pause/resume actions aligned with `S2` controls
- explicit empty-state handling when a job has no native schedule or native scheduling is disabled
- wording and evidence links that keep native scheduling bounded

## Example operator flow

- open one job detail page that already supports guarded ad hoc trigger behavior from `U3`
- view a dedicated schedule panel showing `Active` or `Paused` plus next-run intent when the native scheduler is enabled
- choose **Pause schedule**, confirm the action, and see a clear success/error response without changing ad hoc trigger wording
- revisit the page later and confirm that the schedule panel now shows `Paused`
- open a job with no native schedule and see an explicit empty state instead of a broken or misleading control panel

## Example low-fidelity UI sketch

Illustrative only; this is a backlog-level clarity aid, not a final design:

```text
Job Detail: customer-load
-------------------------------------------------
Run actions
[ Trigger now ]

Native schedule
Status: Active
Next run: 2026-06-08 02:00 America/Chicago
Schedule id: sched_customer_load_daily
[ Pause schedule ]

Notes
- Native scheduling is bounded.
- Direct selected-job execution remains valid.
```

## Out of scope

- schedule authoring/CRUD beyond pause/resume for the first slice
- overlap or missed-run policy controls (`S3`)
- making OneFlow-native scheduling mandatory for ETL execution
- replacing external scheduler/orchestrator support
- widening the UI into a general orchestration designer

## Proposed approach

Build this slice only on top of the `S1`/`S2` scheduler boundary:

1. surface native schedule state only when control-plane APIs project that data
2. use guarded pause/resume actions rather than broad schedule editing in the first slice
3. preserve explicit wording that these controls apply to native schedules only
4. keep ad hoc trigger (`U3`) and schedule controls clearly separated in UI copy and evidence
5. render stable trigger/schedule identifiers so operators can correlate schedule actions with later runs

Balanced-growth pairing for this slice:

- `A7` proves ETL/runtime extensibility without changing the selected-job launch boundary
- `S2` provides the native schedule state/action contract this UI slice depends on
- `U4` gives operator-facing proof that scheduler growth stays understandable and bounded in the UI

## Operator / runtime impact

- operators get one bounded place to inspect whether a native schedule is active or paused
- pause/resume intent becomes observable without requiring direct database or API inspection
- the ETL worker launch contract remains unchanged and independently runnable
- external schedulers remain valid because the UI is exposing native scheduling, not redefining the launch contract

## Trade-off Snapshot

- Decision: add schedule visibility and pause/resume before broader schedule authoring
- Benefit: proves scheduler/UI parity with a small, understandable control slice
- Cost: first schedule UI remains intentionally narrow
- Risk: users may over-assume full scheduler governance if wording is not explicit
- Use when: the team needs balanced cross-capability proof that scheduler and UI can grow together safely
- Avoid when: native schedule state/action APIs are still unstable
- Default: read-mostly schedule state plus guarded pause/resume only
- Evidence: scheduler API tests, UI route/action tests, and operator evidence showing schedule-state transitions

## Acceptance criteria

- [ ] jobs with native schedule definitions show explicit schedule state in the Operator UI
- [ ] pause/resume actions are guarded, operator-visible, and aligned with `S2`
- [ ] jobs without native schedules render a clear empty state
- [ ] UI wording keeps native scheduling bounded and preserves direct selected-job execution as a valid path
- [ ] schedule state and actions remain distinguishable from ad hoc trigger-now behavior shipped in `U3`
- [ ] related backlog and architecture docs stay aligned with the first bounded schedule-control slice

## Related docs

- [`Product backlog`](../../product-backlog.md)
- [`Epic U - Operator UI monitoring-first MVP`](../../epics/operator-ui/epic-u-operator-ui-monitoring-first-mvp.md)
- [`S1 - Schedule model and trigger contract`](../scheduler/S1-schedule-model-and-trigger-contract.md)
- [`S2 - Time-based schedule definitions with pause/resume controls`](../scheduler/S2-time-based-schedule-definitions-with-pause-resume.md)
- [`Control plane and worker boundary`](../../../architecture/control-plane/control-plane-worker-boundary.md)
- [`Operator UI MVP API surface`](../../../architecture/control-plane/operator-ui-mvp-api-surface.md)

## Implementation notes

Keep the first slice narrow and evidence-first: show schedule state, expose guarded pause/resume, and avoid broad schedule CRUD or policy editing until `S3` and later scheduler work are ready.

## Status notes

- Added as the first planned post-MVP Operator UI slice so ETL, scheduler, and UI capability growth can be tested in parallel rather than only serially.
- Intended pairing for the next balanced delivery lane: one ETL slice, one scheduler slice, and one UI slice progressing together on the same control-plane boundaries.


