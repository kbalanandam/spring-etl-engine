# Operator UI MVP API Surface

## Purpose

This document defines a practical first API surface for the optional control plane to support the Angular operator UI MVP screens.

It exists to freeze a small, explicit backend contract for UI delivery without changing the existing selected-job runtime boundary.

## Status

- Classification: **Future direction**
- This note still carries future-direction design intent, but the monitoring-first subset below is now implemented by the optional `com.etl.controlplane.ControlPlaneApiApplication` starter.
- Implemented now: `GET /api/v1/jobs`, `GET /api/v1/jobs/{jobKey}`, `POST /api/v1/jobs/{jobKey}:trigger-now`, `GET /api/v1/jobs/{jobKey}/trigger-events`, `GET /api/v1/runs`, `GET /api/v1/runs/{jobExecutionId}`, `GET /api/v1/runs/{jobExecutionId}/detail`, `GET /api/v1/runs/{jobExecutionId}/log`, `GET /api/v1/schedules`, `GET /api/v1/schedules/{scheduleId}`, `POST /api/v1/schedules`, `PUT /api/v1/schedules/{scheduleId}`, `POST /api/v1/schedules/{scheduleId}:enable`, `POST /api/v1/schedules/{scheduleId}:disable`, `POST /api/v1/schedules/{scheduleId}:pause`, `POST /api/v1/schedules/{scheduleId}:resume`, `GET /api/v1/schedules/{scheduleId}/trigger-events`, `GET /api/v1/system/health`, and `GET /api/v1/system/info`.
- Trigger-event history now persists in the control-plane JDBC store when `controlplane.triggers.persistence.mode=jdbc` (control-plane profile default), with memory mode still available as a fallback.
- Trigger-event persistence mode switches are startup-guarded: when the prior marker mode differs from the current configured mode (`jdbc` <-> `memory`), startup fails fast unless `controlplane.triggers.persistence.allow-mode-switch=true` is set intentionally.
- Run-summary history for `/runs` and `/runs/{jobExecutionId}` now persists in the control-plane JDBC store when `controlplane.runs.persistence.mode=jdbc` (control-plane profile default), while `/runs/{jobExecutionId}/detail` remains log-projected.
- Schedule persistence foundation now exists internally in the control-plane JDBC store when `controlplane.schedules.persistence.mode=jdbc` (control-plane profile default).
- Schedule trigger-event history now resolves by `scheduleId` in the trigger registry.
- Optional scheduler tick evaluation can now record schedule-origin trigger events when `controlplane.scheduler.enabled=true`; default remains disabled for explicit opt-in.
- Scheduler dedup now persists the last accepted due instant per schedule so duplicate ticks are suppressed across control-plane restarts.
- Watermark advancement is claimed atomically per schedule due instant, reducing duplicate schedule ticks when multiple control-plane pollers overlap.
- Scheduler missed-run behavior is now policy-driven with `controlplane.scheduler.missed-run-policy` (`SKIP` default, optional `CATCH_UP_ONCE` or `CATCH_UP_ALL`) plus `controlplane.scheduler.max-catch-up-iterations` safety bounds.
- Scheduler overlap behavior is now policy-driven with `controlplane.scheduler.overlap-policy` (`ALLOW` default, optional `SERIALIZE`).
- `GET /api/v1/system/info` now exposes scheduler governance defaults (`schedulerEnabled`, `schedulerMissedRunPolicy`, `schedulerOverlapPolicy`).
- `GET /api/v1/schedules*` responses now expose persisted scheduler watermark state through `lastAcceptedDueAt` alongside computed `nextDueAt`.
- `GET /api/v1/runs*` responses now expose additive F1 restart-contract evidence fields (`runMode`, `recoveryPolicy`) when present in `RUN_SUMMARY` projections.

## Scope

This document covers:

- a minimal endpoint set for Jobs, Runs, Run detail, Schedules, and System screens
- request and response view models aligned with existing architecture terms
- operator-action endpoints for trigger-now and basic schedule management
- API-level guardrails that preserve the selected-job runtime contract

This document does **not** define:

- final auth/RBAC semantics
- final pagination, sorting, or filtering grammar
- final API gateway or deployment topology
- final restart/replay APIs

## Context

The UI and scheduler architecture notes already preserve these rules:

1. the ETL worker remains independently runnable
2. control-plane services are optional
3. any UI-triggered execution must resolve to the same selected-job launch contract

The first API surface should therefore be small, explicit, and contract-first.

## API principles

Use these principles for the MVP API surface:

- **Boundary-first** - APIs trigger or observe runs, but do not bypass worker launch validation
- **View-model aligned** - responses project from `JobBundleSummaryView`, `RunRecordView`, `RunDetailView`, `ScheduleView`, and `TriggerEventView`
- **Read-first** - prioritize reliable read models before complex operator mutation flows
- **Portable semantics** - keep payload semantics stable across local SQLite-first and later relational targets
- **Optional control plane** - API availability augments operations; it is not a worker prerequisite

## Versioning and base path

Use one explicit base path for the first cut:

```text
/api/v1
```

Machine-readable contract file:

- [`operator-ui-mvp-openapi.yaml`](operator-ui-mvp-openapi.yaml)

Suggested resource groups:

- `/jobs`
- `/runs`
- `/schedules`
- `/system`

## Endpoint summary

```text
GET    /api/v1/jobs
GET    /api/v1/jobs/{jobKey}
POST   /api/v1/jobs/{jobKey}:trigger-now
GET    /api/v1/jobs/{jobKey}/trigger-events

GET    /api/v1/runs
GET    /api/v1/runs/{jobExecutionId}
GET    /api/v1/runs/{jobExecutionId}/detail
GET    /api/v1/runs/{jobExecutionId}/log

GET    /api/v1/schedules
GET    /api/v1/schedules/{scheduleId}
POST   /api/v1/schedules
PUT    /api/v1/schedules/{scheduleId}
POST   /api/v1/schedules/{scheduleId}:enable
POST   /api/v1/schedules/{scheduleId}:disable
POST   /api/v1/schedules/{scheduleId}:pause
POST   /api/v1/schedules/{scheduleId}:resume
GET    /api/v1/schedules/{scheduleId}/trigger-events

GET    /api/v1/system/health
GET    /api/v1/system/info
```

## Jobs endpoints

### `GET /api/v1/jobs`

Returns job bundle summaries for the Jobs list screen.

Current query support:

- no filters yet

Response body:

```json
{
  "items": [
    {
      "jobKey": "customer-load",
      "displayName": "Customer Load",
      "jobConfigPath": "src/main/resources/config-jobs/customer-load/job-config.yaml",
      "readinessStatus": "READY",
      "validationMessages": []
    }
  ],
  "page": 0,
  "size": 1,
  "totalItems": 1
}
```

### `GET /api/v1/jobs/{jobKey}`

Returns an aggregated job detail payload for the first Jobs drill-down screen.

Response body:

```json
{
  "job": {
    "jobKey": "customer-load",
    "displayName": "Customer Load",
    "jobConfigPath": "src/main/resources/config-jobs/customer-load/job-config.yaml",
    "readinessStatus": "READY",
    "validationMessages": []
  },
  "recentRuns": [
    {
      "scenario": "Customer Load",
      "jobExecutionId": 101,
      "status": "COMPLETED",
      "startTime": "2026-05-27T10:00:00",
      "endTime": "2026-05-27T10:00:10",
      "durationSeconds": 10,
      "sourceCount": 10,
      "writtenCount": 10,
      "rejectedCount": 0,
      "logPath": "logs/2026-05-27/customer-load.log"
    }
  ],
  "recentTriggerEvents": [
    {
      "triggerEventId": "te-123",
      "jobKey": "customer-load",
      "decisionStatus": "ACCEPTED",
      "reason": "manual_operator_request",
      "requestedBy": "operator@example",
      "requestedAt": "2026-05-27T10:15:30Z",
      "launchedRunId": null,
      "message": "accepted"
    }
  ]
}
```

### `POST /api/v1/jobs/{jobKey}:trigger-now`

Requests an immediate run for an already-registered bundle.

Request body (suggested first slice):

```json
{
  "reason": "manual_operator_request",
  "requestedBy": "operator@example"
}
```

Response body:

```json
{
  "jobKey": "customer-load",
  "decisionStatus": "ACCEPTED",
  "message": "Trigger request accepted as placeholder for reason='manual_operator_request' requestedBy='operator@example'.",
  "triggerEventId": "te-20260525-001"
}
```

Current behavior:

- returns `202 ACCEPTED` for known jobs
- records a trigger event in the configured control-plane registry (`jdbc` by default for control-plane profile, `memory` fallback)
- follows trigger persistence mode-switch guardrails so fallback changes are explicit, not silent (`controlplane.triggers.persistence.allow-mode-switch=true` only for intentional resets)
- does not launch the worker yet; launch orchestration remains a later slice

### `GET /api/v1/jobs/{jobKey}/trigger-events`

Returns recent trigger history for one registered job bundle.

Suggested first-slice query:

- `limit` (optional)

Response body shape:

```json
{
  "items": [
    {
      "triggerEventId": "te-20260525-001",
      "jobKey": "customer-load",
      "decisionStatus": "ACCEPTED",
      "reason": "manual_operator_request",
      "requestedBy": "operator@example",
      "requestedAt": "2026-05-27T10:15:30Z",
      "launchedRunId": null,
      "message": "Trigger request accepted as placeholder for reason='manual_operator_request' requestedBy='operator@example'."
    }
  ],
  "page": 0,
  "size": 20,
  "totalItems": 1
}
```

## Runs endpoints

### `GET /api/v1/runs`

Returns run summaries for the Runs screen.

Current query support:

- `limit` (optional)
- `job` (optional selected-job filter)
- `startDate` (optional inclusive start date in `yyyy-MM-dd`)
- `timezone` (optional IANA timezone used with `startDate`, defaults to server timezone)

Response body shape:

```json
{
  "items": [
    {
      "scenario": "Customer Load",
      "jobExecutionId": 10421,
      "runMode": "explicit-job",
      "recoveryPolicy": "rerun-from-start",
      "status": "COMPLETED",
      "startTime": "2026-05-25T10:41:00",
      "endTime": "2026-05-25T10:42:00",
      "durationSeconds": 60,
      "sourceCount": 250,
      "writtenCount": 250,
      "rejectedCount": 0
    }
  ],
  "page": 0,
  "size": 25,
  "totalItems": 1
}
```

### `GET /api/v1/runs/{jobExecutionId}`

Returns one projected `RUN_SUMMARY` view by job execution id.

### `GET /api/v1/runs/{jobExecutionId}/detail`

Returns a richer run drill-down assembled from structured scenario-log evidence for the same job execution id.

Current detail shape:

```json
{
  "run": {
    "scenario": "Customer Load",
    "jobExecutionId": 101,
    "status": "FAILED",
    "startTime": "2026-05-27T10:00:00",
    "endTime": "2026-05-27T10:00:10",
    "durationSeconds": 10,
    "sourceCount": 10,
    "writtenCount": 8,
    "rejectedCount": 2,
    "logPath": "logs/2026-05-27/customer-load.log"
  },
  "steps": [
    {
      "stepName": "normalize-orders",
      "sequence": 1,
      "status": "COMPLETED",
      "stepExecutionId": 201,
      "readCount": 10,
      "writeCount": 8,
      "filterCount": 0,
      "skipCount": 0,
      "rollbackCount": 0,
      "rejectedCount": 2,
      "startedAt": "2026-05-27T10:00:01",
      "finishedAt": "2026-05-27T10:00:05",
      "subFlow": "normalize-orders-subflow",
      "stepSummary": "Normalize orders step"
    }
  ],
  "artifacts": [
    {
      "artifactId": "reject-201",
      "role": "reject-output",
      "label": "Rejected records for normalize-orders",
      "pathOrUri": "output/rejects/orders.csv",
      "createdAt": "2026-05-27T10:00:05",
      "recordCount": 2,
      "stepName": "normalize-orders"
    }
  ],
  "failureSummary": {
    "category": "target_write",
    "exceptionType": "IllegalStateException",
    "rootCause": "constraint failed",
    "message": "constraint failed"
  },
  "evidenceLinks": [
    {
      "label": "Scenario log",
      "href": "logs/2026-05-27/customer-load.log",
      "type": "log-file"
    },
    {
      "label": "Run summary",
      "href": "logs/2026-05-27/customer-load.log#L6",
      "type": "run-summary"
    },
    {
      "label": "Step events (4)",
      "href": "logs/2026-05-27/customer-load.log#L2",
      "type": "step-event"
    },
    {
      "label": "Job failure context",
      "href": "logs/2026-05-27/customer-load.log#L7",
      "type": "job-failure"
    }
  ]
}
```

Current evidence sources for this route:

- `RUN_SUMMARY` for run-level counts and timestamps
- `STEP_EVENT event=step_started|step_finished` for ordered step outcomes and artifact paths
- `JOB_FAILURE event=job_failure` for failure categorization

Evidence-link anchor contract for this route:

- base log links use `logs/<yyyy-MM-dd>/<scenario>.log`
- line anchors append `#L<lineNumber>` to that path
- example: `logs/2026-05-27/customer-load.log#L6`

Missing/rolled log behavior:

- if the projected `run.logPath` no longer exists (for example roll/archive cleanup), the endpoint still returns `200` with summary/detail fields that are available
- `evidenceLinks` includes the normal `type=log-file` entry and an additive `type=log-file-missing` marker
- line-anchored links (`#L...`) are emitted only when the log file is currently readable

### `GET /api/v1/runs/{jobExecutionId}/log`

Returns run-scoped log lines for one selected run instance so Operator UI run detail does not show full scenario history by default.

Current query support:

- `limit` (optional; bounded server-side)

Current shape:

```json
{
  "jobExecutionId": 101,
  "scenario": "Customer Load",
  "logPath": "logs/2026-05-27/customer-load.log",
  "totalLines": 3,
  "truncated": false,
  "lines": [
    {
      "lineNumber": 2,
      "loggedAt": "2026-05-27T10:00:01",
      "level": "INFO",
      "recordType": "STEP_EVENT",
      "event": "step_started",
      "message": "2026-05-27T10:00:01.000+00:00 INFO ...",
      "structured": true
    }
  ]
}
```

Current extraction semantics:

- includes structured lines where parsed `jobExecutionId` matches the requested run id
- includes continuation/raw lines immediately after matching structured lines to preserve local error context
- does not include unrelated runs in the same scenario log file

## Schedules endpoints

### `GET /api/v1/schedules`

Returns schedule summaries for list and filtering.

### `GET /api/v1/schedules/{scheduleId}`

Returns one schedule detail.

Current detail projection includes:

- `lastAcceptedDueAt` (persisted watermark used for dedup and missed-run evaluation)
- `nextDueAt` (computed preview based on expression/timezone/enabled/paused state)

### `POST /api/v1/schedules`

Creates a schedule bound to one selected job bundle.

Request body (suggested first slice):

```json
{
  "selectedJobKey": "customer-load",
  "expression": "0 0 * * *",
  "timezone": "UTC",
  "enabled": true,
  "description": "Daily customer load"
}
```

### `PUT /api/v1/schedules/{scheduleId}`

Updates expression/metadata for an existing schedule.

### State-change actions

- `POST /api/v1/schedules/{scheduleId}:enable`
- `POST /api/v1/schedules/{scheduleId}:disable`
- `POST /api/v1/schedules/{scheduleId}:pause`
- `POST /api/v1/schedules/{scheduleId}:resume`

Return shape can stay minimal in MVP:

```json
{
  "scheduleId": "sch-001",
  "enabled": true,
  "paused": false,
  "updatedAt": "2026-05-25T11:00:00Z"
}
```

### `GET /api/v1/schedules/{scheduleId}/trigger-events`

Returns paged trigger history for schedule drill-down.

Current query support:

- `limit` (optional)

Response body shape:

```json
{
  "items": [
    {
      "triggerEventId": "te-20260525-001",
      "jobKey": "customer-load",
      "decisionStatus": "ACCEPTED",
      "reason": "manual_operator_request",
      "requestedBy": "operator@example",
      "requestedAt": "2026-05-27T10:15:30Z",
      "launchedRunId": null,
      "message": "Trigger request accepted as placeholder for reason='manual_operator_request' requestedBy='operator@example'."
    }
  ],
  "page": 0,
  "size": 20,
  "totalItems": 1
}
```

Current behavior: trigger history is retrieved by `scheduleId` from the shared trigger-event registry, so multiple schedules targeting the same job stay isolated in drill-down views.

When scheduler tick mode is enabled, the same history can include records with `reason=schedule_tick` and `requestedBy=scheduler`.

## System endpoints

### `GET /api/v1/system/health`

Returns health summary used by the System screen.

Suggested response:

```json
{
  "status": "UP",
  "timestamp": "2026-05-25T11:05:00Z"
}
```

### `GET /api/v1/system/info`

Returns environment and version metadata for display.

Suggested response:

```json
{
  "service": "spring-etl-engine-control-plane",
  "javaVersion": "21",
  "profile": "controlplane",
  "schedulerEnabled": false,
  "schedulerMissedRunPolicy": "SKIP",
  "schedulerOverlapPolicy": "ALLOW"
}
```

## View-model contract summary

The MVP API should align with these response models:

- `JobBundleSummaryView`
- `JobBundleDetailResponse`
- `RunSummaryView`
- `TriggerEventView`
- `SystemHealthResponse`
- `SystemInfoResponse`

Planned later view models still include:

- `RunDetailView`
- `StepRecordView`
- `ArtifactRecordView`
- `ScheduleView`

These names and fields are described in [`../operator-ui/angular-ui-mvp-structure.md`](../operator-ui/angular-ui-mvp-structure.md).

## Error model (MVP)

Use one consistent error envelope:

```json
{
  "errorCode": "SCHEDULE_NOT_FOUND",
  "message": "Schedule sch-001 does not exist",
  "details": null,
  "correlationId": "corr-20260525-abc"
}
```

Suggested first error categories:

- `VALIDATION_ERROR`
- `NOT_FOUND`
- `CONFLICT`
- `LAUNCH_REJECTED`
- `DEPENDENCY_UNAVAILABLE`
- `INTERNAL_ERROR`

## Guardrails

Treat these as non-negotiable for the MVP API surface:

- trigger endpoints must launch through the same selected-job boundary used by direct worker execution
- schedule endpoints manage control-plane records; they do not define a second orchestration DSL
- API contracts must not require the UI for normal worker execution
- payload fields should remain operator-facing and evidence-oriented, not UI-framework-specific

## MVP delivery order

Implemented so far:

1. read-only Jobs and Runs endpoints
2. Run summary lookup by job execution id
3. System health/info endpoints
4. Trigger-now placeholder endpoint plus trigger-event history
5. aggregated job detail payload for the first Jobs drill-down view
6. schedule list/detail/create/update/state-change endpoints

Recommended next backend slices:

1. richer run-detail projection beyond `RUN_SUMMARY`
2. schedule trigger-event history endpoint plus scheduler execution loop
3. file-watcher trigger ingestion and watcher-schedule linkage
4. worker-launch orchestration behind trigger-now

That order supports the monitoring-first UI rollout with minimal churn.

## Testing / validation expectations

When these APIs are implemented, validate at least:

- direct `etl.config.job` execution still runs with no control-plane API present
- list/detail responses map cleanly to the documented view models
- trigger-now and schedule state changes preserve selected-job launch guardrails
- run and step summaries remain aligned with runtime evidence semantics
- API contracts remain stable enough for Angular client generation and typed service use

## Related documents

- [`../operator-ui/operator-ui-architecture-direction.md`](../operator-ui/operator-ui-architecture-direction.md)
- [`../operator-ui/angular-ui-mvp-structure.md`](../operator-ui/angular-ui-mvp-structure.md)
- [`../operator-ui/angular-ui-mvp-wireframes.md`](../operator-ui/angular-ui-mvp-wireframes.md)
- [`scheduler-architecture-direction.md`](scheduler-architecture-direction.md)
- [`control-plane-worker-boundary.md`](control-plane-worker-boundary.md)
- [`control-plane-operational-data-model.md`](control-plane-operational-data-model.md)
- [`job-history-and-operational-observability.md`](job-history-and-operational-observability.md)



