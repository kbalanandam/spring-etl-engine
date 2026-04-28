# ADR-0006: separate source validation and processor rule SPIs

- Status: Accepted
- Date: 2026-04-25

## Context

`spring-etl-engine` now has an active validation path in two places:

- source/config validation through the active config loading path
- record-level validation through `processor-config.yaml` field rules and `ValidationRuleEvaluator`

At the same time, the repository still contains a deprecated legacy standalone validation framework under `com.etl.validation.*` and a deprecated `validation-config.yaml` resource.

Future work needs more extensibility, but not another parallel validation framework.

The main tension is:

- we need extensibility for new source-level and processor-level validations
- we do not want future contributors to extend the deprecated legacy package and drift away from the active runtime architecture

## Decision

The product will keep two separate future extension seams:

1. a **source validation SPI** for source artifact / source contract validation
2. a **processor rule SPI** for record-level acceptance and rejection rules

The product will not revive `validation-config.yaml` or the deprecated `com.etl.validation.*` package as the main extension model.

## Consequences

### Positive

- validation growth stays aligned to the active ETL runtime path
- file-level validation and record-level validation remain clearly separated
- future XML/XSD validation has a clean home in the source validation seam
- future regex/range/cross-field validation has a clean home in the processor rule seam
- contributors retain extensibility without being directed to deprecated code paths

### Negative

- the first shipped slice keeps a simpler implementation now and may need future refactoring into explicit SPIs
- some useful legacy concepts such as `XsdValidationRule` may need to be reimplemented or rehomed instead of reused in place
- repository cleanup now has to preserve deprecated code temporarily while the new extension seams are documented and later implemented

## Notes

- `XsdValidationRule` maps conceptually to the future source validation SPI
- `RegexRule` maps conceptually to the future processor rule SPI
- the shipped current contract remains source config + processor config + explicit `job-config.yaml` orchestration

## Related

- [`../architecture/validation-extension-architecture.md`](../architecture/validation-extension-architecture.md)
- [`../architecture/file-ingestion-hardening.md`](../architecture/file-ingestion-hardening.md)
- [`0004-use-explicit-job-config-for-business-scenario-selection.md`](0004-use-explicit-job-config-for-business-scenario-selection.md)

