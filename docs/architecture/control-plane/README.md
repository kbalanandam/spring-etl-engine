# Control-plane architecture notes

Use this folder for architecture notes that describe the optional operational layer around the ETL worker.

## Purpose

This folder is the main landing zone for scheduler, watcher, trigger, retained-history, and control-plane service design.

Use it when you want to understand:

- the boundary between the mandatory ETL worker and optional operational layers
- scheduler and trigger direction without inventing a second runtime contract
- retained operational history for schedules, runs, steps, and artifacts
- the backend responsibilities that operator-facing UI will rely on later

## Current anchor notes

- [`control-plane-worker-boundary.md`](control-plane-worker-boundary.md) - mandatory worker versus optional control-plane boundary
- [`control-plane-persistence-boundary-contract.md`](control-plane-persistence-boundary-contract.md) - Epic R boundary contract for optional persistence portability work
- [`control-plane-operational-data-model.md`](control-plane-operational-data-model.md) - retained conceptual model for trigger, run, step, and artifact history
- [`control-plane-local-relational-schema.md`](control-plane-local-relational-schema.md) - SQLite-first local persistence direction
- [`./scheduler-architecture-direction.md`](./scheduler-architecture-direction.md) - first scheduler-specific design direction under the control-plane layer
- [`./operator-ui-mvp-api-surface.md`](./operator-ui-mvp-api-surface.md) - first control-plane API surface for Angular MVP screens (Jobs, Runs, Run detail, Schedules, System)
- [`./operator-ui-mvp-openapi.yaml`](./operator-ui-mvp-openapi.yaml) - machine-readable OpenAPI 3.1 draft contract for the operator UI MVP API surface

## Related notes

- [`job-history-and-operational-observability.md`](job-history-and-operational-observability.md) - current observability baseline plus retained-history direction
- [`../etl-core/scenario-driven-runtime-direction.md`](../etl-core/scenario-driven-runtime-direction.md) - selected-job runtime contract that scheduler launches must preserve
- [`../etl-core/oneflow-runtime-fallback-reference.md`](../etl-core/oneflow-runtime-fallback-reference.md) - current runtime decisions that scheduler/control-plane work must not bypass
- [`../operator-ui/README.md`](../operator-ui/README.md) - future UI layer that will consume this control-plane surface

## Layering rule

Scheduler is grouped here, not under the UI folder.

The UI may manage schedules, but the scheduler itself is a control-plane/backend capability that must still exist conceptually even when no browser UI is present.

## Backend stereotype intent

- Use `@Service` for control-plane orchestration and read-model projection beans, such as schedule workflows and run/job detail assembly.
- Use `@Repository` for JDBC-backed registries that own durable control-plane persistence access.
- Keep generic `@Component` for infrastructure helpers, validators, guards, and adapter registries that are not the primary service or persistence boundary.


