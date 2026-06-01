# Epic U - Operator UI monitoring-first MVP

## Summary

Epic U defines the first independent Operator UI slice for OneFlow so users can inspect jobs, runs, and run details in one centralized experience without changing the ETL-core launch contract.

## Scope

This epic is the home for work that:

- keeps the UI and control-plane API optional and independently deployable from the ETL worker
- starts with monitoring-first read models (jobs list, run list, run detail)
- supports selected-job and/or start-date run narrowing so operators can choose one run instance before opening run detail
- exposes operational evidence for faster diagnosis (status, counts, failure summary, artifacts)
- preserves explicit selected-job launch behavior and avoids scheduler lock-in

This epic is **not** the place to make UI a runtime prerequisite or to redesign `etl.config.job` execution boundaries.

## Related backlog items

- [`U1 - Stand up independent monitoring-first Operator UI shell with jobs and runs list views`](../../backlog-items/operator-ui/U1-independent-operator-ui-shell-and-monitoring-read-model.md)
- [`U2 - Add job run detail drill-down with step outcomes, evidence links, and run-scoped log viewer`](../../backlog-items/operator-ui/U2-run-detail-drilldown-with-step-and-artifact-evidence.md)
- [`U3 - Add guarded trigger-now action from job details without scheduler coupling`](../../backlog-items/operator-ui/U3-guarded-trigger-now-from-job-details.md)

## Related docs

- [`../../../architecture/operator-ui/operator-ui-architecture-direction.md`](../../../architecture/operator-ui/operator-ui-architecture-direction.md)
- [`../../../architecture/operator-ui/angular-ui-mvp-structure.md`](../../../architecture/operator-ui/angular-ui-mvp-structure.md)
- [`../../../architecture/operator-ui/angular-ui-mvp-wireframes.md`](../../../architecture/operator-ui/angular-ui-mvp-wireframes.md)
- [`../../../architecture/control-plane/operator-ui-mvp-api-surface.md`](../../../architecture/control-plane/operator-ui-mvp-api-surface.md)
- [`../../../architecture/control-plane/operator-ui-mvp-openapi.yaml`](../../../architecture/control-plane/operator-ui-mvp-openapi.yaml)
- [`../../../architecture/control-plane/control-plane-worker-boundary.md`](../../../architecture/control-plane/control-plane-worker-boundary.md)

## Maintenance note

Use [`../../product-backlog.md`](../../product-backlog.md) for live item-level board fields. Use this page for the shared Epic U boundary and cross-item intent.


