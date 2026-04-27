# Extension Points

## Purpose

This document explains where new capabilities should be added so the architecture stays coherent as the product grows.

## Current extension model

The engine is designed around three runtime extension points:

- reader implementations
- processor implementations
- writer implementations

These are selected dynamically based on config.

Validation is now implemented as two additional extension seams on the active runtime path:

- source-level validation extensions for source artifact / source contract checks
- processor-rule validation extensions for record acceptance / rejection checks

## Current code anchors

- Reader selection: `src/main/java/com/etl/reader/DynamicReaderFactory.java`
- Processor selection: `src/main/java/com/etl/processor/DynamicProcessorFactory.java`
- Writer selection: `src/main/java/com/etl/writer/DynamicWriterFactory.java`
- Source polymorphism: `src/main/java/com/etl/config/source/SourceConfig.java`
- Target polymorphism: `src/main/java/com/etl/config/target/TargetConfig.java`
- Format enum: `src/main/java/com/etl/enums/ModelFormat.java`
- Source validation SPI: `src/main/java/com/etl/config/source/validation/SourceValidator.java`
- Source validation dispatch: `src/main/java/com/etl/config/source/validation/SourceValidationService.java`
- Processor rule SPI: `src/main/java/com/etl/processor/validation/ProcessorValidationRule.java`
- Processor rule dispatch: `src/main/java/com/etl/processor/validation/ValidationRuleEvaluator.java`

## How to add a new source/target format

### New source format
1. add a new `ModelFormat` enum value if needed
2. create a new `SourceConfig` subtype
3. register it in `SourceConfig` polymorphic deserialization
4. implement a matching `DynamicReader`
5. add focused tests for config loading and reading behavior
6. update architecture docs and add an ADR if the design introduces a new runtime pattern

### New target format
1. create a new `TargetConfig` subtype
2. register it in `TargetConfig` polymorphic deserialization
3. implement a matching `DynamicWriter`
4. handle any special write-class / wrapper contract explicitly
5. add tests and docs

## How to add a new processor type

1. implement `DynamicProcessor`
2. register the bean with a stable type name
3. reference that type from `processor-config.yaml`
4. verify it works with `ResolvedModelMetadata`

## Design rule for future relational support

When relational DB support is added, keep these concerns separate:

### Shared connection concern
Use a shared object such as `RelationalConnectionConfig` for:
- vendor
- jdbcUrl
- host
- port
- schema
- credentials
- connection properties

### Source concern
Use a source config for:
- table
- query
- fetch size
- incremental read settings

### Target concern
Use a target config for:
- table
- write mode
- batch size
- upsert keys

### Procedure concern
Do not force stored procedures entirely into source/target classes. Treat them as an explicit operation type when that feature is introduced.

## Recommended future extension points

```mermaid
flowchart LR
    A[Config subtype] --> B[Factory registration]
    B --> C[Runtime implementation]
    C --> D[Tests]
    D --> E[Architecture doc update]
    E --> F[ADR if design changed]
```

## Practical architecture rules

- prefer composition over vendor-specific config duplication
- centralize runtime contracts instead of scattering conventions
- add new behaviors through factories instead of conditionals spread across the codebase
- document every new runtime path when it is introduced

## Likely next extension areas

- relational reader/writer support
- stored procedure tasklet / reader / writer support
- additional source validators and processor rules described in [`validation-extension-architecture.md`](validation-extension-architecture.md)
- multi-job flow configuration
- dialect abstraction for platform-specific SQL behavior

