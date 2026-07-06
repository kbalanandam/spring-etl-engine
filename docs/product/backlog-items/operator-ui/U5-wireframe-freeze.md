# U5 wireframe freeze - schedule workbench and trigger-origin visibility

## Purpose

Freeze the Operator UI look-and-flow contract for `U5` before implementation changes continue.

This note captures low-fidelity screen shape, control placement, and wording intent so code review can validate behavior against one agreed UI target.

## Status

- Effective date: **2026-07-06**
- Applies to: **`U5` implementation lane**
- Change policy: **update this document first, then code** for any structural UI change

## Shared shell constraints

Keep these shell rules stable during `U5`:

- top-level tabs remain `Jobs`, `Schedules`, `Runs`
- active tab uses minimal emphasis only (connected-tab style and readable text weight)
- do not re-introduce redundant in-canvas page title ribbons
- timezone selector stays in page-level controls where context is local

## Frozen screen A - Schedules workbench

```text
+------------------------------------------------------------------------------------------------+
| Tabs: Jobs | Schedules (active) | Runs                                                         |
+------------------------------------------------------------------------------------------------+
| Schedules controls: [Search schedules] [Status] [Job] [Timezone] [Refresh]                    |
+------------------------------------------------------------------------------------------------+
| Schedules table                                                                                |
|------------------------------------------------------------------------------------------------|
| Schedule key                 | Job             | State    | Next due (local)    | Actions       |
| customer-load-every-minute   | customer-load   | Active   | 2026-07-06 10:31    | Open Pause ...|
| nightly-xml-roundtrip        | xml-roundtrip   | Paused   | --                  | Open Resume...|
+------------------------------------------------------------------------------------------------+
| Recent trigger evidence (selected schedule)                                                    |
| - 10:30:01 origin=Schedule decision=ACCEPTED triggerEventId=te-...                             |
| - 10:29:01 origin=Schedule decision=DROPPED_DUPLICATE triggerEventId=te-...                    |
+------------------------------------------------------------------------------------------------+
```

### Frozen behavior

- table-first workbench, row selects schedule context
- bounded actions only: `Open job`, `Pause`/`Resume`, `Trigger now`
- no schedule authoring CRUD in this slice
- recent trigger evidence is visible without leaving the workbench

## Frozen screen B - Runs trigger-origin visibility

```text
+------------------------------------------------------------------------------------------------+
| Tabs: Jobs | Schedules | Runs (active)                                                         |
+------------------------------------------------------------------------------------------------+
| Runs controls: [Job] [Status] [Trigger origin] [Time range] [Rows] [Prev] [Page] [Next]       |
+------------------------------------------------------------------------------------------------+
| Runs table                                                                                      |
|------------------------------------------------------------------------------------------------|
| Summary (job/run mode/recovery) | Status | Trigger origin | Start time | Duration | Actions     |
| customer-load ...               | Success| Schedule       | 10:30      | 2.1s     | View detail |
| customer-load ...               | Success| Manual         | 10:27      | 1.9s     | View detail |
+------------------------------------------------------------------------------------------------+
```

### Frozen behavior

- `Trigger origin` is explicit in list and run detail
- UI labels are title-case: `Manual`, `Schedule`, `Event`
- current phase renders `Manual` and `Schedule`; `Event` is reserved contract token
- responsive behavior can collapse low-priority columns, but trigger-origin context must remain visible

## Run detail trigger context (frozen excerpt)

```text
Trigger context
- Trigger origin: Schedule
- Trigger event id: te-20260706-103001-00042
- Schedule key: customer-load-every-minute
```

## Contract and naming guardrails

- persistence tokens remain stable: `MANUAL`, `SCHEDULE`, `EVENT`
- UI mapping remains stable: `Manual`, `Schedule`, `Event`
- do not encode origin meaning in color alone; always render text

## Definition of done against this freeze

- implemented UI can be traced to both frozen screens above
- smoke tests assert schedule workbench rendering and trigger-origin visibility
- any post-freeze structural delta updates this document in the same change

## Related docs

- [`U5 backlog item`](./U5-schedule-workbench-and-trigger-origin-visibility.md)
- [`Epic U`](../../epics/operator-ui/epic-u-operator-ui-monitoring-first-mvp.md)
- [`Operator UI MVP API surface`](../../../architecture/control-plane/operator-ui-mvp-api-surface.md)

