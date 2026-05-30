# T9 - Source-native transformation seam before runtime records

## Summary

Define a dedicated source-native transformation seam for adaptation concerns that depend on source structure (for example XPath, namespaces, headers, tokens) before a normal runtime record exists.

## Current board status

- Epic: **[Epic T](../../epics/epic-t-transformation-capability.md)**
- Priority: **P2**
- Status: **Deferred**
- Milestone: **M3**
- Dependency: **T8, P3**
- Sequence rank: **#5** in deferred advanced transform sequence

> Keep these fields synchronized with the row in [`product-backlog.md`](../../product-backlog.md). The execution board remains the canonical source for changing status values.

Use the linked `Epic` entry above to navigate to the shared epic-level product context for this backlog item.

## Problem

Processor transforms are field-centric and operate after reader output. Source-native adaptation requirements are different and should not overload processor-level business transforms.

## Goal

Introduce a clear pre-reader or source-adaptation seam that handles source-structure concerns while preserving processor transform/rule ownership for business-level mapping.

## Scope

- define source-native transform ownership boundaries
- define config shape for source-native adaptation rules
- define precedence with source validation and reader behavior
- define guardrails to avoid duplicate generic rewrites across source and processor layers

## Out of scope

- full transform-profile governance
- record-level or cross-record transformation stage design
- replacing processor transforms for normal business mapping

## Proposed approach

- keep source-native adaptation optional and explicit
- apply only to source-structure-sensitive use cases
- preserve shared runtime precedence and fail-fast validation

## Operator / runtime impact

- clearer separation of source-shape adaptation vs business transforms
- fewer ambiguous transform definitions across layers
- better diagnosability for source-contract issues

## Concrete transformation examples

```yaml
# conceptual source-native adaptation for XML before runtime records
sources:
  - format: xml
    sourceName: PartnerOrders
    filePath: input/orders.xml
    sourceTransforms:
      - type: xpathNormalize
        rules:
          - xpath: /Envelope/Body/Order/StatusCode
            valueMap:
              "01": NEW
              "02": UPDATE
```

```yaml
# processor-level transforms remain business-focused
mappings:
  - source: PartnerOrders
    target: PartnerOrdersCsv
    fields:
      - from: statusCode
        to: statusCode
        transforms:
          - type: trim
          - type: upperCase
```

Expected behavior:

- source-native transforms run before reader emits runtime records
- conflicts between source-native rewrites and processor transforms are rejected at startup
- source validation, source adaptation, processor transform, and processor rule precedence remains explicit

## Developer expectations

- use this seam only for source-structure concerns (XPath, namespace, token/header shape)
- keep generic business normalization in processor transforms
- emit clear failure categories when source adaptation fails vs processor transform/rule failure
- prove with at least one preserved XML scenario using namespace-aware or path-aware adaptation

## Acceptance criteria

- [ ] source-native transform seam and boundaries are documented
- [ ] startup validation behavior is defined for conflicts and invalid configs
- [ ] at least one XML-focused scenario demonstrates the seam when implemented
- [ ] precedence with source validation and processor rules is explicit

## Related docs

- [`Product backlog`](../../product-backlog.md)
- [`Transformation capability catalog`](../../../architecture/etl-core/transformation-capability-catalog.md)
- [`Extension points`](../../../architecture/etl-core/extension-points.md)
- [`OneFlow file parser capabilities`](../../../architecture/etl-core/oneflow-file-parser-capabilities.md)

## Implementation notes

Use this seam only for source-native adaptation. Generic business normalization should remain on processor transforms.

## Status notes

Deferred pending profile reuse baseline and parser-maturity alignment.




