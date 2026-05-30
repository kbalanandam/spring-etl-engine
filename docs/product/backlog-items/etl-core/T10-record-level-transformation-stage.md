# T10 - Record-level transformation stage beyond field-centric mapping

## Summary

Define a record-level transformation stage for scenarios where single-field transform chains are not enough and coordinated multi-field orchestration is required.

## Current board status

- Epic: **[Epic T](../../epics/etl-core/epic-t-transformation-capability.md)**
- Priority: **P2**
- Status: **Deferred**
- Milestone: **M3**
- Dependency: **T8**
- Sequence rank: **#2** in deferred advanced transform sequence

> Keep these fields synchronized with the row in [`product-backlog.md`](../../product-backlog.md). The execution board remains the canonical source for changing status values.

Use the linked `Epic` entry above to navigate to the shared epic-level product context for this backlog item.

## Problem

Current transform execution is primarily field-centric. Some business transformations require whole-record coordination and should not be modeled as many fragile field-order dependencies.

## Goal

Add a clear record-level transformation stage contract while preserving explicit ordered scenario-step orchestration.

Use the concrete examples in this page as the development expectation baseline.

## Scope

- define record-level transformation stage inputs/outputs
- define coexistence with field-level transforms and processor rules
- define startup validation and failure categorization behavior
- define migration path from field-chain patterns to record-level stage where needed
- provide implementation-ready examples for representative transformation families so development expectations are concrete before coding starts

## Out of scope

- cross-record stateful/group/window transformations
- source-native adaptation seam implementation
- replacing current field-level transform model universally

## Proposed approach

- keep existing field-level transforms as default path
- add record-level stage as an additive capability
- preserve explicit `job-config.yaml` step order and one-scenario-per-run model

## Operator / runtime impact

- clearer handling of complex multi-field transform logic
- lower risk of hidden field-order coupling
- better runtime diagnostics for record-level transform failures

## Concrete transformation examples

```yaml
# conceptual record-level stage that computes multiple output fields coherently
recordTransforms:
  - type: deriveCustomerSegment
    inputs: [annualSpend, region, accountAgeMonths]
    outputs: [segmentCode, segmentReason]
```

```yaml
# conceptual runtime placement in one selected step
steps:
  - name: customer-segmentation
    source: CustomerRaw
    processor: CustomerProcessor
    recordTransformStage: default
    target: CustomerSegmentCsv
```

Expected behavior:

- one record-level transform can read many fields and write many fields in a single deterministic operation
- field-level transforms can still run before/after record-level stage only if precedence is explicitly documented
- record-level transform failures are reported with transform identifier and affected record context

## Developer expectations

- keep this stage additive; do not break existing field-level mapping scenarios
- define strict stage precedence with processor transforms and rules before implementation
- provide deterministic outputs for the same input record, independent of field declaration order
- include scenario and unit-test coverage for multi-field orchestration plus fallback behavior

## Acceptance criteria

- [ ] record-level transformation contract is documented
- [ ] runtime precedence with processor rules is explicit
- [ ] failure and observability model is documented
- [ ] this page includes example-driven expectations for each transformation family covered by this item's design boundary
- [ ] at least one preserved scenario proves the new stage when implemented

## Related docs

- [`Product backlog`](../../product-backlog.md)
- [`Transformation capability catalog`](../../../architecture/etl-core/transformation-capability-catalog.md)
- [`Runtime flow`](../../../architecture/etl-core/runtime-flow.md)
- [`Extension points`](../../../architecture/etl-core/extension-points.md)

## Implementation notes

Treat this as additive. Do not remove or regress current field-level transform behavior while introducing record-level capabilities.

## Status notes

Deferred until reusable transform profile work defines stable inputs for broader stage semantics.






