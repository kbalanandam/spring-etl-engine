# T16 - Customer-owned processor transform extension seam

## Summary

Define a bounded extension contract so customers can add new processor-side field transforms without modifying OneFlow core code, while preserving the shipped `type: default` processor path and transform-before-validation runtime order.

## Current board status

- Epic: **[Epic T](../../epics/epic-t-transformation-capability.md)**
- Priority: **P1**
- Status: **In Progress**
- Milestone: **M2**
- M2 scope lock: this milestone does not introduce custom processor types and is transformation-only extensibility on the existing `type: default` processor seam.
- Dependency: **T3, D1**

> Keep these fields synchronized with the row in [`product-backlog.md`](../../product-backlog.md). The execution board remains the canonical source for changing status values.

Use the linked `Epic` entry above to navigate to the shared epic-level product context for this backlog item.

## Problem

The runtime already ships built-in processor transforms (`valueMap`, `expression`, `conditional`), but customer projects need additional transform behavior (for example null replacement, partner-specific string translation, or custom normalization) without repeatedly changing OneFlow core.

## Goal

Add one stable customer-owned processor transform seam with a common transform declaration shape so new transform types can be plugged in through extension providers and validated fail-fast at startup.

## Scope

This item covers:

- one additive custom-transform declaration shape on the existing processor `transforms[]` path
- one stable runtime binding key (`transforms[].type`) for customer transform dispatch
- provider-owned transform configuration payload (`transforms[].config`) for transform-specific options
- startup fail-fast validation for unknown transform types and invalid transform config
- shared failure-category mapping aligned with Epic D (`config`, `binding`, `execution`)
- additive operator evidence for custom transform type and failure context

## Out of scope

This item does not cover:

- alternate processor types beyond `type: default`
- source-native adaptation before runtime records (`T9`)
- record-level multi-field transformation stage (`T10`)
- cross-record/window transformations (`T11`)
- custom-step/job-level orchestration behavior (`A7`)

## Proposed approach

Preferred phase-1 direction:

1. keep all custom transformation work on the active processor transform seam (`ProcessorFieldTransform`)
2. preserve one common transform envelope in config (`type` plus optional provider-owned `config` block)
3. resolve transform implementations through existing extension registration (`ProcessorExtensionProvider`)
4. keep strict ownership boundaries: transforms rewrite values, rules decide accept/reject
5. keep startup validation strict and scenario-aware for unsupported transform types or invalid transform config

### Illustrative future config shape (draft only)

Illustrative example only - this is a future contract shape, not shipped behavior yet:

```yaml
type: default
mappings:
  - source: Customers
    target: CustomersSql
    fields:
      - from: statusCode
        to: status
        transforms:
          - type: replaceNull
            config:
              replacement: A
          - type: partnerStatusTranslate
            config:
              mappings:
                PENDING: Pending
                FAILED: Error
              fallbackValue: Unknown
```

## Operator / runtime impact

Expected impact when this item ships:

- customer teams can add bounded custom transforms without forking OneFlow runtime code
- built-in transform behavior remains backward compatible for existing jobs
- startup failures become more actionable with typed category and transform identifier context
- transform extension growth stays coherent without introducing alternate processor contracts

## Locked review criteria

T16 implementation and review should follow the architecture invariants in:

- [`Customer-owned processor transform seam`](../../../architecture/etl-core/customer-owned-processor-transform-seam.md#t16-architecture-invariants)

## Acceptance criteria

- [x] custom transform extension contract is documented as additive to the shipped `type: default` path
- [x] existing built-in transform behavior remains backward compatible when no custom transform is authored
- [x] startup validation fails fast for unknown custom transform `type`
- [x] startup validation fails fast for invalid transform-specific `config` values
- [x] one shared failure-category mapping is documented and aligned with Epic D
- [x] at least one preserved runnable scenario is identified/planned for custom-transform proof

## Phase-1 implementation evidence

- additive provider-owned transform envelope implemented in `src/main/java/com/etl/config/processor/ProcessorConfig.java` (`FieldTransform.config`)
- transform-level config envelope validation and behavior coverage in `src/test/java/com/etl/processor/transform/TransformEvaluatorTest.java`
- startup fail-fast path coverage for custom transform config in `src/test/java/com/etl/config/ConfigLoaderJobConfigTest.java`
- shipped processor contract docs updated in `docs/config/processor/default-processor.md`
- T16 architecture invariant alignment updated in `docs/architecture/etl-core/customer-owned-processor-transform-seam.md`

Focused verification executed on the branch:

```powershell
mvn -f "C:\spring-etl-engine\pom.xml" --no-transfer-progress "-Dtest=TransformEvaluatorTest,DynamicProcessorFactoryTest,ProcessorExtensionDefaultsTest,ConfigLoaderJobConfigTest#loadsProcessorConfigWhenCustomTransformUsesProviderOwnedConfigEnvelope+failsFastWhenCustomTransformConfigEnvelopeIsInvalid" test
```

## Related docs

- [`Product backlog`](../../product-backlog.md)
- [`Epic T - Transformation capability`](../../epics/epic-t-transformation-capability.md)
- [`Epic D - Error taxonomy and failure categorization`](../../epics/epic-d-error-taxonomy-and-failure-categorization.md)
- [`Default processor reference`](../../../config/processor/default-processor.md)
- [`Extension points`](../../../architecture/etl-core/extension-points.md)
- [`Customer-owned processor transform seam`](../../../architecture/etl-core/customer-owned-processor-transform-seam.md)
- [`A7 + T16 extensibility charter`](../../../architecture/etl-core/a7-t16-extensibility-charter.md)

## Implementation notes

Keep phase-1 narrow: field-scoped transform extensibility only. Do not combine this item with source-native, record-level, or cross-record transform scope expansion.

## Status notes

- This item is planned as additive extensibility, not a replacement of the shipped transform contract.
- Phase-1 (`transforms[].config` on the existing `type: default` processor seam) is implemented on `feature/t16-transform-seam-phase1` and queued for PR review/merge.
- Preserved runnable showcase bundle added: `src/main/resources/config-jobs/xml-to-csv-events-transform-showcase/` with chained built-in transforms plus one ServiceLoader extension transform (`partnerStatusTranslate`).



