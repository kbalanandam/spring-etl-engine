# Changelog
All notable changes to this project will be documented in this file.

The format is based on **Keep a Changelog**  
and this project adheres to **Semantic Versioning**.

## [Unreleased]

---

## [1.4.0] - 2026-04-28

### Added
- Added first-slice field-level validation support in the default processor for CSV-backed scenarios, starting with `notNull` and `timeFormat` rules on mapped fields.
- Added first-slice rejected-record output for validation-aware CSV runs, including reject CSV artifacts with `_rejectField`, `_rejectRule`, and `_rejectMessage` metadata.
- Added archive-on-success support for CSV source configs through `archive.enabled`, `archive.successPath`, and `archive.namePattern`.
- Added a preserved `csv-validation-reject-archive` scenario bundle plus a realistic CSV input sample to prove accepted output, rejected output, and archived-source-file behavior together.
- Added step-finished evidence for `rejectedCount`, `rejectOutputPath`, and `archivedSourcePath` in machine-readable step lifecycle logging.
- Added a preserved `xml-to-csv-events` scenario bundle plus a realistic flat XML sample so XML-to-CSV runs can be exercised through explicit `job-config.yaml` selection as a baseline scenario.
- Added a source-validation SPI on the active runtime path through `SourceValidationService` and `SourceValidator`, with the first built-in validators covering CSV archive config and relational source config validation.
- Added an opt-in CSV file-level `validation` block supporting fail-fast file existence/readability checks plus `allowEmpty` and `requireHeaderMatch` policies on CSV sources.
- Added a processor-rule SPI behind `ValidationRuleEvaluator` through `ProcessorValidationRule`, with built-in `notNull`, `timeFormat`, and first-slice `duplicate` rule handlers plus extension-oriented tests.
- Added composite-key duplicate validation for the built-in `duplicate` processor rule through optional `keyFields` configuration, while keeping duplicate handling on the active processor-rule seam.
- Added ordered duplicate winner selection for the built-in `duplicate` rule through structured `orderBy` entries with `field` and `direction`, so users can retain the best record per duplicate key while `keyFields`-only configurations still keep the first encountered record.

### Changed
- The file-ingestion hardening work is now implemented as a first CSV-focused slice instead of design-only planning, while broader expression, conditional, and richer quarantine behavior remains future work.
- The built-in `duplicate` rule now supports both single-field and composite-key matching with keep-first/reject-later semantics, using step-local in-memory tracking for the simple keep-first path and a shared ordered-duplicate abstraction for winner-selection flows.
- When ordered duplicate winner selection is configured, step execution now resolves the winning record per key before final write, forcing tasklet-style final buffering for that mapping and choosing between in-memory or embedded-DB staging so lower-priority rows can be discarded safely.
- Refreshed processor-config documentation to state explicitly that duplicate checking is optional, that no configured `duplicate` rule means no duplicate-based filtering for that mapping, and that `keyFields`-only duplicate rules remain keep-first unless `orderBy` winner selection is configured.
- Refreshed transform-architecture documentation to state that the first planned YAML transform contract is processor-side and optional by omission, that ordered `transforms[]` chains are the intended shape for zero/one/many cleaner steps, that transform-then-reject flows are valid, and that future source-transform YAML is reserved for source-native adaptation cases rather than generic business/value rewriting.

### Deprecated
- Deprecated the legacy `com.etl.validation.*` package and `src/main/resources/validation-config.yaml` resource because they are not part of the active ETL runtime path. The supported validation path now runs through active source config validation and `processor-config.yaml` field rules.

---

## [1.3.0] - 2026-04-25
### Added
- Added machine-readable ETL lifecycle logging for explicit planning and execution evidence, including `RUN_EVENT`, `RUN_SUMMARY`, `STEP_PLAN`, `STEP_READY`, and `STEP_EVENT` messages.
- Added a local verification workflow with smoke-check scripts and generated markdown reports under `target/`, including retained timestamped report snapshots for recent runs.
- Added orchestration-focused regression coverage for explicit step order, missing processor mappings, scenario config references, and relational placeholder validation.
- Added categorized verification reporting for change-focused verification, regression-suite verification, runtime/smoke verification, and release-readiness interpretation.
- Added a shared in-memory verification evidence model in the report generator as the phase-1 foundation for future multi-format reporting.
- Added backlog and ADR artifacts to track enterprise verification reporting as a first-class product capability.

### Changed
- Explicit `etl.config.job` runs now require a non-empty `steps` list in `job-config.yaml`, and runtime orchestration resolves source/target execution by configured names instead of positional pairing.
- Preserved scenario bundles such as `customer-load`, `department-load`, `cust-dept-load`, `csv-to-sqlserver`, and `relational-to-relational` now declare their ETL execution order through explicit `steps` definitions.
- Startup validation now checks selected relational source/target configs before job execution and rejects placeholder connection values such as `<SQLSERVER_HOST>` with clearer operator-facing configuration errors.
- README guidance now documents the explicit `steps` contract, machine-readable execution logs, and the repeatable verification-report workflow for local change validation.
- The verification report generator now separates evidence collection from Markdown rendering so future HTML and machine-readable outputs can build on the same evidence contract.
- Product backlog and documentation artifacts now reflect the completed 1.3.0 work more accurately, including explicit-step orchestration, fail-fast relational validation, and verification-reporting progress.

### Fixed
- Prevented preserved SQL Server scenarios with placeholder connection values from progressing into late JDBC/runtime failures by failing fast during configuration resolution.

---

## [1.2.0] - 2026-04-24
### Added
- Added `etl.config.allow-demo-fallback` as an explicit local/demo-only switch for legacy direct-path and bundled classpath fallback.
- Added scenario/job-run Logback output with MDC fields for `scenario`, `runCorrelationId`, `jobExecutionId`, and `stepName`, including daily scenario log files.
- Added `docs/product/product-backlog.md` as the execution-facing product backlog, including milestone-aligned board-style tracking from current state to enterprise-grade target.
- Added `docs/architecture/transformation-capability-roadmap.md` to make transformation maturity an explicit part of the enterprise-grade ETL product direction.

### Changed
- Configuration loading is now strict by default; startup fails fast when `etl.config.job` is not provided.
- Fallback to direct-path or bundled classpath YAML now happens only when `etl.config.allow-demo-fallback=true` is explicitly enabled.
- `etl.config.job` is now the default enterprise configuration entry point for one ETL run.
- Expanded architecture roadmap documentation for future job-history/operational observability, scenario/job-run logging strategy, and AI-assisted operations intelligence, including new dedicated design notes and refreshed documentation cross-references.
- Expanded the product roadmap and backlog to treat transformation capability as a first-class maturity track, from structural mapping toward rule-based and enterprise-grade transformation behavior.
- Runtime config selection is now cached and also exposes resolved scenario metadata for startup and logging concerns.
- Runtime log routing now writes to daily scenario files in the form `logs/<yyyy-MM-dd>/<scenario>.log` instead of one file per run.
- Default demo/runtime config paths are now repo-relative and portable instead of assuming Windows-specific `C:/ETLDemo/...` locations.

### Fixed
- N/A
---

## [1.1.6] - 2026-04-22
### Added
- Added a preserved `relational-to-relational` scenario bundle selected through `etl.config.job`.
- Added H2-backed large-volume relational flow coverage with a 20k-row source-to-target integration test.

### Changed
- Updated relational documentation to reflect phase-1 support, large-volume tuning guidance, and preserved scenario usage.
- Sanitized committed SQL Server scenario YAML to use placeholders instead of live connection values.
- Aligned reader and writer factory dispatch to use `ModelFormat` consistently instead of internal raw string keys.

### Fixed
- Normalized relational writer tests to use the relational target model class consistently.
- Hardened relational config validation so malformed source/target JDBC settings fail fast before runtime reads and writes begin.
---

## [1.1.5] - 2026-04-17
### Changed
- Introduced `ResolvedModelMetadata` to normalize runtime source, processing, and write class resolution.
- Centralized runtime model interpretation in `GeneratedModelClassResolver` and threaded resolved metadata through batch, processor, and writer setup.
- Improved XML runtime handling so tasklet mode continues to use wrapper objects while chunk mode streams XML record classes directly.
- Deprecated the unused `TypeConversionUtils.mapToJavaType` helper for future cleanup.

### Fixed
- Fixed large-file XML chunk processing so record classes marshal correctly during chunk-oriented writes.
- Updated XML record generation to include `@XmlRootElement`, allowing streamed record marshalling for XML chunk mode.
- Verified generated model sources and classes can be deleted and regenerated successfully during application startup.
- Validated ETL execution with 20k and 50k CSV inputs, including correct chunk selection and XML output structure.

---

## [1.1.4] - 2026-02-19
### Added
- Added support for dynamic compilation and class loading of generated model classes
- Enhanced XmlModelGenerator with improved validation and file generation logic
- Updated model generation to support both source and target configs for XML and relational formats
- Added helper methods for directory creation and file writing
- Improved error handling and logging around model generation processes

---

## [1.1.3] - 2025-12-29
### Fixed
- Centralized null and blank validation using `ValidationUtils`
- Improved validation consistency across CSV and XML model generators
- Prevented model generation with empty or invalid field definitions

### Improved
- Cleaner generator implementations with reduced duplication
- More robust error handling using model-specific exceptions
- Increased flexibility using `List<? extends FieldDefinition>`

---

## [1.1.2] - 2025-12-18
### Changed
- Refactored dynamic Source/Target configuration hierarchy using abstract base classes.
- Stabilized YAML polymorphic loading for CSV, XML, and DB sources.
- Improved DynamicReaderFactory and DynamicWriterFactory error handling.
- Unified field abstraction using FieldDefinition across readers, processors, and writers.
- Centralized ETL exception hierarchy for clearer error reporting.

### Fixed
- Resolved null `type` issues during dynamic reader resolution.
- Fixed JAXB marshaller configuration for dynamic XML writers.

---

## [1.1.1] - 2025-12-13
### Added
- Implemented **XmlDynamicWriter**, enabling dynamic XML generation for target models.
- Added unified **FieldDefinition** model for both source and target configurations.
- Enhanced **model generator engine** to support XML targets using dynamic field reflection.
- Added automatic JAXB-annotated model generation for XML target structures.
- Integrated XML writer into the **DynamicWriterFactory** for seamless runtime selection.
- Improved error handling and logging around XML marshaling operations.

### Changed
- Refactored internal writer architecture to support pluggable dynamic writers (CSV, XML, DB, etc.).
- Updated configuration loader to use unified `fields` property for both source and target YML definitions.

### Fixed
- Addressed inconsistencies between ColumnConfig vs. FieldDefinition naming.
- Fixed field-level mapping issues when switching target types.

---

## [1.1.0] - 2025-12-10
### Added
- Introduced **dynamic mapping engine** to support flexible source–target transformations.
- Added **TypeConversionUtils** as a centralized utility for field conversion and reflection handling.
- Added **custom exception hierarchy** (`TypeConversionException`, `ReflectionException`) for more meaningful error reporting.
- Added **AOP-based logging** for Reader, Processor, Writer, Step, and Job execution.
- Added **Spring Profiles** to toggle dynamic ETL and environment-specific configurations.
- Added support for **multiple source–target pairs** within a single ETL job.
- Added generic **DynamicReader**, **DynamicProcessor**, and **DynamicWriter** infrastructure.
- Added reusable **ReflectionHelper** and **TypeConversionUtils** for field-level mapping.

### Changed
- Refactored FieldSetMapper to use centralized TypeConversionUtils and Reflection utilities.
- Overhauled DynamicProcessor architecture to introduce MappingEngine and improve modularity.
- Refactored ETL pipeline to use **builder-pattern configuration** for dynamic steps.
- Updated BatchConfig to handle dynamic step creation per Source–Target pair.

### Fixed
- Fixed class-loading issues when resolving source/target model classes dynamically.
- Fixed mismatched writer invocation due to reflection-based method signatures.

---

## [1.0.0] - 2025-11-01
### Added
- Initial version of Dynamic ETL Engine.
- Basic CSV/XML Reader and Writer infrastructure.
- Config-driven Source, Target, and Processor definitions.
- Job, Step, Reader, Processor, Writer bootstrapping.

---


