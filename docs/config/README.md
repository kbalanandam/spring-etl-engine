# Configuration Reference

This section documents the configuration contracts supported by `spring-etl-engine` today.

It should be read together with the preserved scenario YAML bundles under `src/main/resources/config-jobs/`. Those checked-in executable examples should stay aligned with the field references in `docs/config/`.

For private environment-specific runs, copy a preserved bundle into the developer-local git-ignored [`private-jobs/`](../../private-jobs/README.md) area at the repository root. Prefer grouped private collections such as `private-jobs/<collection>/<job-bundle>/` so one collection can be purged cleanly later.

The goal is to keep the baseline resource YAML files stable while providing:

- a field-by-field reference for each config type
- scenario-specific example config sets
- a place to record current support level and phase-1 limitations

Legacy `validation-config.yaml`-style validation under `src/main/java/com/etl/validation/` is deprecated and is not part of the active runtime contract. The supported validation path is the active source/processor config model documented in this section.

## How to use these docs

Use these docs in two ways:

1. as a reference for what each config type supports today
2. as a guide when creating scenario-specific YAML files under `src/main/resources/config-jobs/` or developer-local private bundles under `private-jobs/`

## YAML example maintenance contract

Treat the YAML examples in this section as part of the active config contract, not as decorative snippets.

Each config page under `docs/config/` should preserve the same documentation shape:

1. show one runnable or directly-derived YAML example from a preserved bundle under `src/main/resources/config-jobs/`
2. explain the example fields in the same top-to-bottom order they appear in the YAML
3. call out which blocks are optional, conditional, or omitted from the example
4. link back to the preserved scenario bundle that should be updated with the same change

When a config field is added, removed, renamed, or its meaning changes:

- update the matching `docs/config/*` page in the same change
- refresh the example YAML on that page so it still mirrors a real preserved bundle
- update at least one preserved scenario bundle under `src/main/resources/config-jobs/` when the runtime contract changed
- prefer adding short field-by-field walkthrough bullets immediately below the YAML example instead of relying only on a reference table

This keeps the docs readable for new authors while still keeping them executable-reference friendly for maintainers.

## Status legend

- **Supported** — part of the active runtime contract today
- **Supported (phase 1)** — shipped today with explicit current limitations
- **Future** — preserved in architecture notes, not part of the active config contract yet
- **Deprecated** — retained temporarily for cleanup or migration and not part of the active runtime contract

## Recommended reading order

Use this reading order when authoring or reviewing one scenario:

1. [`Job config reference`](job-config.md) — start here; this is the primary entry point for one selected run, including the shipped optional `isActive` startup guardrail on `job-config.yaml`
2. one source reference such as [`CSV source reference`](source/csv-source.md), [`XML source reference`](source/xml-source.md), or [`Relational source reference`](source/relational-source.md)
3. one target reference such as [`CSV target reference`](target/csv-target.md), [`JSON target reference`](target/json-target.md), [`XML target reference`](target/xml-target.md), or [`Relational target reference`](target/relational-target.md)
4. [`Default processor reference`](processor/default-processor.md) — define field mappings, transforms, and rules
5. [Scenario examples](#scenario-examples) — compare with a preserved runnable bundle closest to your use case

For future-only config proposals that are not shipped yet, stop here and continue in `docs/architecture/` rather than treating them as active config contracts. In particular, proposals for new runtime entry points, registries, UI selectors, or richer step models should still align with the scenario-driven runtime contract in [`Scenario-driven runtime direction`](../architecture/scenario-driven-runtime-direction.md) instead of bypassing `job-config.yaml`.

## Recommended config asset strategy

Keep three layers of config assets in the repository:

### 1. Baseline defaults
These remain under `src/main/resources/`:

- `source-config.yaml`
- `target-config.yaml`
- `processor-config.yaml`

They should stay simple and readable, and serve as the default demo/reference flow.

### 2. Preserved reference jobs
These live under `src/main/resources/config-jobs/`.

Each folder represents one runnable business or connector scenario, for example:

- `csv-to-sqlserver`
- `customer-load`
- `customer-load-zipped`
- `department-load`
- `cust-dept-load`
- future `sqlserver-to-csv`
- future `sqlserver-to-sqlserver`

This avoids rewriting the baseline YAML files every time a new connector combination or business flow is tested.

Each preserved job folder should be treated as a self-contained config bundle for one ETL run. The folder is not executed automatically. One run should explicitly choose one bundle's `job-config.yaml`.

These preserved job bundles are the closest thing to living reference YAMLs in the repository, so when a field contract changes the matching bundle and the matching `docs/config/*` page should be updated together.

### 3. Private deployable jobs
These should live under a git-ignored repo-root folder such as `private-jobs/`.

Treat that folder as a developer-local placeholder workspace, not as a second committed example root.

- start from a preserved reference bundle under `src/main/resources/config-jobs/`
- copy it into `private-jobs/<collection>/<job-bundle>/`
- replace sample values with local, customer, partner, or environment-specific settings
- do not commit those copied bundles, private data files, or generated outputs to GitHub

Use that area for:

- real customer or partner job bundles
- environment-specific JDBC and filesystem paths
- local production-like testing bundles
- private input, output, reject, or archive paths that must not be published

Preferred structure:

```text
private-jobs/
  <collection>/
    <job-bundle>/
      config/
        job-config.yaml
        source-config.yaml
        target-config.yaml
        processor-config.yaml
```

Use the collection layer for the purge boundary you care about, for example one partner, one project, or one environment-specific workspace. Private bundles now standardize their runnable YAML under `config/`, while sibling folders such as `input/`, `output/`, `definitions/`, and `sql/` stay at the job-bundle root.

`ConfigLoader` already resolves `etl.config.job` from an explicit filesystem path, so these private bundles do not need to live under `src/main/resources/`.

### 4. Config reference docs
These live under `docs/config/` and explain what each config type supports today.

Forward-looking config proposals for not-yet-shipped behavior should stay in `docs/architecture/` design notes until the runtime contract is actually implemented.

## Current support matrix

| Category | Type | Status | Notes |
|---|---|---|---|
| Source | CSV | Supported | Stable baseline source |
| Source | XML | Supported | Existing runtime path |
| Source | Relational | Supported (phase 1) | Table/query reads with current field name == column name assumption |
| Target | CSV | Supported | Existing runtime path |
| Target | JSON | Supported | Flat staged JSON array output |
| Target | XML | Supported | Existing runtime path |
| Target | Relational | Supported (phase 1) | Insert-only target path with current field name == column name assumption |
| Processor | Default | Supported | Field-to-field mapping plus first-slice CSV validation and rejected-record output |

## Docs in this section

### Source
- [`source/csv-source.md`](source/csv-source.md)
- [`source/xml-source.md`](source/xml-source.md)
- [`source/relational-source.md`](source/relational-source.md)

### Target
- [`target/csv-target.md`](target/csv-target.md)
- [`target/json-target.md`](target/json-target.md)
- [`target/xml-target.md`](target/xml-target.md)
- [`target/relational-target.md`](target/relational-target.md)

### Processor
- [`processor/default-processor.md`](processor/default-processor.md)

### Job selection
- [`job-config.md`](job-config.md)

## Scenario examples

### Available now

| Scenario bundle | Primary flow | Notes |
|---|---|---|
| `src/main/resources/config-jobs/csv-validation-reject-archive/` | CSV -> CSV | Preferred entry path for the preserved first shipped proof for CSV field validation rules, rejected-record output, and archive-on-success behavior |
| `src/main/resources/config-jobs/csv-to-nested-xml/` | CSV -> nested XML | Preferred entry path for the preserved explicit job bundle proving flat CSV fields can map into nested XML target paths through a generated target model definition |
| `src/main/resources/config-jobs/csv-to-sqlserver/` | CSV -> relational SQL Server target | Preferred entry path for the preserved placeholder SQL Server scenario |
| `src/main/resources/config-jobs/relational-to-relational/` | relational source -> relational target | Preferred entry path for the preserved larger-volume relational testing bundle |
| `src/main/resources/config-jobs/xml-to-csv-events/` | XML -> CSV | Preferred entry path for the preserved realistic flat XML baseline |
| `src/main/resources/config-jobs/xml-to-json-events/` | XML -> JSON | Preferred entry path for the preserved production-style flat XML to JSON baseline |
| `src/main/resources/config-jobs/xml-nested-to-csv-tag-validation/` | nested XML -> CSV | Preferred entry path for the preserved nested XML flattening example |
| `src/main/resources/config-jobs/xml-nested-tag-validation/` | nested XML -> XML | Preferred entry path for the preserved nested XML to XML example |
| `src/main/resources/config-jobs/xml-nested-to-csv-to-nested-xml/` | nested XML -> CSV -> nested XML | Preferred entry path for the preserved explicit multi-step roundtrip bundle |
| `src/main/resources/config-jobs/xml-nested-to-csv-to-nested-xml-archive-e2e/` | nested XML -> CSV -> nested XML | Preferred entry path for the preserved multi-step roundtrip bundle with XML archive-on-success |
| `src/main/resources/config-jobs/customer-load/` | CSV -> XML | Preferred entry path for the single-step customer-load example |
| `src/main/resources/config-jobs/customer-load-reject-quarantine/` | CSV -> XML + reject quarantine | Preferred entry path for preserved reject-quarantine proof (`rejectHandling.quarantinePath`) with duplicate rejection |
| `src/main/resources/config-jobs/customer-load-zipped/` | ZIP-backed CSV -> XML | Preferred entry path for the first preserved unzip-before-read proof on the shared file-source contract, using the minimal `filePath: ...zip` convention and keeping the original ZIP as the reject/archive identity |
| `src/main/resources/config-jobs/department-load/` | CSV -> XML | Preferred entry path for the single-step department-load example |
| `src/main/resources/config-jobs/cust-dept-load/` | CSV -> XML + XML | Preferred entry path for the multi-step customer + department example |

Those scenarios together demonstrate:
- first shipped CSV field validation / reject / archive behavior
- additive reject quarantine publication through `rejectHandling.quarantinePath`
- CSV source mapping into nested XML target structure through `modelDefinitionPath`
- existing CSV source
- ZIP-backed file-source preparation inferred from `filePath: ...zip` before normal CSV/XML validation and reading, with default extracted staging under a runtime-owned JVM temp work root instead of beside the input artifact
- optional zip-on-archive packaging for plain file-backed CSV/XML sources through `archive.packageAsZip`, reusing the same shared ZIP utility boundary as unzip-before-read
- optional zip-on-reject packaging through `processor-config.yaml -> rejectHandling.packageAsZip`
- optional zip-on-successful-output packaging through file target `packageAsZip` on CSV, JSON, and XML targets
- existing XML source
- default processor mapping
- CSV target output
- JSON target output
- XML target output
- relational SQL Server target
- direct relational source to relational target flow
- flat XML source to CSV target flow
- flat XML source to JSON target flow
- nested XML source to CSV target flow through the shared flattening path
- nested XML source to XML target flow through the next-direction XML generation and flattening path
- one selected multi-step scenario that hands nested XML -> CSV -> nested XML through an intermediate file inside the same job
- explicit `job-config.yaml` driven selection
- single-entity scenarios such as `customer-load`, `customer-load-zipped`, and `department-load`
- a multi-entity scenario such as `cust-dept-load` where one selected config set drives multiple ETL steps in one run

For preserved local examples, keep visible runtime artifacts such as final outputs, rejects, archives, and intermediate handoff files under each scenario bundle's `output/` folder when practical. This keeps `input/`, `output/`, and config files together for quick inspection while production runs can still override paths explicitly. Those local `output/` folders are intentionally ignored from Git and excluded from packaged application resources.

For relational scenarios, prefer preserving large-volume settings directly in the scenario bundle:

- `countQuery` when source counting should stay explicit
- `fetchSize` for source-side streaming hints
- `batchSize` for target-side write grouping guidance

## Runtime override pattern

Job bundles are intended to be selected through runtime property overrides instead of replacing the default resource files.

Typical properties are:

- `etl.config.job`
- `etl.config.allow-demo-fallback`
- `etl.config.source`
- `etl.config.target`
- `etl.config.processor`

Recommended precedence:

1. use `etl.config.job` as the primary business-scenario selector for one run
2. require `etl.config.job` by default for normal runtime execution
3. use `etl.config.source`, `etl.config.target`, and `etl.config.processor` only when demo fallback is explicitly enabled for local/manual runs

Treat `etl.config.job` and its selected `job-config.yaml` as the normal reader entry point for the config model. The direct `etl.config.source`, `etl.config.target`, and `etl.config.processor` overrides remain secondary and are mainly for controlled demo fallback or low-level local runs.

Recommended bundle locations:

- `src/main/resources/config-jobs/...` for checked-in preserved reference jobs
- [`private-jobs/...`](../../private-jobs/README.md) for developer-local git-ignored private jobs copied from preserved examples

Example job config:

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

Relative paths in `job-config.yaml` are resolved from the job-config file's folder, and explicit job-config runs now require a non-empty `steps` list.

Source and target runtime YAML no longer support authored `packageName`. On explicit job-config runs, the runtime and job-scoped generation path derive `com.etl.generated.job.<normalized-job-name>.source` and `com.etl.generated.job.<normalized-job-name>.target` from the selected non-blank `job-config.yaml -> name`. In direct-config/demo fallback mode, the runtime still applies internal compatibility defaults `com.etl.model.source|target` so the preserved demo classes keep working. In both modes, authored `packageName` now fails fast instead of remaining part of the supported config contract.

Legacy development-time model generation paths from `model.paths.*` remain anchored to the repository root rather than the selected scenario working directory. This keeps explicit job runs from creating scenario-local `src/main/java` or `target/classes` trees when older dev-profile generators are still active.

For the full job-config field reference, including multi-step examples such as `cust-dept-load`, see [`job-config.md`](job-config.md).

The engine should not auto-discover all scenario folders and execute them. One run should explicitly select one scenario/config set through `etl.config.job`.

The canonical checked-in preserved bundle path is `config-jobs`, and the runtime still accepts legacy `config-scenarios/...` references temporarily for backward compatibility, but that alias path is now deprecated. Developer-local private bundles should now prefer `private-jobs/...` instead of adding real data or environment-specific settings under `src/main/resources/`, and those private bundles should remain uncommitted.

`JobConfig.name` is currently the selected scenario/job identity used in logs and metadata. It is still not a separate lookup registry key, but it now also seeds the generated package path for the active package-free explicit-job contract, and that non-blank job name is part of the active naming guardrail.

If `etl.config.job` is not set, startup should fail unless `etl.config.allow-demo-fallback=true` is enabled. Demo fallback mode may then use the direct config path properties and, if those direct files are missing, continue into bundled classpath YAML intended for local/demo usage.

For selected relational source or target configs, startup now also validates that committed template values such as `<SQLSERVER_HOST>` have been replaced with real environment-specific settings before runtime. This prevents preserved example scenarios from failing late during JDBC connection setup.

This means the preserved SQL Server scenario bundles are safe to keep in the repository as templates, while still failing clearly if they are run without real environment-specific overrides.

## Documentation rule

Whenever a new source, target, or processor type is added or its field contract changes:

- update the relevant config reference doc
- add or update at least one scenario config folder if the change introduces a new combination worth preserving
- keep the preserved YAML example and the matching field reference synchronized so the docs remain executable-reference friendly

For the broader file-ingestion hardening direction beyond the current CSV slice, see [`File ingestion hardening`](../architecture/file-ingestion-hardening.md). It now captures the shipped first slice plus the remaining deferred expansions.

