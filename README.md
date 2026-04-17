# Spring ETL Engine

A lightweight, configurable, and modular ETL (Extract–Transform–Load) framework built using **Spring Boot**, designed for handling multiple source formats and multiple target destinations dynamically. The engine supports CSV/XML ingestion, dynamic field mappings, reflection-based transformations, type conversion utilities, profile‑based configuration, and structured logging.

## Features
- **Dynamic Mapping Framework**: Map any source structure to any target using configs.
- **Reusable Utilities**: Centralized `*Utils` for type conversion, reflection, validation.
- **Custom Exceptions**: Domain‑specific exceptions to identify issues clearly.
- **AOP Logging**: Automatic method-level logging for ETL flow visibility.
- **Builder Pattern**: Clean and safe object construction.
- **Profile-based Execution**: Separate dev, test, prod behaviour.
- **Multi‑Source Input**: Currently CSV/XML; extendable.
- **Multi‑Target Output**: Extendable (e.g., MySQL Writer, File Writer, API Writer).

## Repository Structure
```
/spring-etl-engine
 ├── src/main/java/com/etl
 │    ├── aspect/          # AOP logging
 │    ├── common/          # Shared utilities and common exceptions
 │    ├── config/          # Batch, YAML, and model path configuration
 │    ├── enums/           # Shared enums such as model formats
 │    ├── exception/       # Core ETL exceptions
 │    ├── job/             # Job listeners and batch job support
 │    ├── mapping/         # Dynamic mapping engine
 │    ├── model/           # Generated models and model generators
 │    ├── processor/       # Dynamic processors
 │    ├── reader/          # Source readers
 │    ├── runner/          # Application/job startup runner
 │    ├── validation/      # Validation rules and validators
 │    ├── writer/          # Target writers
 │    └── ETLEngineApplication.java
 ├── src/main/resources
 │    ├── application.properties
 │    ├── application-dev.properties
 │    ├── source-config.yaml
 │    ├── target-config.yaml
 │    ├── processor-config.yaml
 │    ├── validation-config.yaml
 │    └── demo-input/      # Bundled fallback sample CSV files
 ├── src/test
 │    ├── java/            # Unit and integration tests
 │    └── resources/       # Test datasets
 ├── README.md
 ├── pom.xml
 └── LICENSE
```

## Installation
Clone the repository:
```powershell
git clone https://github.com/kbalanandam/spring-etl-engine.git
```

## Quick Start

Prerequisites:

- Java 17
- Maven 3.x

Choose one of the following ways to run the project:

1. **Use your external ETL config** if you already have files under `C:/ETLDemo/config`.
2. **Use the bundled fallback config** if you want to try the project immediately from the repository.

If you want the fastest first run, go directly to **Classpath fallback mode** below.

## Run Modes

The application supports two run modes:

1. **External config mode** - uses YAML files from `C:/ETLDemo/config`
2. **Classpath fallback mode** - uses bundled YAML files from `src/main/resources` when the external files are missing

`application.properties` currently defaults to the external config locations, and `ConfigLoader` automatically falls back to the bundled classpath files if those external YAML files are not found.

### External config mode

This is the default mode when these files exist:

- `C:/ETLDemo/config/source-config.yaml`
- `C:/ETLDemo/config/target-config.yaml`
- `C:/ETLDemo/config/processor-config.yaml`

Current external sample paths:

- input: `C:/ETLDemo/data/input/Customers.csv`
- input: `C:/ETLDemo/data/input/Department.csv`
- output: `C:/ETLDemo/data/output/`

Run with:

```powershell
Set-Location 'C:\spring-etl-engine'
mvn --no-transfer-progress -DskipTests spring-boot:run
```

Expected output files:

- `C:/ETLDemo/data/output/customers.xml`
- `C:/ETLDemo/data/output/departments.xml`

### Classpath fallback mode

If the external YAML files are missing, the app falls back to the bundled resources:

- `src/main/resources/source-config.yaml`
- `src/main/resources/target-config.yaml`
- `src/main/resources/processor-config.yaml`

Bundled sample input files:

- `src/main/resources/demo-input/Customers.csv`
- `src/main/resources/demo-input/Department.csv`

Bundled fallback output location:

- `target/`

You can force fallback mode by overriding the config paths to missing files:

```powershell
Set-Location 'C:\spring-etl-engine'
mvn --no-transfer-progress -DskipTests "-Dspring-boot.run.jvmArguments=-Detl.config.source=Z:/missing/source-config.yaml -Detl.config.target=Z:/missing/target-config.yaml -Detl.config.processor=Z:/missing/processor-config.yaml" spring-boot:run
```

Expected fallback output files:

- `target/customers.xml`
- `target/departments.xml`

## Usage
1. Define your source configuration in `source-config.yaml`.
2. Define your target configuration in `target-config.yaml`.
3. Define field mappings in `processor-config.yaml`.
4. Run the application in external-config mode or fallback mode.
5. Review the generated output files in the configured target directory.

## Example Mapping
```yaml
source: customer.csv
fields:
  - source: id
    target: customerId
  - source: first_name
    target: firstName
```

## License
This project is licensed under the **MIT License**.

![License](https://img.shields.io/badge/License-MIT-green.svg)

See the full license in the `LICENSE` file.

