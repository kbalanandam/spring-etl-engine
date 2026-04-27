# File Ingestion Hardening Checklist

## Purpose

This checklist turns the proposed design in [`file-ingestion-hardening.md`](file-ingestion-hardening.md) into an execution-ready implementation plan without changing the agreed scope.

Use it to answer three questions before coding starts:

1. which classes and config contracts are expected to change first
2. which tests should be added before the slice is considered done
3. which preserved realistic scenario should prove the slice end to end

This note is still design-oriented. It is a checklist for implementation readiness, not proof that the runtime already supports the behavior.

## Scope anchors

The first slice stays intentionally narrow.

### In scope

- CSV file source as the first supported source type
- field-level validation rules in processor mappings
- initial rules:
  - `notNull`
  - `timeFormat`
- rejected-record output with reason metadata
- processed-source-file archiving after successful step completion
- operator-visible counts for read / accepted / rejected / written
- one preserved realistic file scenario with mixed valid and invalid rows

### Explicitly out of scope for this slice

- XML-specific nested validation behavior
- relational-source archive handling
- expression-based mapping itself
- conditional transformation rules
- quarantine workflow orchestration beyond a simple reject output
- replay/retry semantics for archived or rejected files
- rule severity levels
- multi-destination reject routing

## Proposed first proof scenario

### New preserved scenario bundle

Recommended first bundle:

- `src/main/resources/config-scenarios/csv-validation-reject-archive/`

### Why a new bundle

A new bundle keeps the proving slice isolated from:

- `customer-load`
- `csv-to-sqlserver`
- `relational-to-relational`

That makes it easier to review the first hardening behavior without mixing it into older scenarios.

### Suggested files in the bundle

- `job-config.yaml`
- `source-config.yaml`
- `target-config.yaml`
- `processor-config.yaml`
- input sample file under a scenario-local or test-local path
- expected output / reject output references for tests where practical

### Suggested sample rows

The first sample should include at least:

- one valid row
- one row with a missing required field
- one row with an invalid time field
- one additional valid row

### Expected proof outcomes

The scenario should prove:

- valid rows are written to the accepted target
- invalid rows are written to reject output
- reject metadata is present
- the original input file is archived after successful completion
- runtime evidence shows accepted, rejected, and written counts clearly

## Likely production code touch points

## 1. Config binding and validation

### `src/main/java/com/etl/config/source/CsvSourceConfig.java`
Expected work:

- add file-source archive config object or fields
- validate archive config shape where needed
- preserve backward compatibility for current CSV scenarios

### `src/main/java/com/etl/config/processor/ProcessorConfig.java`
Expected work:

- add processor-level rejected-record output config
- add per-field rule config under `FieldMapping`
- keep current mapping-only scenarios valid without requiring new fields

### `src/main/java/com/etl/config/ConfigLoader.java`
Expected work:

- validate the new processor config fields cleanly
- validate archive config for file-based sources
- keep current config-loading error messages explicit and operator-friendly

## 2. Runtime orchestration and lifecycle behavior

### `src/main/java/com/etl/config/BatchConfig.java`
Expected work:

- thread accepted vs rejected behavior through the step execution path
- trigger archive-on-success after successful step completion
- keep explicit `steps` orchestration unchanged
- avoid changing the chunk/tasklet decision model unnecessarily

### `src/main/java/com/etl/job/listener/StepLoggingContextListener.java`
Expected work:

- expand step-finished evidence to include accepted/rejected or archive-result details where justified
- keep current machine-readable step event style stable

### `src/main/java/com/etl/job/listener/JobCompletionNotificationListener.java`
Expected work:

- optionally roll up reject/archive evidence into run-summary output if needed for the first slice

## 3. Mapping, validation, and reject behavior

### `src/main/java/com/etl/processor/impl/DefaultDynamicProcessor.java`
Expected work:

- resolve the selected mapping as today
- incorporate validation-aware processing for mapped fields
- avoid mixing broad expression behavior into this first slice

### `src/main/java/com/etl/mapping/DynamicMapping.java`
Expected work:

- likely evolve from direct field copy into validation-aware mapping support
- decide whether rule evaluation belongs here or in a dedicated helper invoked by the processor

### New helper classes likely needed

Recommended new family rather than overloading existing classes too early:

- field rule config model
- field rule evaluator / validator helper
- reject record model or reject writer helper
- archive helper for file lifecycle handling

## 4. Target / artifact writing

### `src/main/java/com/etl/writer/impl/CsvDynamicWriter.java`
Expected work:

- support or reuse CSV writing for reject output artifacts
- keep accepted target writing distinct from reject output writing

### `src/main/java/com/etl/writer/DynamicWriterFactory.java`
Expected work:

- decide whether reject output uses the normal writer path, a specialized helper, or a small dedicated reject writer

## Test checklist

## 1. Config binding tests

### `src/test/java/com/etl/config/source/SourceConfigPolymorphicDeserializationTest.java`
Add coverage for:

- CSV source archive config binding
- backward compatibility when archive config is absent

### `src/test/java/com/etl/config/ConfigLoaderJobConfigTest.java`
Add coverage for:

- new processor validation rule config binding
- rejected-record output config validation
- archive config validation for file-based sources
- clear failure messages for malformed rule definitions

## 2. Orchestration and runtime selection tests

### `src/test/java/com/etl/config/BatchConfigStepOrchestrationTest.java`
Add coverage for:

- explicit-step runs still select mappings by source/target names
- archive behavior does not change step ordering semantics
- first validation/reject slice does not reintroduce positional assumptions

### `src/test/java/com/etl/config/ScenarioConfigReferenceTest.java`
Add coverage for:

- new preserved scenario bundle references valid config files

## 3. Processor and mapping tests

### New tests recommended

- `DefaultDynamicProcessorValidationTest`
- `DynamicMappingValidationTest`

Add coverage for:

- `notNull` rule success and failure
- `timeFormat` rule success and failure
- valid row continues to accepted target path
- invalid row is marked for reject output with reason metadata

## 4. Writer / artifact tests

### `src/test/java/com/etl/writer/DynamicWriterFactoryTest.java`
Add coverage for:

- reject output writer resolution if it enters the writer factory path

### New tests recommended

- `CsvRejectWriterTest`
- `ArchiveOnSuccessBehaviorTest`

Add coverage for:

- reject output columns include reason metadata
- accepted rows are not written to reject output
- source file moves only after successful step completion
- source file remains in place after technical failure

## 5. End-to-end scenario test

### New integration test recommended

- `CsvValidationRejectArchiveFlowTest`

Expected assertions:

- accepted output contains only valid rows
- reject output contains only invalid rows
- reject metadata columns exist
- archive path contains the original source file after success
- runtime counts are consistent with the sample file contents

## Suggested implementation order

1. config model additions in source and processor config classes
2. config-loading validation in `ConfigLoader`
3. validation-aware mapping / processor behavior
4. reject output writing
5. archive-on-success lifecycle behavior
6. step/run evidence expansion
7. preserved scenario creation
8. end-to-end test and docs refresh

## Review checkpoints before merging code

Before implementation is considered ready, confirm:

- current shipped scenarios still load unchanged
- current docs/config pages remain accurate after code changes
- one preserved realistic scenario demonstrates the full accepted/rejected/archived flow
- expression-based mapping has not accidentally been pulled into this first slice
- reject and archive semantics are visible in tests and runtime evidence

## Related notes

- [`file-ingestion-hardening.md`](file-ingestion-hardening.md)
- [`runtime-flow.md`](runtime-flow.md)
- [`transformation-capability-roadmap.md`](transformation-capability-roadmap.md)
- [`../product/product-backlog.md`](../product/product-backlog.md)

