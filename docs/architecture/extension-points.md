# Extension Points

## Purpose

This document explains where new capabilities should be added so the architecture stays coherent as the product grows.

## Current extension model

The engine is designed around three runtime extension points:

- reader implementations
- processor implementations
- writer implementations

These are selected dynamically based on config.

Validation and field-level processing behavior are now implemented or planned as additional extension points on the active runtime path:

- source-level validation extensions for source artifact / source contract checks
- future source-level transform extensions for source-native adaptation before normal runtime records exist
- processor-rule validation extensions for record acceptance / rejection checks
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

Planned runtime precedence should stay explicit:

1. source validation
2. source transforms, when a source-native case exists
3. reader emits the runtime record
4. processor transforms
5. processor rules
6. write accepted output / rejected-record output

That means transform-then-reject is a valid and expected flow. For example, a country code may be normalized to `UNKNOWN` first and then rejected by a processor rule.

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
- field transforms / normalization cleaners such as value mapping and code standardization
- multi-job flow configuration
- dialect abstraction for platform-specific SQL behavior

