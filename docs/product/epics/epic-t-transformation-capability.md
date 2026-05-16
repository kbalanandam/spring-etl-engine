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

- [`T1 ? — Add field-level validation rules and first reject-handling slice for file scenarios`](../backlog-items/T1-field-level-validation-and-first-reject-handling-slice.md)
- [`T1a ? — Define processor transform SPI and first cleaner/normalization slice`](../backlog-items/T1a-processor-transform-spi-and-first-cleaner-normalization-slice.md)
- [`T2 ? — Add expression-based derived field support`](../backlog-items/T2-expression-based-derived-field-support.md)
- [`T3 ? — Add conditional transformation rule support`](../backlog-items/T3-conditional-transformation-rule-support.md)
- [`T4 ? — Expand validation and rejected-record/quarantine handling in transformation flow`](../backlog-items/T4-transformation-quarantine-and-duplicate-hardening.md)
- [`T5 ? — Define lookup/enrichment processor baseline`](../backlog-items/T5-reference-set-validation-and-enrichment-baseline.md)
- [`T6 ? — Add shared default-value and placeholder mapping baseline`](../backlog-items/T6-shared-default-value-and-placeholder-mapping.md)

## Related docs

- [`../../architecture/transformation-capability-roadmap.md`](../../architecture/transformation-capability-roadmap.md)
- [`../../architecture/extension-points.md`](../../architecture/extension-points.md)
- [`../../architecture/reference-set-validation-and-enrichment.md`](../../architecture/reference-set-validation-and-enrichment.md)
- [`../../architecture/t6-shared-default-value-mapping-syntax-comparison.md`](../../architecture/t6-shared-default-value-mapping-syntax-comparison.md)
- [`../../config/processor/default-processor.md`](../../config/processor/default-processor.md)

## Maintenance note

Use [`../product-backlog.md`](../product-backlog.md) for execution-board status. Use this page for the shared transformation-capability intent and boundary that spans multiple `T*` items.

