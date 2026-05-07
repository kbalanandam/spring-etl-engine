# File Ingestion Hardening Checklist

## Purpose

This checklist now acts as a **current-state review aid** for the shipped file-ingestion hardening architecture in `spring-etl-engine`.

Use it to answer three questions before extending this area further:

1. what is already shipped on the active runtime path
2. which classes and config contracts now anchor that behavior
3. which preserved tests and scenarios should still be used as proof after new changes

This page still preserves the history of the original first implementation slice, but it is no longer a pre-coding task list. For the broader architecture note, see [`file-ingestion-hardening.md`](file-ingestion-hardening.md).

## Current shipped scope

### Shipped baseline today

- processor-level field validation rules on the active default-processor path
- rejected-record output with reason metadata through `processor-config.yaml`
- duplicate handling for keep-first and ordered winner-selection behavior
- processed-source-file archiving after successful step completion
- step-finished evidence with `rejectedCount`, `rejectOutputPath`, and `archivedSourcePath`
- scenario-local path normalization for selected source, target, processor, reject, and archive paths

### Shared file-backed source boundary

The current runtime treats archive-on-success as a **shared file-backed source concern**.

- file-backed sources such as CSV and XML participate through the shared file-source contract
- relational sources do not participate in archive-on-success because they are not file-backed sources
- archive behavior is configured in source config, not in processor or job config

### Historical first proof

The original first preserved proof remains intentionally CSV-centered:

- `src/main/resources/config-scenarios/csv-validation-reject-archive/`

That bundle is still the clearest first-slice proof for accepted rows, rejected rows, reject metadata, and archive-on-success in one scenario.

## Explicitly deferred or out of scope

The following items are still not part of the shipped hardening contract:

- relational-source archive handling
- replay/retry orchestration for archived or rejected files
- multi-destination reject routing
- rule severity levels
- broad conditional rule engines
- XML-native duplicate identity rules based on XPath/namespaces or other pre-flattening structure
- richer source-native XML validation beyond the current lightweight structural/file-level baseline

## Current architecture anchors

### Config contract anchors

- `src/main/java/com/etl/config/source/FileSourceConfig.java` — shared contract for file-backed sources that expose `filePath` and archive behavior
- `src/main/java/com/etl/config/source/FileArchiveConfig.java` — shared archive-on-success config object
- `src/main/java/com/etl/config/source/CsvSourceConfig.java` — CSV file-backed source contract
- `src/main/java/com/etl/config/source/XmlSourceConfig.java` — XML file-backed source contract
- `src/main/java/com/etl/config/processor/ProcessorConfig.java` — processor rule and reject-handling config
- `src/main/java/com/etl/config/ConfigLoader.java` — validation and scenario-relative path normalization for source, target, reject, and archive paths

### Runtime lifecycle anchors

- `src/main/java/com/etl/runtime/FileIngestionRuntimeSupport.java` — step-scoped reject handling, duplicate tracking, and archive-on-success lifecycle support
- `src/main/java/com/etl/job/listener/FileIngestionHardeningStepListener.java` — initializes and completes the hardening runtime state around each step
- `src/main/java/com/etl/job/listener/StepLoggingContextListener.java` — publishes `rejectedCount`, `rejectOutputPath`, and `archivedSourcePath` in machine-readable step-finished logs
- `src/main/java/com/etl/runtime/scenario/ScenarioRuntimeDescriptorAssembler.java` — exposes archive/reject-related execution hints into descriptor metadata

## Preserved proof scenarios

Use these preserved bundles when reviewing or extending the hardening slice:

- `src/main/resources/config-scenarios/csv-validation-reject-archive/`
  - first preserved proof for processor rules, rejected-record output, and archive-on-success
- `src/main/resources/config-scenarios/xml-nested-to-csv-tag-validation/`
  - preserved proof that the shared processor rule and reject-handling contract also applies on a file-backed XML flow after XML flattening
- `src/main/resources/config-scenarios/xml-nested-to-csv-to-nested-xml-archive-e2e/`
  - preserved proof that XML sources now participate in archive-on-success and emit `archivedSourcePath` evidence after step completion

## Verification anchors

### Focused test evidence

- `src/test/java/com/etl/runtime/FileIngestionRuntimeSupportTest.java`
  - verifies archive-on-success behavior and execution-context evidence for file-backed sources
- `src/test/java/com/etl/config/source/SourceConfigPolymorphicDeserializationTest.java`
  - verifies source-config binding for shared archive config on CSV and XML sources
- `src/test/java/com/etl/config/ConfigLoaderJobConfigTest.java`
  - verifies scenario-relative path normalization and fail-fast config validation for reject/archive concerns

### Runtime evidence to preserve

After changes in this area, preserved proofs should still demonstrate:

- accepted rows are written only to the intended target artifact
- rejected rows are written only to the reject artifact when reject handling is enabled
- original source files move only after successful step completion when archive is enabled
- `STEP_EVENT event=step_finished` continues to emit `rejectedCount`, `rejectOutputPath`, and `archivedSourcePath` consistently
- relative scenario-local paths still resolve from the selected scenario bundle cleanly

## Review checklist for future changes

Before considering additional hardening changes complete, confirm:

- file-backed source behavior still stays behind the shared `FileSourceConfig` seam instead of reintroducing CSV-only or XML-only archive logic
- relational-source configs are still excluded naturally from archive-on-success behavior
- current preserved scenarios still load unchanged unless a deliberate config-contract change is being made
- docs under `docs/config/` and `docs/architecture/` still describe archive behavior as a shared file-backed source concern
- step and run evidence remain machine-readable and operator-visible
- new behavior is proven by both focused tests and at least one preserved realistic scenario

## Related notes

- [`file-ingestion-hardening.md`](file-ingestion-hardening.md)
- [`runtime-flow.md`](runtime-flow.md)
- [`validation-extension-architecture.md`](validation-extension-architecture.md)
- [`transformation-capability-roadmap.md`](transformation-capability-roadmap.md)
- [`../product/product-backlog.md`](../product/product-backlog.md)

