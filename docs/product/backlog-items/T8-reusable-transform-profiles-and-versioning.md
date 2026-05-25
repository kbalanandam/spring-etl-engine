# T8 â€” Reusable transform profiles and versioning contract

## Summary

Define a reusable transformation-profile contract so common transform chains can be authored once, versioned, and referenced by multiple mappings/jobs instead of being duplicated across many processor configs.

## Current board status

- Epic: **[Epic T](../epics/epic-t-transformation-capability.md)**
- Priority: **P2**
- Status: **Deferred**
- Milestone: **M3**
- Dependency: **T3, T6**
- Sequence rank: **#1** in deferred advanced transform sequence

> Keep these fields synchronized with the row in [`product-backlog.md`](../product-backlog.md). The execution board remains the canonical source for changing status values.

Use the linked `Epic` entry above to navigate to the shared epic-level product context for this backlog item.

## Problem

Many mappings need the same transform chains (normalization, defaults, derived values). Copy/paste YAML increases drift risk and makes controlled change rollout hard.

## Goal

Enable profile-based transform reuse with explicit versioning and rollout semantics while preserving current explicit-step runtime behavior.

## Scope

- define transform-profile config shape and reference model
- define profile versioning and compatibility expectations
- define validation guardrails for missing/incompatible profile references
- define migration guidance from inline transforms to profile-based references

## Out of scope

- source-native transform seam design
- cross-record stateful transformation semantics
- broad governance workflow implementation

## Proposed approach

- keep current inline `transforms[]` contract working
- add optional profile references as an additive model
- resolve profile references during startup validation
- preserve runtime precedence: transform before processor rules

## Operator / runtime impact

- less duplicated transform config across jobs
- safer transform updates through explicit profile versions
- clearer impact analysis when changing shared transform logic

## Concrete transformation examples

```yaml
# conceptual profile definition
profiles:
  - name: customer-normalization
    version: 2.1.0
    fields:
      - to: countryCode
        transforms:
          - type: trim
          - type: upperCase
          - type: valueMap
            mappings:
              USA: US
              IND: IN
```

```yaml
# conceptual mapping reference to a shared profile
mappings:
  - source: CustomerRaw
    target: CustomerCsv
    transformProfileRef:
      name: customer-normalization
      version: 2.1.0
```

Expected behavior:

- startup fails fast when `transformProfileRef` is missing or version does not exist
- profile resolution is deterministic and visible in run evidence metadata
- inline `transforms[]` can coexist for job-specific overrides while profile usage remains explicit

## Developer expectations

- keep current inline transform behavior fully backward compatible
- perform profile reference resolution/validation during startup, not mid-run
- define clear version-compatibility rules (exact match first slice; relaxed matching only if explicitly designed)
- add preserved-scenario proof showing at least two mappings reusing the same profile

## Acceptance criteria

- [ ] profile config and reference contract are documented
- [ ] startup validation fails fast for invalid profile references
- [ ] at least one preserved scenario proves profile reuse
- [ ] compatibility/versioning behavior is documented for operators

## Related docs

- [`Product backlog`](../product-backlog.md)
- [`Transformation capability catalog`](../../architecture/etl-core/transformation-capability-catalog.md)
- [`Transformation capability roadmap`](../../architecture/etl-core/transformation-capability-roadmap.md)
- [`Default processor reference`](../../config/processor/default-processor.md)

## Implementation notes

Treat this as an additive extension first; do not break existing inline transform behavior while profile adoption is optional.

## Status notes

Deferred pending completion of higher-priority transformation maturity items.



