# Job Config

## Purpose

`job-config.yaml` is the primary runtime entry point for scenario-based execution through `etl.config.job`.

It chooses exactly one source config file, one target config file, and one processor config file for a run, and it defines the explicit ETL step order for that run.

This is the config contract that now anchors the preserved scenario bundles under `src/main/resources/config-scenarios/`.

## Java contract

Backed by:
- `src/main/java/com/etl/config/job/JobConfig.java`
- `src/main/java/com/etl/config/ConfigLoader.java`
- `src/main/java/com/etl/config/BatchConfig.java`

## Supported fields today

| Field | Required | Type | Description |
|---|---|---|---|
| `name` | yes | string | Descriptive scenario name for the selected config bundle |
| `sourceConfigPath` | yes | string | Relative or absolute path to the selected source config file |
| `targetConfigPath` | yes | string | Relative or absolute path to the selected target config file |
| `processorConfigPath` | yes | string | Relative or absolute path to the selected processor config file |
| `steps` | yes | list | Explicit ordered ETL steps for this run |
| `steps[].name` | yes | string | Step name used for plan/logging/runtime identity |
| `steps[].source` | yes | string | Must match a configured `sourceName` from the selected source config |
| `steps[].target` | yes | string | Must match a configured `targetName` from the selected target config |

## Single-step example

This mirrors `src/main/resources/config-scenarios/csv-to-sqlserver/job-config.yaml`.

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

## Multi-step example

This mirrors `src/main/resources/config-scenarios/cust-dept-load/job-config.yaml`.

```yaml
name: cust-dept-load
sourceConfigPath: source-config.yaml
targetConfigPath: target-config.yaml
processorConfigPath: processor-config.yaml
steps:
  - name: customers-step
    source: Customers
    target: Customers
  - name: departments-step
    source: Department
    target: Departments
```

## Runtime behavior today

- Explicit `etl.config.job` runs require a non-empty `steps` list.
- Step order is taken from `steps` and is no longer inferred by source/target list position.
- Relative `sourceConfigPath`, `targetConfigPath`, and `processorConfigPath` values are resolved from the `job-config.yaml` file's folder.
- The runtime does not scan scenario folders automatically; one run explicitly chooses one `job-config.yaml`.
- `name` is currently descriptive metadata for the selected bundle rather than a separate lookup key.

## Validation / usage notes

- Every `steps[].source` value must match a configured `sourceName` in the selected source config file.
- Every `steps[].target` value must match a configured `targetName` in the selected target config file.
- The selected processor config must contain a matching mapping for each source/target pair used by the selected steps.
- A multi-step scenario can reuse one processor config file with multiple mappings; runtime picks the mapping by `source` and `target` names, not by list position.
- Use `etl.config.job` as the normal production-style entry point. Direct `etl.config.source`, `etl.config.target`, and `etl.config.processor` overrides are intended for demo/fallback cases only.
- Archive-on-success remains part of the selected CSV source config, not `job-config.yaml`.
- Rejected-record handling and field-level validation rules remain part of the selected processor config, not `job-config.yaml`.

## Related design note

The broader file-ingestion hardening direction beyond the current CSV slice is documented in [`../architecture/file-ingestion-hardening.md`](../architecture/file-ingestion-hardening.md).

## Preserved examples

- `src/main/resources/config-scenarios/csv-to-sqlserver/job-config.yaml`
- `src/main/resources/config-scenarios/csv-validation-reject-archive/job-config.yaml`
- `src/main/resources/config-scenarios/relational-to-relational/job-config.yaml`
- `src/main/resources/config-scenarios/xml-to-csv-events/job-config.yaml`
- `src/main/resources/config-scenarios/customer-load/job-config.yaml`
- `src/main/resources/config-scenarios/department-load/job-config.yaml`
- `src/main/resources/config-scenarios/cust-dept-load/job-config.yaml`

## Related docs

- [`README.md`](README.md)
- [`processor/default-processor.md`](processor/default-processor.md)
- [`../architecture/runtime-flow.md`](../architecture/runtime-flow.md)

