# ADR-0003: Use adaptive step selection and a centralized generated-model contract

- Status: Accepted
- Date: 2026-04-18

## Context

The current ETL engine supports different source sizes and format-specific model requirements, especially for XML targets where processing and writing may use different classes.

Two important behaviors already exist:

1. `BatchConfig` selects chunk vs tasklet execution based on source record count and `etl.chunk.threshold`.
2. `GeneratedModelClassResolver` centralizes runtime naming and wrapper metadata for generated models.

## Decision

The system will keep:

- adaptive chunk/tasklet step selection for runtime efficiency
- a single centralized resolver for generated model class names and XML wrapper metadata

## Consequences

### Positive
- orchestration can optimize runtime strategy without format-specific branching everywhere
- XML-specific wrapper behavior stays defined in one place
- downstream readers, processors, and writers can rely on a stable metadata contract

### Negative
- generated model naming is now an architectural contract that must be preserved
- new formats with multiple runtime class shapes will need resolver-aware extensions
- richer future orchestration may need a more general step model than current source-target pairing

## Implications for future work

Future enhancements should build from this pattern:
- relational operations may resolve different read vs write classes
- stored procedure steps may require additional metadata contracts
- multi-job orchestration may need step-level operation metadata beyond current source/target config objects

