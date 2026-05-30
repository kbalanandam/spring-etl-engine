# A7 + T16 extensibility charter

## Purpose

Define one bounded extensibility model that combines:

- job-level custom steps (`A7`) for workflow side effects and cross-step handoff
- processor-level custom transforms (`T16`) for field-level value shaping

The goal is to keep OneFlow scalable for customer-specific needs while preserving the shipped runtime contract and adoption simplicity.

## Status

- Classification: **Future direction**
- Backlog anchors:
  - [`A7 - Add custom-step pairing, context handoff, and failure-contract baseline`](../../product/backlog-items/etl-core/A7-custom-step-pairing-context-handoff-and-failure-contract.md)
  - [`T16 - Define customer-owned processor transform extension seam`](../../product/backlog-items/etl-core/T16-customer-owned-processor-transform-extension-seam.md)

## Extensibility split

### Job-level custom steps (`A7`)

Use custom steps when behavior is orchestration- or lifecycle-oriented:

- pre/post business side effects (for example header start/finalize)
- controlled cross-step context handoff (for example `header.fileId`)
- bounded job-level failure finalization callbacks

### Processor-level custom transforms (`T16`)

Use custom transforms when behavior is field-level value shaping:

- replace nulls with defaults
- translate partner-specific strings/codes
- apply project-specific normalization before rules run

## Joint invariants

1. One selected job per run; one explicit ordered `steps[]` plan.
2. One processor path remains active (`type: default`).
3. Steps and transforms stay additive; standard-only jobs remain backward compatible.
4. Ownership stays clear: steps orchestrate, transforms rewrite values, rules accept/reject.
5. No second runtime engine or alternate launch contract is introduced.
6. Failure categories align with Epic D vocabulary.
7. Operator evidence remains shared and additive across standard/custom behavior.

## Failure taxonomy alignment (Epic D)

Use one shared vocabulary direction across both seams:

- `config`: invalid authored shape or required field combinations
- `binding`: unresolved type/provider conflicts
- `context`: missing/invalid cross-step context keys (A7 path)
- `execution`: runtime exceptions in custom handlers/transforms

## Phase-1 boundaries

Keep the first implementation slices narrow:

- A7: custom step seam, context bridge, outcome mapping, failure finalizer
- T16: field-scoped transform extensibility on existing transform SPI
- Out of scope in phase-1: source-native transform seam (`T9`), record-level stage (`T10`), cross-record transforms (`T11`)

## Review checklist before implementation

1. Runtime review confirms one assembly path and deterministic ordering.
2. Product/backlog review confirms status, dependencies, and acceptance criteria.
3. Taxonomy review confirms Epic D category mapping and evidence fields.
4. Docs review confirms cross-links across config, architecture, epic, and backlog pages.

## Related docs

- [`ETL custom-step pairing and context handoff`](custom-step-pairing-and-context-handoff.md)
- [`Customer-owned processor transform seam`](customer-owned-processor-transform-seam.md)
- [`Extension points`](extension-points.md)
- [`Epic D - Error taxonomy and failure categorization`](../../product/epics/etl-core/epic-d-error-taxonomy-and-failure-categorization.md)


