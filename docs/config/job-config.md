# Job Config

## Purpose

`job-config.yaml` is the primary runtime entry point for scenario-based execution through `etl.config.job`.

It chooses exactly one source config file, one target config file, and one processor config file for a run, and it defines the explicit ETL step order for that run.

This is the config contract that now anchors the preserved reference bundles checked in under `src/main/resources/config-jobs/` and the developer-local private bundles you may keep under the git-ignored [`private-jobs/`](../../private-jobs/README.md) workspace.

Today this remains the shipped flat execution baseline. The frozen architecture direction may later group those ordered steps into subflows inside one selected main flow, but the runtime boundary still remains one selected scenario and one selected `job-config.yaml`.

## Java contract

Backed by:
- `src/main/java/com/etl/config/job/JobConfig.java`
- `src/main/java/com/etl/config/ConfigLoader.java`
- `src/main/java/com/etl/config/BatchConfig.java`

## Supported fields today

| Field | Required | Type | Description |
|---|---|---|---|
| `name` | yes | string | Required naming anchor for the selected config bundle. Explicit job runs use this value for logs, evidence, and derived generated-model package naming; blank values now fail fast instead of falling back to the job folder name |
| `sourceConfigPath` | yes | string | Relative or absolute path to the selected source config file |
| `targetConfigPath` | yes | string | Relative or absolute path to the selected target config file |
| `processorConfigPath` | yes | string | Relative or absolute path to the selected processor config file |
| `steps` | yes | list | Explicit ordered ETL steps for this run |
| `steps[].name` | yes | string | Step name used for plan/logging/runtime identity |
| `steps[].source` | yes | string | Must match a configured `sourceName` from the selected source config |
| `steps[].target` | yes | string | Must match a configured `targetName` from the selected target config |

## Single-step example

This mirrors `src/main/resources/config-jobs/csv-to-sqlserver/job-config.yaml`.

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

### Single-step example walkthrough

- `name` identifies the selected scenario in logs, evidence, and default generated package derivation when source or target `packageName` is omitted.
- Keep `name` non-blank for every explicit job bundle; the active generated-model contract now fails fast when it is blank.
- `sourceConfigPath` points to the source bundle for this run and is resolved relative to the `job-config.yaml` folder when written as a relative path.
- `targetConfigPath` points to the target bundle selected for this run.
- `processorConfigPath` points to the processor bundle that contains the mapping for the step below.
- `steps` is the explicit ordered execution plan.
- `steps[].name` is the operator-visible step identity used in plan and run logs.
- `steps[].source` must match one `sourceName` from the selected source config.
- `steps[].target` must match one `targetName` from the selected target config.
- Reusing the same logical name as `steps[].source` and `steps[].target` in the same step is still allowed for the current single-step compatibility pattern (for example `Customers -> Customers`). Reusing a logical name across different ordered steps is only valid when an earlier step produces that handoff artifact and a later step consumes it.

## Multi-step example

This mirrors `src/main/resources/config-jobs/cust-dept-load/job-config.yaml`.

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

### Multi-step example walkthrough

- The top four fields keep the same meaning as the single-step example; one selected job still points to one source config, one target config, and one processor config.
- The difference is the `steps` list now contains multiple ordered entries.
- Runtime executes `customers-step` first and `departments-step` second because step order is taken directly from the YAML list, not inferred from source or target list position.
- Each step must still resolve to a valid `sourceName`, `targetName`, and processor mapping pair.

## Composed-flow baseline example

For a multi-step scenario where one step's output becomes the next step's input, use `src/main/resources/config-jobs/xml-nested-to-csv-to-nested-xml/job-config.yaml` as the current baseline pattern.

That preserved bundle demonstrates that:

- one selected `job-config.yaml` can run multiple ordered steps across different formats
- the intermediate artifact path is preserved in the selected source and target config files rather than inferred at runtime
- downstream readability requirements belong to the selected format config, for example `includeHeader: true` on the intermediate CSV target so the next CSV source step can consume the file safely
- when the downstream CSV source reads that intermediate file, keep the meaning explicit in the source config with `skipHeader: true` if the upstream CSV target emitted a header row

The longer-term direction is for `MainFlow` descriptor context to carry small cross-subflow handshake metadata such as artifact references, readiness flags, checksums, or schema/version markers. The actual business payload should still move through the explicit step/subflow outputs rather than through `job-config.yaml` fields.

## Runtime behavior today

- Explicit `etl.config.job` runs require a non-empty `steps` list.
- Step order is taken from `steps` and is no longer inferred by source/target list position.
- The current flat `steps` list is the executable baseline even when future architecture docs describe a richer `main flow -> subflow -> step` hierarchy.
- Current descriptor assembly synthesizes named subflow/status metadata from the flat ordered `steps` list for observability, so startup/job logs can emit `MAIN_FLOW_PLAN`, `SUBFLOW_PLAN`, and `SUBFLOW_SUMMARY` evidence even though execution still follows the flat `steps` list.
- When an upstream step/subflow fails, downstream descriptor-derived subflows can now be logged as `BLOCKED` with explicit dependency and handoff reasons, but `job-config.yaml` still does not require explicit authored subflow blocks.
- Relative `sourceConfigPath`, `targetConfigPath`, and `processorConfigPath` values are resolved from the `job-config.yaml` file's folder.
- Checked-in reference bundles should use `config-jobs/...`. Developer-local private bundles copied from those examples should prefer [`private-jobs/...`](../../private-jobs/README.md). Legacy `config-scenarios/...` bundle paths still resolve for backward compatibility, but that alias path is now deprecated.
- The runtime does not scan scenario folders automatically; one run explicitly chooses one `job-config.yaml`.
- Explicit `etl.config.job` runs now also require a non-blank `name` so generated-model naming stays deterministic and does not fall back to the job folder name.
- `name` is the selected bundle identity shown in logs and metadata. When the selected source or target config omits `packageName`, explicit job runs derive default packages as `com.etl.generated.job.<normalized-job-name>.source` and `com.etl.generated.job.<normalized-job-name>.target`.
- If an explicit source or target config still authors a deprecated bridge package under `com.etl.generated.job...` that does not match the package derived from `name`, runtime/build-time startup now logs a warning with the selected job name, config path, logical config name, authored package, and derived package, while still honoring the authored value for compatibility.
- During explicit startup, the selected source and target configs are validated first, then the selected processor config is validated before generated-model class checks run.
- Processor-config validation failures in explicit runs are surfaced with the selected scenario name and processor-config path so operators can identify the broken scenario bundle quickly.
- Generated-model naming/package failures in explicit runs are surfaced as config errors with the selected scenario name, job-config path, and the failing `step` / `source` / `target` so support can narrow model-resolution issues quickly.

## Validation / usage notes

- Every `steps[].source` value must match a configured `sourceName` in the selected source config file.
- Every `steps[].target` value must match a configured `targetName` in the selected target config file.
- In explicit job mode, `packageName` in the selected source/target config is now optional. When omitted, the runtime and build-time generation path derive it from the selected non-blank `job-config.yaml` name using a normalized lowercase alphanumeric segment.
- Explicit authored `packageName` values that already use the deprecated `com.etl.generated.job...` bridge shape should either match that derived package or be removed. Mismatches no longer fail immediately, but they now produce a startup/generation warning so operators can clean them up before the bridge is tightened further.
- Selected logical names must still remain stable enough to avoid generated-class collisions after normalization. Different names such as `Customer Feed` and `Customer-Feed` can now fail fast if they would generate the same class in the same selected job side.
- The selected processor config must contain a matching mapping for each source/target pair used by the selected steps.
- A multi-step scenario can reuse one processor config file with multiple mappings; runtime picks the mapping by `source` and `target` names, not by list position.
- If the selected processor config is malformed, explicit startup now fails before generated-model class validation so processor issues are not masked by unrelated missing generated classes.
- Use `etl.config.job` as the normal production-style entry point whether the selected `job-config.yaml` lives under `src/main/resources/config-jobs/` or a developer-local git-ignored private bundle under `private-jobs/`. Direct `etl.config.source`, `etl.config.target`, and `etl.config.processor` overrides are intended for demo/fallback cases only.
- Archive-on-success remains part of the selected file-backed source config (for example CSV or XML), not `job-config.yaml`.
- Rejected-record output and field-level validation rules remain part of the selected processor config, not `job-config.yaml`.

## Related design note

The broader file-ingestion hardening direction beyond the first preserved CSV proof slice and the current shared file-source archive contract is documented in [`File ingestion hardening`](../architecture/file-ingestion-hardening.md).

## Preserved examples

- `src/main/resources/config-jobs/csv-to-sqlserver/job-config.yaml`
- `src/main/resources/config-jobs/csv-validation-reject-archive/job-config.yaml`
- `src/main/resources/config-jobs/relational-to-relational/job-config.yaml`
- `src/main/resources/config-jobs/xml-to-csv-events/job-config.yaml`
- `src/main/resources/config-jobs/xml-to-json-events/job-config.yaml`
- `src/main/resources/config-jobs/xml-nested-to-csv-to-nested-xml/job-config.yaml`
- `src/main/resources/config-jobs/xml-nested-to-csv-to-nested-xml-archive-e2e/job-config.yaml`
- `src/main/resources/config-jobs/customer-load/job-config.yaml`
- `src/main/resources/config-jobs/department-load/job-config.yaml`
- `src/main/resources/config-jobs/cust-dept-load/job-config.yaml`

## Private deployable bundle pattern

For private production-like runs, prefer a grouped collection under `private-jobs/` so one collection can be deleted as a single purge unit, and gather the runnable YAML under a `config/` subfolder. Treat that area as a developer-local placeholder created from preserved examples, not as a committed repository example root. For example:

```text
private-jobs/
  acme-prod/
    partner-orders-daily/
      config/
        job-config.yaml
        source-config.yaml
        target-config.yaml
        processor-config.yaml
      input/
      output/
      archive/
```

Run it with `etl.config.job=private-jobs/acme-prod/partner-orders-daily/config/job-config.yaml` so the relative config paths still resolve from that config folder. Keep the copied bundle local and out of GitHub.

Single-level private bundles such as `private-jobs/partner-orders/` still work, but grouped collections are the preferred pattern for new private workspaces.

## Related docs

- [`Config docs overview`](README.md)
- [`Default processor reference`](processor/default-processor.md)
- [`Hierarchical flow composition`](../architecture/hierarchical-flow-composition.md)
- [`Flow normalization rules`](../architecture/flow-normalization-rules.md)
- [`Runtime flow`](../architecture/runtime-flow.md)

