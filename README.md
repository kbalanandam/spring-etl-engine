<img src="docs/assets/github-social-preview-tagline.svg" alt="OneFlow social preview" width="1100" />

# Spring ETL Engine

> One runtime for repeatable business flows.

`spring-etl-engine` is the stable technical identity for the repository and codebase, while **OneFlow** is the GitHub-facing product name for a focused, config-driven runtime that helps teams execute repeatable file-based and integration-oriented flows. It is not positioned as a traditional ETL suite in the style of Informatica or SSIS. The current runtime is built with **Spring Batch** and focuses on explicit step-based execution, validation-aware processing, rejected-record output, and extensible transformation flows.

## Naming convention

- `spring-etl-engine` remains the stable technical identity for the repository, codebase, package structure, and technical references.
- `OneFlow` is the current brand-facing product name for user-facing messaging, visual assets, and GitHub presentation.
- Future branding changes should update brand-facing copy first and avoid unnecessary renames of technical identifiers unless there is a strong operational reason.

## Product vision

The near-term goal is to become the default internal runtime for repeatable file-based integration scenarios so teams stop rewriting the same ETL concerns in custom code for every business scenario.

In product terms, OneFlow gives teams one focused runtime for orchestrating, validating, and delivering those scenarios without rebuilding file-flow and ETL plumbing for each project.

That means standardizing common concerns such as:

- source and target file handling
- reusable validation policies and optional duplicate-handling rules
- explicit scenario orchestration through configuration
- consistent reject, archive, and operational evidence behavior

From that starting point, the product can continue maturing carefully over time without being marketed today as a broad traditional ETL suite. The current focus remains practical delivery, repeatability, and reduced repetitive engineering effort.

## Highlights

- **Explicit orchestration** — one selected `job-config.yaml` defines the source/target/processor set and the exact `steps` execution order for a run.
- **Validation-aware processing** — the active runtime supports source validation, processor-side field rules, explicit rejected-record output, and archive-on-success for CSV file scenarios.
- **Transform-aware processing** — optional processor-side `transforms[]` chains now support cleanup/normalization plus expression-derived fields before validation rules, while source-transform YAML stays reserved for source-native cases.
- **Format flexibility** — current runtime paths support CSV, XML, and phase-1 relational sources plus CSV, JSON, XML, and phase-1 relational targets.
- **Config-driven extensibility** — dynamic readers, processors, writers, and validation SPIs keep new behavior on the active product path instead of hardcoded one-off flows.
- **Operational visibility** — machine-readable run, subflow, and step logging provides scenario-aware execution evidence for operators and verification workflows, including blocked-downstream explanations when an upstream subflow fails.

## Supported flows

- CSV → XML
- CSV → relational target
- XML → CSV
- XML → JSON
- relational → relational
- multi-step scenario execution through explicit `steps`

## Why this exists

Many enterprise file-based integration flows are still implemented as one-off code. That usually leads to repeated logic for file intake, validation, optional duplicate handling, reject output, archive behavior, and delivery steps such as SFTP push/pull.

`spring-etl-engine` exists to standardize those repeated concerns in one configurable runtime so teams can spend less time rebuilding ETL plumbing and more time delivering business scenarios.

## Who this is for

This product is for teams that repeatedly build and maintain file-based integration jobs and want one consistent runtime for:

- explicit scenario orchestration
- reusable validation policies and optional duplicate rules
- standard reject and archive behavior
- repeatable file in / file out execution patterns
- operator-visible runtime evidence

## What OneFlow is not

OneFlow is not currently positioned as:

- a full traditional ETL suite like Informatica or SSIS
- a broad self-service enterprise integration platform
- a finished managed file transfer, OCR, or workflow product

It is a focused runtime for repeatable, config-driven integration scenarios that teams would otherwise implement repeatedly in custom code.

## What it standardizes

The near-term product focus is to make these recurring concerns consistent across business scenarios:

- source and target file handling
- config-driven scenario execution through `job-config.yaml`
- file/source validation and record-level rule evaluation
- optional duplicate handling plus rejected-record output
- processed-file archive behavior
- machine-readable run and step evidence

## Current status

### Shipped today

- explicit `job-config.yaml`-driven scenario selection with ordered `steps`
- CSV, XML, and phase-1 relational source paths plus CSV, JSON, XML, and phase-1 relational target paths
- source validation plus processor-side validation rules
- processor-side `valueMap` cleanup and `expression`-based derived fields through the active `transforms[]` contract
- optional job-scoped generated package derivation when source or target configs omit `packageName`
- rejected-record output and archive-on-success for the current CSV-focused hardening slice
- machine-readable run and step evidence for operators and verification
- descriptor-backed main-flow/subflow planning evidence plus blocked-subflow summaries layered on top of the current flat ordered-step runtime

### Deprecated path

- `src/main/java/com/etl/validation/` and `src/main/resources/validation-config.yaml` are deprecated and are not part of the active runtime path

### Future direction

- broader processor-side transformation maturity beyond the shipped `valueMap` + `expression` transform baseline
- richer fault tolerance, reconciliation, restartability, scheduling, and transport capabilities
- deeper relational hardening and enterprise verification/reporting maturity

## Start here

Use this table as the recommended reading order by goal:

| Goal | Start here | Then go to |
|---|---|---|
| First local run | [Quick Start](#quick-start) | [Run Modes](#run-modes) |
| Run a real scenario | [Explicit job-config mode](#explicit-job-config-mode) | [`docs/config/job-config.md`](docs/config/job-config.md) |
| Understand the config model | [`docs/config/README.md`](docs/config/README.md) | [`docs/config/processor/default-processor.md`](docs/config/processor/default-processor.md) |
| Explore architecture/runtime flow | [Architecture Docs](#architecture-docs) | [`docs/architecture/runtime-flow.md`](docs/architecture/runtime-flow.md) |
| Understand the next architecture target | [`docs/architecture/scenario-driven-runtime-direction.md`](docs/architecture/scenario-driven-runtime-direction.md) | [`docs/architecture/1-4-to-next-architecture-classification.md`](docs/architecture/1-4-to-next-architecture-classification.md) |
| Assess current gaps to the reusable scenario model | [`docs/architecture/runtime-to-scenario-gap-assessment.md`](docs/architecture/runtime-to-scenario-gap-assessment.md) | [`docs/architecture/hierarchical-flow-composition.md`](docs/architecture/hierarchical-flow-composition.md) |
| See preserved runnable examples | `src/main/resources/config-jobs/` | [`docs/config/README.md#scenario-examples`](docs/config/README.md#scenario-examples) |
| Set up a developer-local private job bundle or collection | [`private-jobs/`](private-jobs/README.md) | [Explicit job-config mode](#explicit-job-config-mode) |
| Understand what is shipped vs planned | [`docs/README.md`](docs/README.md) | [`docs/product/product-backlog.md`](docs/product/product-backlog.md) |

## Documentation strategy

Use the repository docs in this order:

1. **`README.md`** — product landing page, run modes, and first navigation choices
2. **`docs/README.md`** — docs portal, core terms, and architecture/product navigation
3. **`docs/config/README.md`** — current supported config contracts and scenario-reading order
4. **`src/main/resources/config-jobs/`** — preserved runnable example bundles checked in with the repository
5. **[`private-jobs/`](private-jobs/README.md)** — repo-root developer-local placeholder area for private job bundles; copy preserved examples into it as needed, keep only the guidance file committed, and keep all real bundles git-ignored

Documentation intent is split deliberately:

- `README.md` explains what the product is and where to start
- `docs/config/` describes supported contracts **today**
- `docs/architecture/` explains runtime design, extension seams, and future direction
- `docs/product/` explains backlog, milestones, and execution priorities

## Architecture Docs

Architecture and design notes now live in-repo under [`docs/`](docs/README.md).

Start here:

- [`docs/README.md`](docs/README.md)
- [`docs/architecture/overview.md`](docs/architecture/overview.md)
- [`docs/architecture/scenario-driven-runtime-direction.md`](docs/architecture/scenario-driven-runtime-direction.md)
- [`docs/architecture/runtime-to-scenario-gap-assessment.md`](docs/architecture/runtime-to-scenario-gap-assessment.md)
- [`docs/architecture/1-4-to-next-architecture-classification.md`](docs/architecture/1-4-to-next-architecture-classification.md)
- [`docs/architecture/runtime-flow.md`](docs/architecture/runtime-flow.md)
- [`docs/architecture/extension-points.md`](docs/architecture/extension-points.md)
- [`docs/architecture/transformation-capability-roadmap.md`](docs/architecture/transformation-capability-roadmap.md)
- [`docs/adr/0007-add-separate-processor-transform-spi-for-cleaning-and-normalization.md`](docs/adr/0007-add-separate-processor-transform-spi-for-cleaning-and-normalization.md)
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
isActive: true
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

This is the recommended product direction for both checked-in reference bundles and private deployable bundles.

Use the bundle root that matches your need:

- `src/main/resources/config-jobs/` for preserved safe examples, docs-aligned proofs, and smoke/reference scenarios
- `private-jobs/` for developer-local private job collections derived from preserved examples, real input paths, customer-specific values, and pre-production or production-ready test bundles that must not be committed; private bundles now standardize their runnable YAML under a `config/` subfolder

Preserved business-scenario examples include:

- `customer-load`
- `department-load`
- `cust-dept-load`
- `csv-to-sqlserver`
- `relational-to-relational`

Run with:

```powershell
Set-Location '<repo-root>'
mvn --no-transfer-progress -DskipTests "-Dspring-boot.run.jvmArguments=-Detl.config.job=src/main/resources/config-jobs/csv-to-sqlserver/job-config.yaml" spring-boot:run
```

Private deployable bundle example:

```powershell
Set-Location '<repo-root>'
mvn --no-transfer-progress -DskipTests "-Dspring-boot.run.jvmArguments=-Detl.config.job=private-jobs/partner-orders/config/job-config.yaml" spring-boot:run
```

Grouped private collection example:

```powershell
Set-Location '<repo-root>'
mvn --no-transfer-progress -DskipTests "-Dspring-boot.run.jvmArguments=-Detl.config.job=private-jobs/acme-prod/partner-orders-daily/config/job-config.yaml" spring-boot:run
```

`config-jobs` remains the canonical checked-in preserved-bundle path. `private-jobs` is the preferred developer-local git-ignored area for private bundles that contributors create from those preserved examples, and nothing there except the guidance file should be committed to GitHub. Legacy `config-scenarios/...` path support remains available temporarily for backward compatibility, but it is now deprecated.

In this mode, the app does **not** auto-discover other scenarios or sibling config sets. It only loads the three files referenced by the job config and executes the explicit `steps` in the order declared.

If `etl.config.job` is missing, unreadable, malformed, declares `isActive: false`, omits `steps`, or references missing source/target/processor artifacts, startup fails fast.

For explicit `etl.config.job` runs, startup validates the selected source, target, and processor config before step execution begins. Processor config validation now runs before generated-model class checks, so malformed processor mappings or rule settings surface as scenario-aware configuration errors instead of being masked by unrelated generated-model failures.

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

- source/config validation through the active config model under `src/main/java/com/etl/config/`, dispatched by `src/main/java/com/etl/config/source/validation/SourceValidationService.java`
- record validation through `processor-config.yaml` field rules, dispatched by `src/main/java/com/etl/processor/validation/ValidationRuleEvaluator.java` and `src/main/java/com/etl/processor/validation/ProcessorValidationRule.java`

The legacy `src/main/java/com/etl/validation/` package and `src/main/resources/validation-config.yaml` are deprecated and are not part of the active ETL runtime path.

## Planned transform path note

The next planned transform contract is processor-first:

- generic cleanup/normalization such as value mapping, null fallback, trim, or case normalization should live in future processor-side field `transforms[]`
- `transforms[]` is intended to stay optional by omission and ordered for zero/one/many transform steps
- processor rules still decide accept/reject after transforms run, so transform-then-reject is valid
- future source-transform YAML is reserved for source-native adaptation such as XPath-, namespace-, header-, token-, or other pre-flattening concerns

See:

- [`docs/config/processor/default-processor.md`](docs/config/processor/default-processor.md)
- [`docs/architecture/transformation-capability-roadmap.md`](docs/architecture/transformation-capability-roadmap.md)
- [`docs/adr/0007-add-separate-processor-transform-spi-for-cleaning-and-normalization.md`](docs/adr/0007-add-separate-processor-transform-spi-for-cleaning-and-normalization.md)

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

