# 1.4.x to Next Architecture Classification

## Purpose

This note classifies the current `1.4.x` codebase so the next architecture can move forward without mixing new work into paths that no longer fit the target direction.

The immediate next direction is:

- build-time generated model classes instead of runtime generation/compilation
- shared generation utilities first for XML, CSV, and relational formats
- job-driven preserved business flows
- selective reuse of shared runtime utilities such as processor validation, duplicate handling, and logging
- gradual replacement of the current runtime assembly rather than a blind rewrite in one step

This page is intentionally practical. It tells contributors what to reuse, what to bridge temporarily, what to avoid extending, and what should eventually be removed.

## Classification legend

| Status | Meaning | Rule for next development |
|---|---|---|
| `REUSE` | Fits the next direction with little or moderate adaptation | Safe to call from new code. Prefer wrapping instead of rewriting when possible. |
| `BRIDGE` | Useful during migration but not necessarily the final target design | Keep stable enough to support migration. Do not grow it into the final architecture. |
| `LEGACY` | Old path retained only for compatibility, reference, or controlled maintenance | Do not add new feature work here. Only bug fixes or migration support if absolutely necessary. |
| `REMOVE` | Planned cleanup target once replacement exists | Do not build new dependencies on it. Delete after the replacement is proven. |

## Default next-development rule

When a new development task starts:

1. check this classification first
2. implement new architecture work under a clearly new package root where practical
3. reuse only `REUSE` code directly
4. touch `BRIDGE` code only when required to connect old and new paths
5. avoid feature work in `LEGACY`
6. do not create new dependencies on `REMOVE`

## Package and class classification

| Package / area | Representative classes or resources | Status | Why | Next-development rule |
|---|---|---|---|---|
| `com.etl.config.job` | `JobConfig` | `REUSE` | Explicit job-level selection still fits the next direction even if orchestration evolves later. | Keep as a stable run-selection contract unless a stronger replacement is introduced deliberately. |
| `com.etl.config.source` | `SourceConfig`, `CsvSourceConfig`, `XmlSourceConfig`, `RelationalSourceConfig`, `SourceWrapper` | `REUSE` | Format-specific config contracts remain useful for generator inputs and runtime selection. | Extend carefully only when the new generator/runtime contract truly needs new structural fields. |
| `com.etl.config.target` | `TargetConfig`, `CsvTargetConfig`, `XmlTargetConfig`, `RelationalTargetConfig`, `TargetWrapper` | `REUSE` | Target config contracts still fit the next direction and remain useful for generated target models and runtime writer selection. | Reuse as the target-side config baseline. |
| `com.etl.config.processor` | `ProcessorConfig` | `REUSE` | Shared processor validation and mapping remain strong candidates for reuse in the next architecture. | Keep as the shared processor contract unless a real processor-profile model is needed later. |
| `com.etl.config.source.validation` | `SourceValidationService`, `CsvSourceValidator`, `XmlSourceValidator`, `RelationalSourceValidator` | `REUSE` | Source validation stays relevant and should remain outside processor rules. | Reuse and extend for source-native validation only. |
| `com.etl.processor.validation` | `ValidationRuleEvaluator`, `ProcessorValidationRule`, `NotNullProcessorValidationRule`, `DuplicateProcessorValidationRule`, `TimeFormatProcessorValidationRule` | `REUSE` | Shared record-level validation is still aligned with the future shared processor direction. | Keep as shared processor infrastructure. |
| `com.etl.runtime` | `FileIngestionRuntimeSupport`, `DuplicateRule`, `DuplicateResolverFactory`, `InMemoryDuplicateResolver`, `EmbeddedDbDuplicateResolver` | `REUSE` | Duplicate handling and reject/runtime support are shared platform concerns, not scenario-specific concerns. | Reuse selectively in the next runtime; keep contracts focused and independent of old orchestration. |
| `com.etl.logging` | `RunLoggingContext` | `REUSE` | Run/job/scenario logging context remains useful regardless of orchestration redesign. | Reuse with terminology cleanup later if needed. |
| `com.etl.reader` and `com.etl.writer` factories | `DynamicReaderFactory`, `DynamicWriterFactory`, `DynamicReader`, `DynamicWriter` | `REUSE` | Factory-based extension points still fit the long-term design. | Keep the extension-point idea; implementations may evolve behind the factory boundary. |
| `com.etl.processor` factory | `DynamicProcessorFactory`, `DynamicProcessor` | `REUSE` | Shared processor selection remains a valid extension seam. | Keep and adapt only if processor profiles later need stronger selection rules. |
| `com.etl.reader.impl` | `CsvDynamicReader`, `XmlDynamicReader`, `RelationalDynamicReader` | `BRIDGE` | Reader implementations are useful, but some behavior depends on the current generated-model/runtime assumptions. | Reuse carefully while the new build-time generated model contract is stabilized. |
| `com.etl.writer.impl` | `CsvDynamicWriter`, `XmlDynamicWriter`, `SingleObjectXmlWriter`, `RelationalDynamicWriter` | `BRIDGE` | Writers remain useful, especially XML wrapper handling, but current assumptions may change with the new generated-model layout. | Keep for migration; refactor only behind stable writer contracts. |
| `com.etl.mapping` | `DynamicMapping`, `ValidationAwareDynamicMapping`, `MappingEngine` | `BRIDGE` | Mapping logic is still valuable, but it is tied to the current runtime flow and generated class assumptions. | Reuse if needed, but do not let this package become the final home for next-generation orchestration decisions. |
| `com.etl.config` orchestration classes | `ConfigLoader`, `BatchConfig`, `RunConfigurationMetadata`, `ModelPathConfig` | `BRIDGE` | These classes are central today but the next direction likely changes generation lifecycle and orchestration shape. | Keep stable enough to support migration. Avoid growing them with new architecture-specific behavior unless it is truly transitional. |
| `com.etl.runner` | `EtlJobRunner` | `BRIDGE` | Current run entry is still useful while migration happens, but final job-launch flow may evolve. | Use as a temporary launch bridge, not as the final orchestration design center. |
| `com.etl.job.listener` | `StepLoggingContextListener`, `JobCompletionNotificationListener`, `FileIngestionHardeningStepListener` | `BRIDGE` | Operational listeners still provide value, but they are tied to the current batch assembly path. | Reuse as needed; revisit after the next runtime assembly is in place. |
| `com.etl.relational` and `com.etl.config.relational` | `DatabaseDialect`, `DatabaseDialectResolver`, `RelationalDataSourceFactory`, `RelationalConnectionConfig` | `REUSE` | Relational platform abstractions remain relevant for generator and runtime hardening work. | Keep as the relational baseline. |
| `com.etl.common.util` general helpers | `ValidationUtils`, `StringUtils`, `ReflectionUtils`, `DynamicBatchUtils` | `REUSE` | General utility helpers remain useful. | Reuse selectively; move or slim only when justified by new architecture needs. |
| `com.etl.common.util.GeneratedModelClassResolver` and `ResolvedModelMetadata` | `GeneratedModelClassResolver`, `ResolvedModelMetadata` | `BRIDGE` | The generated-model contract is still strategically important, but the current naming and wrapper assumptions are tied to runtime generation and current packages. | Preserve as the compatibility boundary while build-time generation is introduced. Expect evolution rather than immediate deletion. |
| `com.etl.model.generator.support` | `GeneratedSourcePathResolver`, `JavaTypeNameResolver` | `REUSE` | These support classes are directly relevant to the new generation-utility-first direction. | Reuse, but align them to build-time generated output roots instead of handwritten source folders. |
| `com.etl.model.generator` format generators | `CsvModelGenerator`, `RelationalModelGenerator`, `XmlModelGenerator`, `ModelGenerator` | `BRIDGE` | These are the strongest starting point for the new build-time generation utilities, but the lifecycle and output strategy still need redesign. | Reuse the generation logic selectively. Refactor toward build-time generation rather than startup-triggered generation. |
| `com.etl.model.generator.ModelGeneratorFactory` | `ModelGeneratorFactory` | `LEGACY` | Current behavior is tied to `dev` profile startup generation, runtime compilation, and dynamic class loading. That is not the target production model. | Use only as reference during migration. Do not extend the runtime generation path. |
| Generated model source packages under handwritten source tree | `com.etl.model.source.*`, `com.etl.model.target.*`, nested generated directories such as `com\etl\model\source\com\etl\model\source\xml\*` | `LEGACY` | These are output artifacts from the old generation approach and are mixed into handwritten source paths. The next direction is build-time generation into generated-sources output. | Do not treat these packages as the future package layout. Replace with build-time generated packages. |
| `com.etl.processor.impl.DefaultDynamicProcessor` | `DefaultDynamicProcessor` | `REUSE` | Shared processor behavior is still a likely long-term platform asset. | Keep as the shared processor baseline unless the new design proves a stronger abstraction is needed. |
| `com.etl.processor.impl.CustomerProcessor` | `CustomerProcessor` | `LEGACY` | Scenario/entity-specific processor logic does not fit the new shared processor direction. | Do not add new scenario-specific processors here. Replace with job/source-specific adapters or shared processor rules as appropriate. |
| `com.etl.validation` | `Validator`, `ValidatorFactory`, `ValidationConfigLoader`, `ValidationRule`, `RegexRule`, `XsdValidationRule`, `validation-config.yaml` | `LEGACY` | The repository already documents this path as deprecated and outside the active runtime flow. | No new feature work. Keep only until fully safe to remove. |
| `com.etl.common.util.TypeConversionUtils#mapToJavaType` | `mapToJavaType(String)` | `REMOVE` | Already marked deprecated and unused in favor of generator-specific type resolution. | Remove when the next cleanup window is opened. |
| Demo fallback-only runtime assumptions | `etl.config.allow-demo-fallback`, direct repo resource defaults under `src/main/resources/*-config.yaml` | `BRIDGE` | Demo fallback is still useful locally, but it is not the target enterprise execution model. | Keep for local/demo safety only. Do not build new product behavior around it. |

## Initial reuse set for the next direction

The most promising early reuse set is:

- `com.etl.config.job.JobConfig`
- source, target, and processor config models under `com.etl.config.*`
- `SourceValidationService` and the active source validators
- `ValidationRuleEvaluator` and processor validation rules
- `FileIngestionRuntimeSupport` plus duplicate resolver support
- reader/processor/writer factory interfaces and factories
- `GeneratedSourcePathResolver` and `JavaTypeNameResolver`
- selected logic from `CsvModelGenerator`, `RelationalModelGenerator`, and `XmlModelGenerator`

## Initial bridge set for the next direction

The most practical bridge set is:

- `ConfigLoader`
- `BatchConfig`
- `RunConfigurationMetadata`
- `EtlJobRunner`
- `GeneratedModelClassResolver`
- current reader and writer implementations
- current mapping package

These should help the migration proceed, but they should not quietly become the final architecture center.

## Initial legacy or cleanup targets

The clearest early legacy or cleanup targets are:

- `com.etl.validation.*`
- `src/main/resources/validation-config.yaml`
- startup/runtime generation flow centered on `ModelGeneratorFactory`
- generated model classes written into handwritten source folders
- `CustomerProcessor`
- `TypeConversionUtils#mapToJavaType`

## Future cleanup rule

A `LEGACY` or `REMOVE` item should be deleted only after:

1. its replacement exists
2. migration tests or verification evidence pass
3. no active runtime path depends on it
4. the migration index is updated in the same change

## Recommended next step

Start the next architecture work in the generation utility path first:

- build-time generated classes
- separate generated output roots
- shared XML / CSV / relational generation support
- preserve flattening and scenario-specific business semantics outside the shared generators

That direction gives the clearest way to reuse the useful parts of `1.4.x` without carrying forward the parts that no longer fit.

