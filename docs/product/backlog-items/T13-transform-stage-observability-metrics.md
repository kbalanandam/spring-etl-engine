# T13 — Transform-stage observability metrics and operational evidence

## Summary

Define transform-stage specific observability so transform behavior can be measured and diagnosed independently from processor-rule validation outcomes.

## Current board status

- Epic: **[Epic T](../epics/epic-t-transformation-capability.md)**
- Priority: **P2**
- Status: **Deferred**
- Milestone: **M3**
- Dependency: **T10, V1**
- Sequence rank: **#4** in deferred advanced transform sequence

> Keep these fields synchronized with the row in [`product-backlog.md`](../product-backlog.md). The execution board remains the canonical source for changing status values.

Use the linked `Epic` entry above to navigate to the shared epic-level product context for this backlog item.

## Problem

Current run/step evidence is strong, but transform-stage visibility is limited. Operators need to see transform-level counts, timing, and failure signals without inferring everything from rule events.

## Goal

Introduce explicit transform-stage evidence and metrics with stable naming so transform behavior is diagnosable at scale.

## Scope

- define transform-stage event/metric model
- define minimum dimensions and counters (applied/skipped/failed)
- define correlation with run/step IDs and scenario context
- define retention/reporting expectations in verification outputs

## Out of scope

- complete telemetry platform overhaul
- non-transform observability redesign across all subsystems
- AI-assisted analytics productization

## Proposed approach

- add additive transform-stage evidence around active runtime path
- keep metric naming stable and scenario-aware
- integrate with existing run-summary and verification evidence model

## Operator / runtime impact

- faster diagnosis of transform failures and hotspots
- clearer distinction between transform and validation effects
- stronger enterprise reporting for transformation operations

## Concrete transformation examples

Conceptual transform-stage summary event shape:

```json
{
  "event": "TRANSFORM_STAGE_SUMMARY",
  "scenario": "customer-load",
  "stepName": "normalize-customers",
  "stage": "recordTransform",
  "transformId": "deriveCustomerSegment",
  "appliedCount": 9821,
  "skippedCount": 214,
  "failedCount": 7,
  "durationMs": 1640
}
```

Conceptual transform-stage failure event shape:

```json
{
  "event": "TRANSFORM_STAGE_FAILURE",
  "scenario": "customer-load",
  "stepName": "normalize-customers",
  "transformId": "deriveCustomerSegment",
  "recordKey": "customerId=88421",
  "reasonCode": "expression_evaluation_failed"
}
```

Expected behavior:

- transform metrics are emitted independently from validation-rule counters
- transform events remain correlated with `scenario`, `runCorrelationId`, `jobExecutionId`, and `stepName`
- verification reporting can summarize top failing transforms and transform-stage latency

## Developer expectations

- define stable metric/event names before adding optional dimensions
- avoid high-cardinality dimensions in first slice (for example raw payload values)
- ensure transform failures and rule failures remain separable in evidence and dashboards
- include at least one scenario proving transform-stage metrics plus failure event emission

## Acceptance criteria

- [ ] transform-stage evidence model is documented
- [ ] core counters/timers and dimensions are defined
- [ ] evidence integration with verification model is documented
- [ ] at least one scenario/test proves transform-stage evidence when implemented

## Related docs

- [`Product backlog`](../product-backlog.md)
- [`Transformation capability catalog`](../../architecture/transformation-capability-catalog.md)
- [`Enterprise verification evidence model`](V1-enterprise-verification-evidence-model-and-report-categories.md)
- [`Job history and operational observability`](../../architecture/job-history-and-operational-observability.md)

## Implementation notes

Prioritize stable evidence naming and correlation fields over broad metric volume in the first slice.

## Status notes

Deferred until record-level transformation-stage boundaries are explicit.




