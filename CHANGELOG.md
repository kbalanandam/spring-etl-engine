# Changelog
All notable changes to this project will be documented in this file.

The format is based on **Keep a Changelog**  
and this project adheres to **Semantic Versioning**.

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
