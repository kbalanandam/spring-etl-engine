# T6 — Shared default-value and placeholder mapping

## Summary

Define a product-level config contract for common target-field defaults so jobs can fill audit columns and other required fields without repeating the same per-field authoring in every mapping. The first focus is shared defaults for columns such as `createdUser`, `updatedUser`, `createdDate`, and `updatedDate`, plus a reusable placeholder/formula path for fixed values and derived runtime values.

## Current board status

- Epic: **Epic T**
- Priority: **P1**
- Status: **Deferred**
- Milestone: **M2**
- Dependency: **T2**

> Keep these fields synchronized with the row in [`product-backlog.md`](../product-backlog.md). The execution board remains the canonical source for changing status values.

## Problem

The shipped processor contract already supports direct field mapping, `valueMap`, and `expression` transforms, but it still makes common defaulting work too repetitive:

- audit fields such as `createdUser` / `updatedUser` and `createdDate` / `updatedDate` must be authored field by field
- there is no shared placeholder contract for common runtime values such as selected job name, fixed constants, or a standard current timestamp value
- config authors must think in low-level field-by-field transform terms even when the intent is only “fill the usual audit columns”
- future relational jobs will keep reintroducing the same mapping noise unless this becomes a first-class product pattern

That is especially visible in file-to-relational and multi-step jobs where source data does not naturally carry all required target audit columns.

## Goal

Add one explicit config pattern for shared default-value assignment so the product can fill common audit and operational columns predictably, while still allowing scenario authors to supply fixed values or formula-derived values for other fields when needed.

## Scope

This item covers:

- shared defaulting for common audit columns such as `createdUser`, `updatedUser`, `createdDate`, and `updatedDate`
- support for filling selected fields from the chosen job name or another explicit constant
- one product-defined default “current timestamp” behavior for generated values, including one standard rendered format where string/date formatting is needed
- a placeholder or formula-based path for other fixed values and derived runtime values
- validation guardrails so defaulted fields do not conflict with normal `from -> to` mapping or ordered `transforms[]`
- documentation and example-job updates once implementation begins

## Out of scope

This item does not cover:

- a generic rules engine or broad scripting platform
- secret injection or environment-secret management; keep that under `G1`
- database-trigger-only or database-default-only solutions as the sole product contract
- lookup/enrichment joins against external reference data; keep that under `T5`
- changing the selected-job runtime model or moving transformation behavior out of the processor seam by default

## Proposed approach

The preferred direction is:

1. keep the active processor transform path as the main extension seam instead of adding hidden target-writer-specific default injection
2. introduce an additive default-value contract for target fields that need generated or shared values rather than source-derived input
3. provide short standardized runtime placeholders for common values such as selected job name, explicit constants, and a shared “current timestamp” token
4. preserve `expression` for richer formulas, but avoid forcing every common audit column to be written as custom SpEL in each job
5. fail fast when the same field is assigned ambiguously through both normal source mapping and a default/placeholder rule
6. document one common default timestamp policy so users do not need to keep authoring format details for standard audit columns

## Operator / runtime impact

Expected impact when this item ships:

- relational-target configs become smaller and more consistent across jobs
- common audit-column behavior becomes explicit instead of being hidden in ad hoc field mappings
- startup validation can point clearly to conflicting or unsupported placeholder/default definitions
- operators get more predictable metadata population across jobs without custom Java code
- docs can show one standard way to fill technical columns that are not present in the business source payload

## Acceptance criteria

- [ ] the product defines one documented contract for shared default-value mapping on top of the active processor transform seam
- [ ] common audit-column defaults can be configured without repeating full custom logic in every job bundle
- [ ] the selected job name and explicit constants can be used as supported generated values for target fields
- [ ] the product defines one default current-timestamp behavior and one standard rendered format for the common audit use case
- [ ] config validation fails fast for unknown placeholders, ambiguous double assignment, and incompatible field/default combinations
- [ ] at least one relational-style preserved or private job shows the intended pattern when implementation begins

## Related docs

- [`Product backlog`](../product-backlog.md)
- [`Transformation capability roadmap`](../../architecture/transformation-capability-roadmap.md)
- [`Default processor reference`](../../config/processor/default-processor.md)
- [`ADR-0007: separate processor transform SPI for cleaning and normalization`](../../adr/0007-add-separate-processor-transform-spi-for-cleaning-and-normalization.md)

## Implementation notes

Current code anchors for this future item are:

- `src/main/java/com/etl/config/processor/ProcessorConfig.java`
- `src/main/java/com/etl/processor/transform/TransformEvaluator.java`
- `src/main/java/com/etl/processor/transform/ValueMapProcessorTransform.java`
- `src/main/java/com/etl/processor/transform/ExpressionProcessorTransform.java`

The important product guardrail is to keep this explicit and config-driven. Audit/default filling should not become a hidden writer-side behavior that differs by target type.

The first delivery slice should also decide and document what “server timestamp” means for the product contract: application-runtime generated current time, target-database generated value, or an explicitly selectable mode. Until that is decided, the backlog item should stay focused on the contract and guardrails rather than on a fixed syntax.

## Status notes

Added as a follow-on transformation-maturity item because the shipped `valueMap` and `expression` support already prove the processor transform seam, but shared audit/default authoring still lacks a first-class reusable product contract.
