# Epic T — Transformation capability

## Summary

Epic T covers how the product evolves from structural mapping into richer transformation behavior while keeping the active runtime centered on the processor path. It groups the shipped validation and transform slices with the deferred enrichment and conditional-processing work.

## Scope

This epic is the home for work that:

- expands processor-side transforms and validation rules
- adds derived-field, conditional, lookup, enrichment, and default-value behavior
- hardens rejected-record and quarantine handling for transformation flows
- preserves a clear transform-before-validation execution model on the active path

This epic is **not** the place for source-transport concerns, orchestration/scheduler control, or generated-model naming governance.

## Related backlog items

- [`T1 — Add field-level validation rules and first reject-handling slice for file scenarios`](../backlog-items/T1-field-level-validation-and-first-reject-handling-slice.md)
- [`T1a — Define processor transform SPI and first cleaner/normalization slice`](../backlog-items/T1a-processor-transform-spi-and-first-cleaner-normalization-slice.md)
- [`T2 — Add expression-based derived field support`](../backlog-items/T2-expression-based-derived-field-support.md)
- [`T3 — Add conditional transformation rule support`](../backlog-items/T3-conditional-transformation-rule-support.md)
- [`T4 — Expand validation and rejected-record/quarantine handling in transformation flow`](../backlog-items/T4-transformation-quarantine-and-duplicate-hardening.md)
- [`T5 — Define lookup/enrichment processor baseline`](../backlog-items/T5-reference-set-validation-and-enrichment-baseline.md)
- [`T6 — Add shared default-value and placeholder mapping baseline`](../backlog-items/T6-shared-default-value-and-placeholder-mapping.md)
- [`T7 — Define duplicate-tracking scalability redesign as a separate deferred track`](../backlog-items/T7-duplicate-tracking-scalability-redesign-deferment.md)
- [`T8 — Define reusable transform profiles and versioning contract`](../backlog-items/T8-reusable-transform-profiles-and-versioning.md)
- [`T9 — Define source-native transformation seam before runtime records`](../backlog-items/T9-source-native-transformation-seam.md)
- [`T10 — Define record-level transformation stage beyond field-centric mapping`](../backlog-items/T10-record-level-transformation-stage.md)
- [`T11 — Define cross-record window and aggregation transformation semantics`](../backlog-items/T11-cross-record-window-and-aggregation-transforms.md)
- [`T12 — Define transformation governance and lineage evidence model`](../backlog-items/T12-transformation-governance-and-lineage.md)
- [`T13 — Define transform-stage observability metrics and operational evidence`](../backlog-items/T13-transform-stage-observability-metrics.md)
- [`T14 — Define secure data-shaping transforms for sensitive fields`](../backlog-items/T14-secure-data-shaping-transforms.md)
- [`T15 — Define XML-native duplicate identity for nested XML source scenarios`](../backlog-items/T15-xml-native-duplicate-identity-for-nested-xml-sources.md)

## Recommended deferred sequencing (T8-T14)

Use this dependency-aware order when Epic T advanced items are activated:

1. `T8` - reusable profile contract and versioning baseline
2. `T10` - record-level stage semantics on top of profile-ready inputs
3. `T12` - governance/lineage identity model anchored to profile/version semantics
4. `T13` - transform-stage observability metrics aligned to record-level boundaries
5. `T9` - source-native adaptation seam after parser-maturity anchor `P3`
6. `T14` - secure data-shaping transforms after profile baseline and secure-config anchor `G1`
7. `T11` - cross-record/window semantics only after `T10` and restart baseline `F1`

This sequence is a recommended start order, not a replacement for execution-board `Dependency` fields in [`product-backlog.md`](../product-backlog.md).

## Related docs

- [`../transformation-checkpoint.md`](../transformation-checkpoint.md)
- [`../../architecture/transformation-capability-roadmap.md`](../../architecture/transformation-capability-roadmap.md)
- [`../../architecture/extension-points.md`](../../architecture/extension-points.md)
- [`../../architecture/reference-set-validation-and-enrichment.md`](../../architecture/reference-set-validation-and-enrichment.md)
- [`../../architecture/t6-shared-default-value-mapping-syntax-comparison.md`](../../architecture/t6-shared-default-value-mapping-syntax-comparison.md)
- [`../../config/processor/default-processor.md`](../../config/processor/default-processor.md)

## Maintenance note

Use [`../product-backlog.md`](../product-backlog.md) for execution-board status. Use this page for the shared transformation-capability intent and boundary that spans multiple `T*` items.

