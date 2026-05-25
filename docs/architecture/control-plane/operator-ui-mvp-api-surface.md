# Operator UI MVP API Surface

## Purpose

This document defines a practical first API surface for the optional control plane to support the Angular operator UI MVP screens.

It exists to freeze a small, explicit backend contract for UI delivery without changing the existing selected-job runtime boundary.

## Status

- Classification: **Future direction**
- This note defines a first API shape for MVP planning, not a shipped production API.

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

GET    /api/v1/runs
GET    /api/v1/runs/{runId}

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

Query (suggested first slice):

- `q` (optional search text)
- `readinessStatus` (optional)
- `hasSchedule` (optional boolean)
- `page`, `size` (optional)

Response body:

```json
{
  "items": [
    {
      "jobKey": "customer-load",
      "displayName": "Customer Load",
      "jobConfigPath": "src/main/resources/config-jobs/customer-load/job-config.yaml",
      "readinessStatus": "READY",
      "validationMessages": [],
      "scheduleCount": 2,
      "lastRunSummary": {
        "runId": "10420",
        "status": "SUCCESS",
        "finishedAt": "2026-05-25T10:12:00Z"
      }
    }
  ],
  "page": 0,
  "size": 25,
  "totalItems": 1
}
```

### `GET /api/v1/jobs/{jobKey}`

Returns one bundle detail with readiness and linked schedule summary.

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
  "triggerEventId": "te-20260525-001",
  "decisionStatus": "LAUNCHED",
  "launchedRunId": "10421"
}
```

## Runs endpoints

### `GET /api/v1/runs`

Returns run summaries for the Runs screen.

Query (suggested first slice):

- `jobKey`
- `status`
- `triggerOrigin`
- `startedFrom`
- `startedTo`
- `page`, `size`

Response body shape:

```json
{
  "items": [
    {
      "runId": "10421",
      "jobKey": "customer-load",
      "jobDisplayName": "Customer Load",
      "status": "RUNNING",
      "triggerOrigin": "SCHEDULE",
      "startedAt": "2026-05-25T10:41:00Z",
      "finishedAt": null,
      "durationMs": null,
      "sourceCount": 250,
      "writtenCount": null,
      "rejectedCount": 0
    }
  ],
  "page": 0,
  "size": 25,
  "totalItems": 1
}
```

### `GET /api/v1/runs/{runId}`

Returns one run detail for the Run detail screen.

Response should include:

- `run` (`RunRecordView`)
- `steps` (`StepRecordView[]`)
- `artifacts` (`ArtifactRecordView[]`)
- `failureSummary` (nullable)
- `evidenceLinks[]`

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
  "status": "HEALTHY",
  "operatorApi": "HEALTHY",
  "scheduler": "HEALTHY",
  "workerReachability": "REACHABLE",
  "historyStore": "HEALTHY",
  "checkedAt": "2026-05-25T11:05:00Z"
}
```

### `GET /api/v1/system/info`

Returns environment and version metadata for display.

## View-model contract summary

The MVP API should align with these response models:

- `JobBundleSummaryView`
- `RunRecordView`
- `RunDetailView`
- `StepRecordView`
- `ArtifactRecordView`
- `ScheduleView`
- `TriggerEventView`

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

Implement backend API slices in this order:

1. read-only Jobs and Runs endpoints
2. Run detail endpoint
3. System health/info endpoints
4. Schedule list/detail/create/update/state-change endpoints
5. Trigger history endpoint
6. Trigger-now endpoint if not already covered

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



