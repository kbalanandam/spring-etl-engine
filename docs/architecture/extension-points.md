# Extension Points

## Purpose

This document explains where new capabilities should be added so the architecture stays coherent as the product grows.

## Status

- Classification: **Current baseline + future evolution**
- The Mermaid diagrams in this document describe the current baseline and the future evolution that should build from it.

## Current extension model

The engine is designed around three runtime extension points:

- reader implementations
- processor implementations
- writer implementations

These are selected dynamically based on config.

Validation and field-level processing behavior now use both shipped and planned extension points around the active runtime path:

- shipped source-level validation extensions for source artifact / source contract checks
- future source-level transform extensions for source-native adaptation before normal runtime records exist
- shipped processor-rule validation extensions for record acceptance / rejection checks
- processor-transform extensions for record cleaning / normalization before validation and write

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
- Current built-in source validators: `CsvSourceValidator`, `XmlSourceValidator`, `RelationalSourceValidator`
- Current built-in processor rules: `NotNullProcessorValidationRule`, `TimeFormatProcessorValidationRule`, `DuplicateProcessorValidationRule`
- Planned processor transform extension point: keep it adjacent to `src/main/java/com/etl/config/processor/ProcessorConfig.java`, `src/main/java/com/etl/processor/impl/DefaultDynamicProcessor.java`, and the mapping path under `src/main/java/com/etl/mapping/`

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

Current shipped target formats now include flat CSV, flat JSON, XML, and phase-1 relational targets. Flat JSON follows the same non-wrapper generated-model contract as other flat targets, while XML remains the only shipped target format with distinct processing-vs-write classes.

For the current file-based target writers, keep publication semantics aligned to the shared staged-file lifecycle: write to a sibling `.part` artifact first, promote to the configured final path only after successful step completion, and clean failed staged artifacts before they can be mistaken for published output.

When a selected explicit job omits `packageName` on source or target configs, keep the runtime aligned to the shared job-scoped derivation contract through `JobScopedPackageNameResolver` instead of reintroducing per-format handwritten package requirements.

## How to add a new processor type

1. implement `DynamicProcessor`
2. register the bean with a stable type name
3. reference that type from `processor-config.yaml`
4. verify it works with `ResolvedModelMetadata`

## How to add a new processor cleaner / normalization capability

For future field-cleaning behavior such as status-code decoding or country-code normalization:

1. keep the behavior inside the active default-processor path unless a truly new processor type is needed
2. model it as a processor **transform**, not as a processor **validation rule**
3. keep the execution order explicit: read → transform → validate → write
4. validate config fail-fast in `ConfigLoader`
5. add focused mapping/evaluator tests and at least one preserved scenario example

This avoids mixing “rewrite the value” behavior with “reject the record” behavior and gives future N-step cleaner chains a coherent extension home.

## Ownership and precedence rule

Use the most shared layer that can express the behavior correctly.

- default home: processor transforms for field-scoped cleanup such as `1 -> Success`, `USA -> US`, null fallback, trim, case normalization, and similar business/value rewrites
- reserved future home: source transforms only when the logic depends on source-native structure, parsing, selectors, or pre-flattening context such as XPath, namespaces, raw header/token cleanup, or other source-shape adaptation
- processor rules remain the only place for accept/reject decisions
- runtime-loaded allow-list/reference-set checks such as agency-code membership validation also belong to processor rules, not source validation

Planned runtime precedence should stay explicit:

1. source validation
2. source transforms, when a source-native case exists
3. reader emits the runtime record
4. processor transforms
5. processor rules
6. write accepted output / rejected-record output

That means transform-then-reject is a valid and expected flow. For example, a country code may be normalized to `UNKNOWN` first and then rejected by a processor rule.

Today, the shipped runtime already implements steps 1, 3, 5, and 6 on the active path. Steps 2 and 4 remain the intended future transform-extension seams.

## Config guardrails

To avoid ambiguous ownership once source transforms exist:

- omit a transform block entirely when no transform behavior is needed
- prefer ordered `transforms[]` lists for zero/one/many transform steps rather than many ad hoc boolean flags
- fail fast or at least warn when the same field is configured for equivalent generic value rewriting in both source and processor layers
- allow a layered flow when the concerns are different, for example source-native extraction followed by processor normalization and then processor rejection
- do not move record/business rejection logic into source validation or source transforms

## Planned YAML rollout

The planned rollout should stay narrow:

1. ship processor-side `mappings[].fields[].transforms[]` first on the active default-processor path
2. keep that block optional by omission and support ordered zero/one/many transform steps
3. defer a broad source-transform YAML model until a real source-native case justifies it

This keeps the first contract simple for customers who have no transform requirement while still giving advanced scenarios a clear growth path.

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

Read this as current baseline + future evolution for how new runtime seams should be added.

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
- fail fast when two runtime implementations try to register the same factory-dispatch key so extension wiring stays deterministic; the current bridge reader and writer factories both enforce this at registration time
- keep reader/writer factory registration and construction failures categorized as `factory`; the current bridge factories may still expose format-specific missing-dispatch exceptions such as `NoReaderFoundException` and `NoWriterFoundException`, while stream/read/write lifecycle failures should surface through the runtime failure category used by operator-facing diagnostics
- on the active reader path, CSV, XML, and relational readers currently share `RuntimeCategorizingItemStreamReader` so delegate `read` and optional `ItemStream` lifecycle failures are categorized consistently across source formats without duplicating that wrapper logic per reader
- CSV field binding on the active reader path now fails during mapper initialization when a configured field does not match a writable property on the target class, instead of silently skipping that mismatch
- reader-side adapters that implement Spring Batch contracts should preserve the framework nullability signature as part of the active runtime contract; for example, `FieldSetMapper.mapFieldSet` in `DynamicFieldSetMapper` should keep the package-level non-null return/parameter expectations exposed by Spring Batch
- document every new runtime path when it is introduced

## Likely next extension areas

- relational reader/writer support
- stored procedure tasklet / reader / writer support
- additional source validators and processor rules described in [`validation-extension-architecture.md`](validation-extension-architecture.md)
- reference-set validation and later lookup/enrichment growth described in [`reference-set-validation-and-enrichment.md`](reference-set-validation-and-enrichment.md)
- field transforms / normalization cleaners such as value mapping and code standardization
- multi-job flow configuration
- dialect abstraction for platform-specific SQL behavior

