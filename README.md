# Spring ETL Engine

A lightweight, configurable, and modular ETL (Extract–Transform–Load) framework built using **Spring Boot**, designed for handling multiple source formats and multiple target destinations dynamically. The engine supports CSV/XML ingestion, dynamic field mappings, reflection-based transformations, type conversion utilities, profile‑based configuration, and structured logging.

## Features
- **Dynamic Mapping Framework**: Map any source structure to any target using configs.
- **Reusable Utilities**: Centralized `*Utils` for type conversion, reflection, validation.
- **Custom Exceptions**: Domain‑specific exceptions to identify issues clearly.
- **AOP Logging**: Automatic method-level logging for ETL flow visibility.
- **Scenario/job-run logging**: MDC-backed logs carry scenario, run, job, and step context, with daily scenario log files and machine-readable run/step event messages for operations.
- **Builder Pattern**: Clean and safe object construction.
- **Profile-based Execution**: Separate dev, test, prod behaviour.
- **Multi‑Source Input**: CSV, XML, and phase-1 relational sources.
- **Multi‑Target Output**: CSV, XML, and phase-1 relational targets.
- **Scenario-driven execution**: one `job-config.yaml` can select a preserved business or connector scenario per run.

## Architecture Docs

Architecture and design notes now live in-repo under [`docs/`](docs/README.md).

Start here:

- [`docs/README.md`](docs/README.md)
- [`docs/architecture/overview.md`](docs/architecture/overview.md)
- [`docs/architecture/runtime-flow.md`](docs/architecture/runtime-flow.md)
- [`docs/architecture/extension-points.md`](docs/architecture/extension-points.md)
- [`docs/README.md#adrs`](docs/README.md#adrs)

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
 │    ├── validation/      # Deprecated legacy validation framework (kept temporarily for cleanup/migration)
 │    ├── writer/          # Target writers
 │    └── ETLEngineApplication.java
 ├── src/main/resources
 │    ├── application.properties
 │    ├── application-dev.properties
 │    ├── source-config.yaml
 │    ├── target-config.yaml
 │    ├── processor-config.yaml
 │    ├── validation-config.yaml  # Deprecated legacy validation resource
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

1. **Use the repo-provided config** under `src/main/resources/` or point to your own external config files.
2. **Use one explicit job config** if you want a single file to choose the source/target/processor YAMLs for the run.
3. **Use the bundled fallback config** if you want to try the project immediately from the repository.

If you want the fastest first run without preparing an external scenario bundle, go directly to **Demo fallback mode** below and enable the explicit fallback flag.

## Run Modes

The application supports two runtime policies:

1. **Explicit job-config mode** - the default and enterprise-grade mode; one selected business-scenario/job config file points to one source/target/processor config set
2. **Demo fallback mode** - optional local/demo mode; enabled only when `etl.config.allow-demo-fallback=true`

`application.properties` is strict by default. If `etl.config.job` is not set, startup fails with a configuration error unless demo fallback is explicitly enabled. When `etl.config.job` is set, that selected job config takes precedence and selects the exact source/target/processor config files to load for one ETL run. An explicitly provided `etl.config.job` must be valid and never silently falls back.

### Explicit job-config mode

Use this mode when you want one file to declare exactly which business scenario or config trio a job should run.

This is the default startup contract.

Example `job-config.yaml`:

```yaml
name: csv-to-sqlserver
sourceConfigPath: source-config.yaml
targetConfigPath: target-config.yaml
processorConfigPath: processor-config.yaml
steps:
  - name: customers-to-sql-step
    source: Customers
    target: CustomersSql
```

The referenced paths may be absolute or relative. Relative paths are resolved from the `job-config.yaml` folder.

`job-config.yaml` now also defines the explicit ETL execution order through `steps`. Positional source-target pairing is no longer a supported orchestration contract for explicit job-config runs.

This is the recommended product direction for preserved business scenarios such as:

- `customer-load`
- `department-load`
- `cust-dept-load`
- `csv-to-sqlserver`
- `relational-to-relational`

Run with:

```powershell
Set-Location '<repo-root>'
mvn --no-transfer-progress -DskipTests "-Dspring-boot.run.jvmArguments=-Detl.config.job=src/main/resources/config-scenarios/csv-to-sqlserver/job-config.yaml" spring-boot:run
```

In this mode, the app does **not** auto-discover other scenarios or sibling config sets. It only loads the three files referenced by the job config and executes the explicit `steps` in the order declared.

If `etl.config.job` is missing, unreadable, malformed, omits `steps`, or references missing source/target/processor artifacts, startup fails fast.

For relational scenarios selected through `etl.config.job`, startup also validates the chosen source/target connection settings early. Placeholder values such as `<SQLSERVER_HOST>` are rejected before batch execution begins so operators see a scenario-aware configuration error instead of a late JDBC connection failure.

## Logging Layout

The runtime now emits correlation-friendly logs with these MDC fields when available:

- `scenario`
- `runCorrelationId`
- `jobExecutionId`
- `stepName`

`src/main/resources/logback-spring.xml` writes:

1. a normal console stream with MDC context visible in each line
2. a daily scenario file under `etl.logging.base-dir`

Current file layout:

- startup logs before a run is selected: `logs/startup/startup.log`
- explicit job-config runs: `logs/<yyyy-MM-dd>/<scenario>.log`
- demo fallback runs: `logs/<yyyy-MM-dd>/demo-fallback.log`

Example:

```text
logs/
  2026-04-23/
    csv-to-sqlserver.log
    demo-fallback.log
  startup/
    startup.log
```

You can override the base directory with:

```properties
etl.logging.base-dir=target/test-logs
```

The scenario name is resolved from `JobConfig.name` when `etl.config.job` is used. If that field is blank, the runtime falls back to the selected `job-config.yaml` folder name. Each log line still keeps the `runCorrelationId`, so multiple same-day runs for one scenario remain distinguishable inside the shared daily file.

For relational large-volume scenarios, the current phase-1 tuning knobs are:

- `countQuery` for predictable chunk/tasklet decisions on relational sources
- `fetchSize` as the JDBC streaming hint for relational reads
- `batchSize` as the recommended write grouping value to align with chunk-based relational loads

### Demo fallback mode

Enable this mode only for local/demo runs. It is not intended for production execution.

When `etl.config.allow-demo-fallback=true` and `etl.config.job` is not set, the runtime uses these direct config paths first:

- `src/main/resources/source-config.yaml`
- `src/main/resources/target-config.yaml`
- `src/main/resources/processor-config.yaml`

Current bundled demo sample paths:

- input: `src/main/resources/demo-input/Customers.csv`
- input: `src/main/resources/demo-input/Department.csv`
- output: `target/`

Run with:

```powershell
Set-Location '<repo-root>'
mvn --no-transfer-progress -DskipTests "-Dspring-boot.run.jvmArguments=-Detl.config.allow-demo-fallback=true" spring-boot:run
```

Expected output files:

- `target/customers.xml`
- `target/departments.xml`

If the direct YAML files are missing, demo fallback continues into the bundled resources:

- `src/main/resources/source-config.yaml`
- `src/main/resources/target-config.yaml`
- `src/main/resources/processor-config.yaml`

Bundled sample input files:

- `src/main/resources/demo-input/Customers.csv`
- `src/main/resources/demo-input/Department.csv`

Bundled fallback output location:

- `target/`

You can force bundled fallback inside demo mode by overriding the direct config paths to missing files:

```powershell
Set-Location '<repo-root>'
mvn --no-transfer-progress -DskipTests "-Dspring-boot.run.jvmArguments=-Detl.config.allow-demo-fallback=true -Detl.config.source=Z:/missing/source-config.yaml -Detl.config.target=Z:/missing/target-config.yaml -Detl.config.processor=Z:/missing/processor-config.yaml" spring-boot:run
```

Expected fallback output files:

- `target/customers.xml`
- `target/departments.xml`

## Usage
1. Define your source configuration in `source-config.yaml`.
2. Define your target configuration in `target-config.yaml`.
3. Define field mappings in `processor-config.yaml`.
4. Prefer running the application with an explicit `etl.config.job` scenario selection.
5. Use `etl.config.allow-demo-fallback=true` only for local/demo fallback runs.
6. Review the generated output files in the configured target directory.

## Validation path note

The supported validation path is now:

- source/config validation through the active config model under `src/main/java/com/etl/config/`
- record validation through `processor-config.yaml` field rules and `src/main/java/com/etl/processor/validation/ValidationRuleEvaluator.java`

The legacy `src/main/java/com/etl/validation/` package and `src/main/resources/validation-config.yaml` are deprecated and are not part of the active ETL runtime path.

## Verification after code changes

For a repeatable local verification workflow after code changes, run:

```powershell
Set-Location 'C:\spring-etl-engine'
powershell.exe -ExecutionPolicy Bypass -File '.\scripts\generate-verification-report.ps1'
```

This produces:

- `target/verification-report.md`
- `target/verification-report-<yyyyMMdd-HHmmss>.md`

The stable `target/verification-report.md` file is always the latest result.
The timestamped report keeps a historical snapshot for that specific verification run.
By default, the report generator keeps only a small recent set of timestamped reports to avoid unbounded growth under `target/`.

The report combines:

- a top-line `STATUS: READY` or `STATUS: NOT READY` banner
- an `At a glance` section with overall readiness, smoke status, pass rate, and the slowest suite/testcase
- quick navigation links to major report sections
- explicit phase-1 verification categories for:
  - change-focused verification
  - regression suite verification
  - runtime and smoke verification
  - release readiness
- a shared verification evidence model inside the report generator so future Markdown/HTML renderers can use the same collected evidence
- current Git branch and changed-file summary
- compact Git status counts before the full changed-file list
- full `mvn test` summary
- suite-by-suite results from `target/surefire-reports/`
- slowest-suite and slowest-testcase highlights for quick performance/regression scanning
- testcase-by-testcase results grouped under each suite, including per-test duration and status
- a focused non-passing test section for failures, errors, or skipped cases when present
- a short PASS/FAIL interpretation section near the top
- smoke verification status using:
  - successful `customer-load`
  - expected fail-fast `csv-to-sqlserver` placeholder validation

Helpful generated artifacts:

- `target/verification-mvn-test.log`
- `target/verification-smoke.log`
- `target/verify-customer-load.log`
- `target/verify-csv-to-sqlserver.log`
- `target/customers.xml`

Optional parameters:

- `-SkipSmoke` — generate the report from automated tests only
- `-KeepLatestCount <N>` — control how many timestamped report snapshots are retained in `target/`

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

