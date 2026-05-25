# Epic S - Scheduling and control plane

## Summary

Epic S covers the future optional control-plane layer that can schedule, trigger, observe, and persist operational history for OneFlow jobs without replacing the explicit selected-job ETL runtime contract.

## Scope

This epic is the home for work that:

- defines native scheduling and trigger contracts
- clarifies pause/resume, overlap, missed-run, and trigger-audit behavior
- defines the retained control-plane data model for schedules, runs, steps, and lineage
- keeps built-in scheduling optional so external orchestrators remain valid

This epic is **not** the place to redesign the ETL worker launch contract or make the control plane mandatory for normal runs.

## Related backlog items

- [`S1 - Define schedule model and trigger contract for scenario-based execution`](../backlog-items/S1-schedule-model-and-trigger-contract.md)
- [`S2 - Add time-based schedule definitions with pause/resume controls`](../backlog-items/S2-time-based-schedule-definitions-with-pause-resume.md)
- [`S3 - Add overlap policy, missed-run handling, and basic trigger audit trail`](../backlog-items/S3-overlap-policy-missed-run-handling-and-trigger-audit-trail.md)
- [`S4 - Define control-plane operational data model for schedules, watchers, trigger events, run and step history, artifact lineage, and restartability anchors`](../backlog-items/S4-control-plane-operational-data-model.md)

## Related docs

- [`../../architecture/control-plane/control-plane-worker-boundary.md`](../../architecture/control-plane/control-plane-worker-boundary.md)
- [`../../architecture/control-plane/control-plane-operational-data-model.md`](../../architecture/control-plane/control-plane-operational-data-model.md)
- [`../../architecture/control-plane/control-plane-local-relational-schema.md`](../../architecture/control-plane/control-plane-local-relational-schema.md)
- [`../../architecture/control-plane/scheduler-architecture-direction.md`](../../architecture/control-plane/scheduler-architecture-direction.md)
- [`../../architecture/operator-ui/operator-ui-architecture-direction.md`](../../architecture/operator-ui/operator-ui-architecture-direction.md)
- [`../../adr/0008-formalize-control-plane-and-etl-worker-boundary.md`](../../adr/0008-formalize-control-plane-and-etl-worker-boundary.md)
- [`../../adr/0009-formalize-sqlite-first-local-control-plane-persistence.md`](../../adr/0009-formalize-sqlite-first-local-control-plane-persistence.md)

## Maintenance note

Use [`../product-backlog.md`](../product-backlog.md) for live item-level board fields. Use this page for the shared Epic S product boundary and cross-item intent.

