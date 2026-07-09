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
| customer-load-every-minute   | customer-load   | Active   | 2026-07-06 10:31    | ⓘ ↗ ▶ (hover for tooltip)|
| nightly-xml-roundtrip        | xml-roundtrip   | Paused   | --                  | ⓘ ↗ ▶ (hover for tooltip)|
+------------------------------------------------------------------------------------------------+
| No schedule selected by default. Use explicit row actions to open history or job context.      |
+------------------------------------------------------------------------------------------------+
```

### Frozen behavior

- table-first workbench with no default selection or implicit row navigation
- bounded actions only: compact symbols (`ⓘ`, `↗`, `▶`) with hover tooltip labels (`Details`, `Open job`, `Trigger now`)
- no schedule authoring CRUD in this slice
- recent trigger evidence stays on the dedicated schedule detail page for this phase
- schedule state changes (`Enable`/`Disable`, `Pause`/`Resume`) live on schedule detail to avoid duplicated control points

## Frozen screen A1 - Schedule detail trigger history

```text
+------------------------------------------------------------------------------------------------+
| Schedule detail: customer-load-every-minute                                                    |
+------------------------------------------------------------------------------------------------+
| Summary: job | state | expression | timezone | next due                                        |
+------------------------------------------------------------------------------------------------+
| Actions: [Edit] [Disable] [Pause]                                                              |
+------------------------------------------------------------------------------------------------+
| [Show recent triggers]                                                                         |
| - 10:30:01 origin=Schedule launch=CONFIRMED     triggerEventId=te-... launchedRunId=42        |
| - 10:29:01 origin=Schedule launch=NOT_CONFIRMED triggerEventId=te-...                          |
+------------------------------------------------------------------------------------------------+
```

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






