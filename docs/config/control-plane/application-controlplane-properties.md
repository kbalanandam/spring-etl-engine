# Control-plane Profile Properties (`application-controlplane.properties`)

## Purpose

This page is the canonical property reference for `src/main/resources/application-controlplane.properties`.

Scope:

- documents every active property currently present in that file
- explains runtime behavior and owning component
- records which values are profile defaults vs environment overrides

## Notes

- Redundant Spring keys that were duplicated by code-level defaults/annotations were removed from the profile file.
- This page documents active properties only.
- Secrets should still be supplied through env vars, not committed literals.

## Runtime identity and HTTP

| Property | Default in profile | Used by | Behavior |
| --- | --- | --- | --- |
| `spring.application.name` | `spring-etl-engine-control-plane` | `JdbcRunSummaryRegistry`, `JdbcTriggerEventRegistry`, `JdbcScheduleRegistry`, `SystemController` | Audit actor/service identity for control-plane writes and system-info reporting. |
| `server.port` | `8081` | Spring Boot web runtime | Control-plane API HTTP port. |
| `etl.logging.base-dir` | `logs` | `RunSummaryReadModelService`, `OperatorLogController` | Base folder for scenario log reads and UI log access. |

## Trigger persistence and guardrails

| Property | Default in profile | Used by | Behavior |
| --- | --- | --- | --- |
| `controlplane.triggers.persistence.mode` | `jdbc` | `JdbcTriggerEventRegistry` / `InMemoryTriggerEventRegistry` (`@ConditionalOnProperty`) | Chooses trigger registry backend (`jdbc` or `memory`). |
| `controlplane.triggers.persistence.mode-marker-path` | `.controlplane/trigger-event-persistence-mode.marker` | `TriggerEventPersistenceModeGuard` | Marker file path used to detect accidental mode flips across restarts. |
| `controlplane.triggers.persistence.allow-mode-switch` | `false` | `TriggerEventPersistenceModeGuard` | When `false`, startup fails fast on mode mismatch; when `true`, allows intentional reset. |
| `controlplane.triggers.retention-per-job` | `500` | `JdbcTriggerEventRegistry`, `InMemoryTriggerEventRegistry` | Per-job trigger history retention cap. |

## Run-summary persistence and refresh behavior

| Property | Default in profile | Used by | Behavior |
| --- | --- | --- | --- |
| `controlplane.runs.persistence.mode` | `jdbc` | `JdbcRunSummaryRegistry` / `InMemoryRunSummaryRegistry` (`@ConditionalOnProperty`) | Chooses runs registry backend (`jdbc` or `memory`). |
| `controlplane.runs.sync-just-finished-job-from-log` | `true` | `JobCompletionNotificationListener` | Attempts targeted run sync from scenario log immediately after job completion. |
| `controlplane.runs.retention` | `5000` | `JdbcRunSummaryRegistry` | Max retained run-summary records before pruning. |
| `controlplane.runs.min-reindex-interval-ms` | `1000` | `RunSummaryReadModelService` | Minimum interval between background log reindex passes. |
| `controlplane.runs.allow-force-refresh` | `true` | `RunSummaryController` | Enables explicit full replay only for `GET /api/v1/runs?...&refresh=true&forceReplay=true`. |

## Database vendor and datasource contract

| Property | Default in profile | Used by | Behavior |
| --- | --- | --- | --- |
| `controlplane.db.vendor` | `${CONTROLPLANE_DB_VENDOR:mysql}` | `JdbcRunSummaryRegistry`, `JdbcTriggerEventRegistry`, `JdbcScheduleRegistry`, `SystemController` | Vendor token used for SQL dialect branching and diagnostics. |
| `controlplane.db.url` | `${CONTROLPLANE_DB_URL:jdbc:mysql://localhost:3306/etl_controlplane?...}` | Spring datasource bridge | Canonical control-plane JDBC URL; mapped into `spring.datasource.url`. |
| `controlplane.db.username` | `${CONTROLPLANE_DB_USERNAME:root}` | Spring datasource bridge | Canonical datasource username; mapped into `spring.datasource.username`. |
| `controlplane.db.password` | `${CONTROLPLANE_DB_PASSWORD:}` | Spring datasource bridge | Canonical datasource password; mapped into `spring.datasource.password`. |
| `controlplane.db.driver-class-name` | `${CONTROLPLANE_DB_DRIVER_CLASS_NAME:com.mysql.cj.jdbc.Driver}` | Spring datasource bridge | Canonical JDBC driver class; mapped into `spring.datasource.driver-class-name`. |

## Schedule runtime

| Property | Default in profile | Used by | Behavior |
| --- | --- | --- | --- |
| `controlplane.schedules.persistence.mode` | `jdbc` | `JdbcScheduleRegistry` / `InMemoryScheduleRegistry` (`@ConditionalOnProperty`) | Chooses schedule registry backend (`jdbc` or `memory`). |
| `controlplane.scheduler.enabled` | `true` | `ScheduleTriggerTickService` (`@ConditionalOnProperty`), `SystemController` | Enables/disables scheduler tick loop bean. |
| `controlplane.scheduler.poll-interval-ms` | `30000` | `ScheduleTriggerTickService` | Fixed delay between scheduler polling cycles. |
| `controlplane.scheduler.missed-run-policy` | `SKIP` | `ScheduleTriggerTickService`, `SystemController` | Missed-run handling policy. |
| `controlplane.scheduler.overlap-policy` | `ALLOW` | `ScheduleTriggerTickService`, `SystemController` | Overlap behavior for due schedules. |
| `controlplane.scheduler.max-catch-up-iterations` | `2000` | `ScheduleTriggerTickService` | Safety bound for catch-up loop iterations. |
| `controlplane.scheduler.launch-enabled` | `true` | `ScheduleTriggerTickService` | Allows scheduler-origin launches vs decision-only recording. |
| `controlplane.scheduler.trigger-reason` | `schedule_tick` | `ScheduleTriggerTickService` | Reason value stamped on scheduler-origin trigger events. |
| `controlplane.scheduler.requested-by` | `scheduler` | `ScheduleTriggerTickService` | Requested-by actor stamped on scheduler-origin trigger events. |

## Worker launch controls

| Property | Default in profile | Used by | Behavior |
| --- | --- | --- | --- |
| `controlplane.job-launch.enabled` | `true` | `SelectedJobLaunchService` | Master switch for launching worker processes from control-plane API/scheduler. |
| `controlplane.job-launch.worker.datasource.url` | `${CONTROLPLANE_WORKER_DB_URL:${controlplane.db.url}}` | `SelectedJobLaunchService` | Worker JVM datasource URL argument for launched runs. |
| `controlplane.job-launch.worker.datasource.username` | `${CONTROLPLANE_WORKER_DB_USERNAME:${controlplane.db.username}}` | `SelectedJobLaunchService` | Worker JVM datasource username argument. |
| `controlplane.job-launch.worker.datasource.password` | `${CONTROLPLANE_WORKER_DB_PASSWORD:${controlplane.db.password}}` | `SelectedJobLaunchService` | Worker JVM datasource password argument. |
| `controlplane.job-launch.worker.datasource.driver-class-name` | `${CONTROLPLANE_WORKER_DB_DRIVER_CLASS_NAME:${controlplane.db.driver-class-name}}` | `SelectedJobLaunchService` | Worker JVM JDBC driver argument. |
| `controlplane.job-launch.worker.connection-init-sql` | `` (blank) | `SelectedJobLaunchService` | Optional worker connection init SQL; keep blank unless explicitly needed per vendor. |

## Spring datasource bridge and pool sizing

| Property | Default in profile | Used by | Behavior |
| --- | --- | --- | --- |
| `spring.datasource.url` | `${controlplane.db.url}` | Spring Boot datasource autoconfig | Active datasource URL for control-plane JDBC registries. |
| `spring.datasource.username` | `${controlplane.db.username}` | Spring Boot datasource autoconfig | Active datasource username. |
| `spring.datasource.password` | `${controlplane.db.password}` | Spring Boot datasource autoconfig | Active datasource password. |
| `spring.datasource.driver-class-name` | `${controlplane.db.driver-class-name}` | Spring Boot datasource autoconfig | Active JDBC driver class. |
| `spring.datasource.hikari.maximum-pool-size` | `10` | HikariCP | Maximum datasource pool size. |
| `spring.datasource.hikari.minimum-idle` | `2` | HikariCP | Minimum idle datasource connections. |
| `spring.sql.init.mode` | `never` | Spring SQL init | Prevents Spring SQL initializer scripts from auto-running in this profile. |

## Quick cleanup review outcome (current file)

- Control-plane-prefixed keys in this profile are actively consumed by runtime code.
- Spring bridge keys retained here are intentional and active (datasource/pool/sql-init wiring).
- Redundant Spring entries removed from this profile were:
  - `spring.main.web-application-type`
  - `spring.autoconfigure.exclude`
  - `spring.batch.jdbc.initialize-schema`

## Property change checklist

Use this checklist whenever `src/main/resources/application-controlplane.properties` changes.

1. Add/update/remove the property in the profile file.
2. Trace runtime usage in code (`@Value`, `@ConditionalOnProperty`, or framework binding).
3. Update this page with default, owner, and behavior.
4. If behavior changed, update matching architecture/config docs (for example API contract or persistence profile notes).
5. For removals, verify no active Java references remain.
6. For control-plane runtime behavior changes, run focused tests (for example `RunSummaryControllerTest`, trigger/scheduler registry tests).
7. Restart control-plane locally and verify `/api/v1/system/info` plus one representative flow (runs list/refresh or schedule tick).


