# Changelog
All notable changes to this project will be documented in this file.

The format is based on **Keep a Changelog**  
and this project adheres to **Semantic Versioning**.

## [Unreleased]
### Added
- N/A
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


