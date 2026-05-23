# ADR-0011: enforce one shared default processor contract on selected-job runtime

- Status: Accepted
- Date: 2026-05-23

## Context

The selected-job runtime (`etl.config.job`) now uses explicit `job-config.yaml` steps and one active processor path.

Before the final cutover, processor selection still carried bridge-era compatibility with legacy or custom processor type values.
That split increased startup ambiguity and made it easier for bundles to drift away from the shipped processor-rule and transform model.

The active runtime model already routes processor behavior through:

- `DefaultDynamicProcessor`
- processor transforms (`ProcessorFieldTransform`)
- processor rules (`ProcessorValidationRule`)
- processor extension providers (`ProcessorExtensionProvider`)

## Decision

For selected-job runs, the runtime accepts only:

- `processor-config.yaml` with `type: default`

The cutover is enforced in two places:

1. startup config validation in `ConfigLoader` (primary fail-fast boundary)
2. defensive runtime guard in `DynamicProcessorFactory` (secondary boundary)

Legacy/custom/blank processor types are rejected with clear migration-oriented errors.

## Consequences

### Positive

- one unambiguous processor contract for selected-job runtime
- fail-fast startup behavior before step assembly/runtime execution
- preserved bundles can be validated consistently (`config-jobs/**/processor-config*.yaml`)
- processor evolution stays on shared transform/rule/provider extension seams

### Negative

- older bundles that relied on legacy/custom processor type values must be migrated
- contributors must avoid reintroducing alternate processor-type dispatch without an explicit ADR

## Migration guidance

- set `processor-config.yaml` to `type: default`
- migrate custom behavior into:
  - `mappings[].fields[].transforms[]`
  - `mappings[].fields[].rules[]`
  - processor extension providers where custom transform/rule implementations are required

## Related

- [`ADR-0002: config-driven ETL pipeline`](0002-config-driven-etl-pipeline.md)
- [`ADR-0004: explicit job-config selection`](0004-use-explicit-job-config-for-business-scenario-selection.md)
- [`Extension points`](../architecture/extension-points.md)
- [`Runtime flow`](../architecture/runtime-flow.md)
- [`Default processor reference`](../config/processor/default-processor.md)

