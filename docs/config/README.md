# Configuration Reference

This section documents the configuration contracts supported by `spring-etl-engine` today.

The goal is to keep the baseline resource YAML files stable while providing:

- a field-by-field reference for each config type
- scenario-specific example config sets
- a place to record current support level and phase-1 limitations

## How to use these docs

Use these docs in two ways:

1. as a reference for what each config type supports today
2. as a guide when creating scenario-specific YAML files under `src/main/resources/config-scenarios/`

## Recommended config asset strategy

Keep three layers of config assets in the repository:

### 1. Baseline defaults
These remain under `src/main/resources/`:

- `source-config.yaml`
- `target-config.yaml`
- `processor-config.yaml`

They should stay simple and readable, and serve as the default demo/reference flow.

### 2. Scenario configs
These live under `src/main/resources/config-scenarios/`.

Each folder represents one runnable business or connector scenario, for example:

- `csv-to-sqlserver`
- `customer-load`
- `department-load`
- `cust-dept-load`
- future `sqlserver-to-csv`
- future `sqlserver-to-sqlserver`

This avoids rewriting the baseline YAML files every time a new connector combination or business flow is tested.

Each scenario folder should be treated as a self-contained config bundle for one ETL run. The scenario folder is not executed automatically. One run should explicitly choose one scenario's `job-config.yaml`.

### 3. Config reference docs
These live under `docs/config/` and explain what each config type supports today.

## Current support matrix

| Category | Type | Status | Notes |
|---|---|---|---|
| Source | CSV | Supported | Stable baseline source |
| Source | XML | Supported | Existing runtime path |
| Source | Relational | Supported (phase 1) | Table/query reads with current field name == column name assumption |
| Target | CSV | Supported | Existing runtime path |
| Target | XML | Supported | Existing runtime path |
| Target | Relational | Supported (phase 1) | Insert-only target path with current field name == column name assumption |
| Processor | Default | Supported | Field-to-field mapping |

## Docs in this section

### Source
- [`source/csv-source.md`](source/csv-source.md)
- [`source/relational-source.md`](source/relational-source.md)

### Target
- [`target/relational-target.md`](target/relational-target.md)

### Processor
- [`processor/default-processor.md`](processor/default-processor.md)

## Scenario examples

### Available now
- `src/main/resources/config-scenarios/csv-to-sqlserver/`
- `src/main/resources/config-scenarios/relational-to-relational/`
- `src/main/resources/config-scenarios/customer-load/`
- `src/main/resources/config-scenarios/department-load/`
- `src/main/resources/config-scenarios/cust-dept-load/`

Those scenarios demonstrate:
- existing CSV source
- default processor mapping
- relational SQL Server target
- direct relational source to relational target flow

The business scenarios demonstrate:
- explicit `job-config.yaml` driven selection
- single-entity scenarios such as `customer-load` and `department-load`
- a multi-entity scenario such as `cust-dept-load` where one selected config set drives multiple ETL steps in one run

For relational scenarios, prefer preserving large-volume settings directly in the scenario bundle:

- `countQuery` when source counting should stay explicit
- `fetchSize` for source-side streaming hints
- `batchSize` for target-side write grouping guidance

## Runtime override pattern

Scenario configs are intended to be selected through runtime property overrides instead of replacing the default resource files.

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

Example job config:

```yaml
name: csv-to-sqlserver
sourceConfigPath: source-config.yaml
targetConfigPath: target-config.yaml
processorConfigPath: processor-config.yaml
```

Relative paths in `job-config.yaml` are resolved from the job-config file's folder.

The engine should not auto-discover all scenario folders and execute them. One run should explicitly select one scenario/config set through `etl.config.job`.

`JobConfig.name` is currently descriptive metadata for the selected scenario. It is not yet used as an independent runtime lookup key.

If `etl.config.job` is not set, startup should fail unless `etl.config.allow-demo-fallback=true` is enabled. Demo fallback mode may then use the direct config path properties and, if those direct files are missing, continue into bundled classpath YAML intended for local/demo usage.

## Documentation rule

Whenever a new source, target, or processor type is added or its field contract changes:

- update the relevant config reference doc
- add or update at least one scenario config folder if the change introduces a new combination worth preserving


