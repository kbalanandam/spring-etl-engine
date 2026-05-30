<img src="docs/assets/github-social-preview-tagline.svg" alt="OneFlow social preview" width="1100" />

# Spring ETL Engine

> One runtime for repeatable business flows.

`spring-etl-engine` is the stable technical identity for the repository and codebase, while **OneFlow** is the GitHub-facing product name for a focused, config-driven runtime that helps teams execute repeatable file-based and integration-oriented flows. It is not positioned as a traditional ETL suite in the style of Informatica or SSIS. The current runtime is built with **Spring Batch** and focuses on explicit step-based execution, validation-aware processing, rejected-record output, and extensible transformation flows.

## Naming convention

- `spring-etl-engine` remains the stable technical identity for the repository, codebase, package structure, and technical references.
- `OneFlow` is the current brand-facing product name for user-facing messaging, visual assets, and GitHub presentation.
- Future branding changes should update brand-facing copy first and avoid unnecessary renames of technical identifiers unless there is a strong operational reason.
- Future broader product renames or brand-wording refresh work should follow [`docs/product/github-promotion.md`](docs/product/github-promotion.md) and the tracked backlog path in [`E3 - Centralize product-brand naming and doc refresh automation`](docs/product/backlog-items/etl-core/E3-centralize-brand-naming-and-doc-refresh.md) rather than a blind repository-wide replace.

## Product vision

The near-term goal is to become the default internal runtime for repeatable file-based integration scenarios so teams stop rewriting the same ETL concerns in custom code for every business scenario.

In product terms, OneFlow gives teams one focused runtime for orchestrating, validating, and delivering those scenarios without rebuilding file-flow and ETL plumbing for each project.

That runtime should remain independently runnable through explicit `job-config.yaml` selection even as future scheduler/control-plane and operator-UI capabilities are added around it. The intended growth path is an optional operational layer for scheduling, file watching, monitoring, and persisted run history - not a replacement for the core ETL execution contract.

That also means future built-in scheduling must stay optional for adopters. Teams that prefer an external enterprise scheduler, orchestrator, or platform trigger should be able to launch the same selected-job runtime contract without adopting a OneFlow-native scheduler first.

That means standardizing common concerns such as:

- source and target file handling
- reusable validation policies and optional duplicate-handling rules
- explicit scenario orchestration through configuration
- consistent reject, archive, and operational evidence behavior

From that starting point, the product can continue maturing carefully over time without being marketed today as a broad traditional ETL suite. The current focus remains practical delivery, repeatability, and reduced repetitive engineering effort.

## Highlights

- **Explicit orchestration** - one selected `job-config.yaml` defines the source/target/processor set and the exact `steps` execution order for a run.
- **Validation-aware processing** - the active runtime supports source validation, processor-side field rules, explicit rejected-record output, and archive-on-success for CSV file scenarios.
- **Transform-aware processing** - optional processor-side `transforms[]` chains now support cleanup/normalization plus expression-derived fields before validation rules, while source-transform YAML stays reserved for source-native cases.
- **Format flexibility** - current runtime paths support CSV, XML, and phase-1 relational sources plus CSV, JSON, XML, and phase-1 relational targets.
- **Config-driven extensibility** - dynamic readers, processors, writers, and validation SPIs keep new behavior on the active product path instead of hardcoded one-off flows.
- **Operational visibility** - machine-readable run, subflow, and step logging provides scenario-aware execution evidence for operators and verification workflows, including blocked-downstream explanations when an upstream subflow fails.

## Supported flows

- CSV -> XML
- CSV -> relational target
- XML -> CSV
- XML -> JSON
- relational -> relational
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
- explicit-job package derivation from `job-config.yaml -> name`, with source/target `packageName` omitted from selected bundles and explicit selected-job runs failing fast if authored `packageName` is present
- rejected-record output and archive-on-success for the current CSV-focused hardening slice
- machine-readable run and step evidence for operators and verification
- descriptor-backed main-flow/subflow planning evidence plus blocked-subflow summaries layered on top of the current flat ordered-step runtime

### Deprecated path

- `src/main/java/com/etl/validation/` and `src/main/resources/validation-config.yaml` are deprecated and are not part of the active runtime path

### Future direction

- broader processor-side transformation maturity beyond the shipped `valueMap` + `expression` transform baseline
- an optional Java-first control plane for scheduling, file watching, trigger governance, persisted evidence, and a future integrated UI over the ETL core
- first-class interoperability with external schedulers/orchestrators that trigger the same explicit selected-job runtime when teams do not want the built-in scheduler layer
- local-first persisted OneFlow operational data, with lightweight relational storage such as SQLite acceptable for early developer/laptop control-plane work before stronger relational deployments are introduced
- richer fault tolerance, reconciliation, restartability, scheduling, and transport capabilities
- deeper relational hardening and enterprise verification/reporting maturity

## Optional control-plane API starter (monitoring-first)

An optional monitoring-first control-plane API starter is now available as a separate launcher:

- `com.etl.controlplane.ControlPlaneApiApplication`

It intentionally runs as a separate process from the ETL worker so the selected-job runtime contract stays unchanged.

Example local run:

```powershell
mvn -f "C:\spring-etl-engine\pom.xml" --no-transfer-progress "-Dspring-boot.run.mainClass=com.etl.controlplane.ControlPlaneApiApplication" "-Dspring-boot.run.profiles=controlplane" spring-boot:run
```

First monitoring endpoints:

- `GET /api/v1/jobs` - lists preserved job bundles with readiness projection metadata
- `GET /api/v1/jobs/{jobKey}` - returns an aggregated job-detail payload with `job`, `recentRuns`, and `recentTriggerEvents`
- `POST /api/v1/jobs/{jobKey}:trigger-now` - records an accepted placeholder trigger decision and returns a `triggerEventId`
- `GET /api/v1/jobs/{jobKey}/trigger-events` - lists recent trigger events for one job bundle
- `GET /api/v1/runs` - lists recent `RUN_SUMMARY` log projections
- `GET /api/v1/runs/{jobExecutionId}` - returns one projected run summary by job execution id
- `GET /api/v1/runs/{jobExecutionId}/detail` - returns a richer run drill-down with step outcomes, artifacts, failure summary, and evidence links
- `GET /api/v1/system/health` - returns minimal control-plane health status
- `GET /api/v1/system/info` - returns service name, Java version, and active profile

## Run Modes

### Explicit job-config mode (preferred)

For local development, run one selected bundle directly through Maven:

```powershell
mvn --no-transfer-progress -DskipTests "-Dspring-boot.run.jvmArguments=-Detl.config.job=src/main/resources/config-jobs/customer-load/job-config.yaml" spring-boot:run
```

### Packaged jar mode (selected-job contract)

Use this path when validating deployment-style execution against one preserved or private bundle.

```powershell
mvn --no-transfer-progress -DskipTests "-Dstart-class=com.etl.ETLEngineApplication" package
$jar = Get-ChildItem -Path .\target -Filter "spring-etl-engine-*.jar" | Where-Object { $_.Name -notlike "*sources*" -and $_.Name -notlike "*javadoc*" } | Sort-Object LastWriteTime -Descending | Select-Object -First 1 -ExpandProperty FullName
java "-Detl.config.job=src/main/resources/config-jobs/customer-load/job-config.yaml" -jar $jar
```

Notes:

- Keep `etl.config.job` explicit. Strict startup without it is intentional unless `-Detl.config.allow-demo-fallback=true` is set.
- Relative paths inside `job-config.yaml` resolve from the job bundle folder.
- For private deployable bundles, point `etl.config.job` to `private-jobs/<collection>/<job-bundle>/config/job-config.yaml`.
- For XML scenarios that require generated job-scoped classes (for example `xml-nested-to-csv-to-nested-xml`), run with `-Pxml-generation` first.

## Start here

Use this table as the recommended reading order by goal:

| Goal | Start here | Then go to |
|---|---|---|
| First local run | Quick Start | Run Modes |
| Run a real scenario | Explicit job-config mode | [`docs/config/job-config.md`](docs/config/job-config.md) |
| Understand the config model | [`docs/config/README.md`](docs/config/README.md) | [`docs/config/processor/default-processor.md`](docs/config/processor/default-processor.md) |
| Explore architecture/runtime flow | [Architecture Docs](#architecture-docs) | [`docs/architecture/README.md`](docs/architecture/README.md) and [`docs/architecture/etl-core/README.md`](docs/architecture/etl-core/README.md) |
| Understand the next architecture target | [`docs/architecture/etl-core/scenario-driven-runtime-direction.md`](docs/architecture/etl-core/scenario-driven-runtime-direction.md) | [`docs/architecture/control-plane/README.md`](docs/architecture/control-plane/README.md) and [`docs/architecture/etl-core/1-4-to-next-architecture-classification.md`](docs/architecture/etl-core/1-4-to-next-architecture-classification.md) |
| Assess current gaps to the reusable scenario model | [`docs/architecture/etl-core/runtime-to-scenario-gap-assessment.md`](docs/architecture/etl-core/runtime-to-scenario-gap-assessment.md) | [`docs/architecture/etl-core/hierarchical-flow-composition.md`](docs/architecture/etl-core/hierarchical-flow-composition.md) |
| See preserved runnable examples | `src/main/resources/config-jobs/` | [`docs/config/README.md#scenario-examples`](docs/config/README.md#scenario-examples) |
| Set up a developer-local private job bundle or collection | [`private-jobs/`](private-jobs/README.md) | Explicit job-config mode |
| Understand what is shipped vs planned | [`docs/README.md`](docs/README.md) | [`docs/product/product-backlog.md`](docs/product/product-backlog.md) |

Active execution tracking for the OneFlow product-facing roadmap lives in the GitHub Project **[OneFlow Executive Dashboard](https://github.com/users/kbalanandam/projects/3/views/1)**. The execution-board table in [`docs/product/product-backlog.md`](docs/product/product-backlog.md) can act as the source of truth for that Project through the backlog-to-project sync described in [`docs/product/project-board-sync.md`](docs/product/project-board-sync.md).

## Documentation strategy

Use the repository docs in this order:

1. **`README.md`** - product landing page, run modes, and first navigation choices
2. **`docs/README.md`** - docs portal, core terms, and architecture/product navigation
3. **`docs/architecture/README.md`** - topic-grouped index for architecture notes and future-direction design docs
4. **`docs/config/README.md`** - current supported config contracts and scenario-reading order
5. **`src/main/resources/config-jobs/`** - preserved runnable example bundles checked in with the repository
6. **[`private-jobs/`](private-jobs/README.md)** - repo-root developer-local placeholder area for private job bundles; copy preserved examples into it as needed, keep only the guidance file committed, and keep all real bundles git-ignored

Documentation intent is split deliberately:

- `README.md` explains what the product is and where to start
- `docs/config/` describes supported contracts **today**
- `docs/architecture/` explains runtime design, extension seams, and future direction
- `docs/product/` explains backlog, milestones, and execution priorities

### Docs website (HTML from Markdown)

This repository includes a lightweight MkDocs setup so docs under `docs/` can be rendered as a browsable HTML site.

```powershell
Set-Location "C:\spring-etl-engine"
python -m pip install --user -r .\docs-requirements.txt
python -m mkdocs serve
```

For a build-only output (no local server):

```powershell
Set-Location "C:\spring-etl-engine"
python -m mkdocs build
```

Generated site output is written to `target/docs-site/`.

## Architecture Docs

Architecture and design notes now live in-repo under [`docs/`](docs/README.md).

Start here:

- [`docs/README.md`](docs/README.md)
- [`docs/architecture/README.md`](docs/architecture/README.md)
- [`docs/architecture/foundations/README.md`](docs/architecture/foundations/README.md)
- [`docs/architecture/foundations/overview.md`](docs/architecture/foundations/overview.md)
- [`docs/architecture/etl-core/README.md`](docs/architecture/etl-core/README.md)
- [`docs/architecture/etl-core/scenario-driven-runtime-direction.md`](docs/architecture/etl-core/scenario-driven-runtime-direction.md)
- [`docs/architecture/control-plane/README.md`](docs/architecture/control-plane/README.md)
- [`docs/architecture/control-plane/control-plane-worker-boundary.md`](docs/architecture/control-plane/control-plane-worker-boundary.md)
- [`docs/architecture/operator-ui/README.md`](docs/architecture/operator-ui/README.md)
- [`docs/architecture/etl-core/runtime-to-scenario-gap-assessment.md`](docs/architecture/etl-core/runtime-to-scenario-gap-assessment.md)
- [`docs/architecture/etl-core/1-4-to-next-architecture-classification.md`](docs/architecture/etl-core/1-4-to-next-architecture-classification.md)
- [`docs/architecture/etl-core/runtime-flow.md`](docs/architecture/etl-core/runtime-flow.md)
- [`docs/architecture/etl-core/extension-points.md`](docs/architecture/etl-core/extension-points.md)
- [`docs/architecture/control-plane/scheduler-architecture-direction.md`](docs/architecture/control-plane/scheduler-architecture-direction.md)
- [`docs/architecture/operator-ui/operator-ui-architecture-direction.md`](docs/architecture/operator-ui/operator-ui-architecture-direction.md)
- [`docs/architecture/etl-core/transformation-capability-roadmap.md`](docs/architecture/etl-core/transformation-capability-roadmap.md)
- [`docs/adr/etl-core/0007-add-separate-processor-transform-spi-for-cleaning-and-normalization.md`](docs/adr/etl-core/0007-add-separate-processor-transform-spi-for-cleaning-and-normalization.md)
- [`docs/adr/foundations/0013-keep-spring-etl-engine-technical-identity-and-oneflow-product-name.md`](docs/adr/foundations/0013-keep-spring-etl-engine-technical-identity-and-oneflow-product-name.md)
- [`docs/README.md#adrs`](docs/README.md#adrs)

## Repository Structure
```
/spring-etl-engine
|-- src/main/java/com/etl
|   |-- aspect/          # AOP logging
|   |-- common/          # Shared utilities and common exceptions
|   |-- config/          # Batch, YAML, and model path configuration
|   |-- enums/           # Shared enums such as model formats
|   |-- exception/       # Core ETL exceptions
|   |-- job/             # Job listeners and batch job support
|   |-- mapping/         # Dynamic mapping engine
|   |-- model/           # Generated models and model generators
|   |-- processor/       # Dynamic processors
|   |-- reader/          # Source readers
|   |-- runner/          # Application/job startup runner
|   |-- validation/      # Deprecated legacy validation framework (kept temporarily for cleanup/migration)
|   |-- writer/          # Target writers
|   \-- ETLEngineApplication.java
|-- src/main/resources
|   |-- application.properties
|   |-- application-dev.properties
|   |-- source-config.yaml       # Simple baseline demo-fallback source config
|   |-- target-config.yaml       # Simple baseline demo-fallback target config
|   |-- processor-config.yaml    # Simple baseline demo-fallback processor config
|   |-- validation-config.yaml   # Deprecated legacy validation resource
|   |-- config-jobs/             # Checked-in preserved runnable job bundles
|   \-- demo-input/              # Bundled fallback sample input files
|-- src/test
|   |-- java/            # Unit and integration tests
|   \-- resources/       # Test datasets
|-- docs/                 # Product, config, architecture, and ADR documentation
|-- private-jobs/         # Git-ignored developer-local private job bundles (README only tracked)
|-- scripts/              # Verification, sync, and maintenance scripts
|-- logs/                 # Runtime log output by scenario/date
|-- README.md
|-- pom.xml
\-- LICENSE

