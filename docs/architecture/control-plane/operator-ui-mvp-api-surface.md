# Operator UI MVP API Surface

## Purpose

This document defines a practical first API surface for the optional control plane to support the Angular operator UI MVP screens.

It exists to freeze a small, explicit backend contract for UI delivery without changing the existing selected-job runtime boundary.

## Status

- Classification: **Future direction**
- This note still carries future-direction design intent, but the monitoring-first subset below is now implemented by the optional `com.etl.controlplane.ControlPlaneApiApplication` starter.
- Implemented now: `GET /api/v1/jobs`, `GET /api/v1/jobs/{jobKey}`, `POST /api/v1/jobs/{jobKey}:trigger-now`, `GET /api/v1/jobs/{jobKey}/trigger-events`, `GET /api/v1/runs`, `GET /api/v1/runs/{jobExecutionId}`, `GET /api/v1/runs/{jobExecutionId}/detail`, `GET /api/v1/system/health`, and `GET /api/v1/system/info`.
- Schedule endpoints in this document remain planned, not implemented.

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

GET    /api/v1/system/health
GET    /api/v1/system/info
```

Planned later:

```text

GET    /api/v1/schedules
GET    /api/v1/schedules/{scheduleId}
POST   /api/v1/schedules
PUT    /api/v1/schedules/{scheduleId}
POST   /api/v1/schedules/{scheduleId}:enable
POST   /api/v1/schedules/{scheduleId}:disable
POST   /api/v1/schedules/{scheduleId}:pause
POST   /api/v1/schedules/{scheduleId}:resume
GET    /api/v1/schedules/{scheduleId}/trigger-events
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
- records an in-memory trigger event for UI integration and drill-down support
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

Response body shape:

```json
{
  "items": [
    {
      "scenario": "Customer Load",
      "jobExecutionId": 10421,
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

## Schedules endpoints

### `GET /api/v1/schedules`

Returns schedule summaries for list and filtering.

### `GET /api/v1/schedules/{scheduleId}`

Returns one schedule detail.

### `POST /api/v1/schedules`

Creates a schedule bound to one selected job bundle.

Request body (suggested first slice):

```json
{
  "jobKey": "customer-load",
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

Query (suggested first slice):

- `page`, `size`

Response body shape:

```json
{
  "items": [
    {
      "triggerEventId": "te-20260525-001",
      "origin": "SCHEDULE",
      "scheduleId": "sch-001",
      "jobKey": "customer-load",
      "decisionStatus": "LAUNCHED",
      "triggeredAt": "2026-05-25T10:41:00Z",
      "launchedRunId": "10421",
      "explanation": null
    }
  ],
  "page": 0,
  "size": 25,
  "totalItems": 1
}
```

Use `TriggerEventPage` as the response envelope.

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
  "profile": "controlplane"
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

Recommended next backend slices:

1. richer run-detail projection beyond `RUN_SUMMARY`
2. persisted trigger-event history and run history storage
3. schedule list/detail/create/update/state-change endpoints
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



