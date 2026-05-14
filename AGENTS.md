# AGENTS.md

## Quick orientation
- This repo’s technical identity is `spring-etl-engine`; **OneFlow** is product-facing copy only. Keep package names, class names, Maven coords, and file paths on the `spring-etl-engine` name unless you are editing marketing/docs copy (`README.md`, `docs/product/github-promotion.md`).
- Treat `README.md` + `docs/README.md` + `docs/config/README.md` as the current contract. Architecture notes in `docs/architecture/` explain why the runtime is shaped this way and must stay in sync with code changes.

## Runtime model to preserve
- The shipped runtime is **one selected scenario per run**: `etl.config.job -> job-config.yaml -> source/target/processor YAML trio -> explicit ordered steps`. Do not add scenario auto-discovery; `ConfigLoader` is intentionally strict (`src/main/java/com/etl/config/ConfigLoader.java`, `docs/config/job-config.md`).
- `BatchConfig` assembles the Spring Batch job from `job-config.yaml` `steps[]`; step order is explicit and not inferred from source/target list position (`src/main/java/com/etl/config/BatchConfig.java`).
- The product now emits a synthesized `MainFlow -> SubFlow -> Step` descriptor for observability, but execution is still a flat ordered Spring Batch plan. Keep that distinction clear (`docs/architecture/runtime-flow.md`).
- Dynamic runtime dispatch happens through factories, not spread-out conditionals: `DynamicReaderFactory`, `DynamicProcessorFactory`, `DynamicWriterFactory`.
- `GeneratedModelClassResolver` is the central contract between config, generated classes, processors, and writers. XML target processing vs write classes differ on purpose because XML writes may require wrapper/root objects.

## Extension seams and active contracts
- Add new source/target formats by extending config polymorphism (`SourceConfig`, `TargetConfig`), then registering a matching reader/writer and documenting the contract (`docs/architecture/extension-points.md`).
- Keep field cleanup in processor `transforms[]`, not validation `rules[]`. The shipped processor order is: read -> transforms -> rules -> write (`docs/config/processor/default-processor.md`).
- The active validation path is **source validation + processor rules**. Do not revive `src/main/java/com/etl/validation/` or `src/main/resources/validation-config.yaml`; both are deprecated.
- Duplicate winner selection is special: when a `duplicate` rule uses `orderBy`, `BatchConfig` intentionally overrides chunk mode to tasklet mode so final winners are resolved before writing.
- Many core classes carry `Transition status` markers like `BRIDGE` / `REUSE` / `LEGACY`. Respect them: don’t quietly turn bridge classes such as `ConfigLoader`, `BatchConfig`, or the factories into the final architecture center.

## Scenario/config conventions
- Preserve runnable scenario bundles under `src/main/resources/config-jobs/`; each folder should be a self-contained example with `job-config.yaml` plus matching source/target/processor YAMLs.
- Keep baseline defaults under `src/main/resources/` simple and demo-friendly; add new real examples under `config-jobs/` instead of constantly rewriting the baseline YAMLs (`docs/config/README.md`).
- For multi-step scenarios, follow the existing handoff pattern from `src/main/resources/config-jobs/xml-nested-to-csv-to-nested-xml/`: explicit step order in `job-config.yaml`, intermediate artifact paths declared in config, and downstream-readable formats (for example `includeHeader: true` on intermediate CSV).
- Relative paths inside `job-config.yaml` resolve from the job-config folder, not the repo root.

## Build, run, verify
- Tests/CI baseline: `mvn --batch-mode --no-transfer-progress test` (see `.github/workflows/pr-unit-tests.yml`).
- Preferred local run style is explicit job-config mode, e.g. `mvn --no-transfer-progress -DskipTests "-Dspring-boot.run.jvmArguments=-Detl.config.job=src/main/resources/config-jobs/customer-load/job-config.yaml" spring-boot:run`.
- Demo fallback exists for local-only use and must be explicitly enabled with `-Detl.config.allow-demo-fallback=true`; strict startup without `etl.config.job` is intentional (`src/main/resources/application.properties`).
- For XML scenarios that depend on generated job-scoped classes (example: `xml-nested-to-csv-to-nested-xml`), run the Maven `xml-generation` profile first, then execute the jar (`pom.xml`, that scenario’s `README.md`).
- After code changes, the project-specific verification workflow is `powershell.exe -ExecutionPolicy Bypass -File .\scripts\generate-verification-report.ps1`. It runs `mvn test`, parses Surefire XML, then runs smoke verification via `scripts/verify-recent-changes.ps1`.
- The smoke script intentionally expects `customer-load` to succeed and `csv-to-sqlserver` to fail fast on placeholder SQL Server values. A failing `csv-to-sqlserver` run can be the correct result.
- `config-scenarios/...` is now a legacy compatibility alias for older commands and tests; new docs, examples, and preserved bundles should use `config-jobs/...`.

## Debugging and evidence
- Read `logs/startup/startup.log` for startup-time `STEP_PLAN` / `STEP_READY` events; read `logs/<yyyy-MM-dd>/<scenario>.log` for `RUN_EVENT`, `MAIN_FLOW_PLAN`, `SUBFLOW_PLAN`, `STEP_EVENT`, `SUBFLOW_SUMMARY`, and `RUN_SUMMARY` (`src/main/resources/logback-spring.xml`, `docs/architecture/runtime-flow.md`).
- Scenario logs are keyed from `JobConfig.name`, and explicit `etl.config.job` runs now fail fast when that selected `job-config.yaml` name is blank. MDC fields like `scenario`, `runCorrelationId`, `jobExecutionId`, and `stepName` are part of the expected evidence model.
- Reject/archive behavior lives on the active step path via `FileIngestionRuntimeSupport` and `FileIngestionHardeningStepListener`; check step-finished evidence for `rejectedCount`, `rejectOutputPath`, and `archivedSourcePath`.

## Docs + PR rules that are enforced here
- If you change architecture-sensitive code under `src/main/java/com/etl/config/`, `reader/`, `writer/`, `processor/`, model generation, or core resource YAML/application properties, update a relevant file under `docs/architecture/` or `docs/adr/` in the same change (`.github/workflows/architecture-doc-guard.yml`).
- Use Markdown + Mermaid for architecture updates; add an ADR when the change introduces a real design decision/tradeoff (`docs/README.md`, `.github/PULL_REQUEST_TEMPLATE.md`).
- When a config contract changes, update the matching `docs/config/*` page and at least one preserved scenario bundle so the docs remain executable-reference friendly.

