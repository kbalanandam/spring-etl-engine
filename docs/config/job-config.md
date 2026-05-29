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
| `isActive` | no | boolean | Optional startup guardrail for the selected explicit job. Omitted means `true`; `false` blocks startup before referenced source/target/processor YAMLs are resolved |
| `sourceConfigPath` | yes | string | Relative or absolute path to the selected source config file |
| `targetConfigPath` | yes | string | Relative or absolute path to the selected target config file |
| `processorConfigPath` | yes | string | Relative or absolute path to the selected processor config file |
| `steps` | yes | list | Explicit ordered ETL steps for this run |
| `steps[].name` | yes | string | Step name used for plan/logging/runtime identity |
| `steps[].source` | yes | string | Must match a configured `sourceName` from the selected source config |
| `steps[].target` | yes | string | Must match a configured `targetName` from the selected target config |
| `steps[].skipPolicy.enabled` | no | boolean | Optional step-level B1 slice flag. When `true`, enables bounded skip behavior for supported CSV steps; runtime may override tasklet planning to chunk mode for this slice |
| `steps[].skipPolicy.skipLimit` | conditional | int | Required positive integer when `steps[].skipPolicy.enabled: true` |
| `steps[].skipPolicy.skippableCategories[]` | conditional | list[string] | Preferred when skip policy is enabled; each value must be a supported ETL error category (`config`, `runtime`, `factory`, `listener`, `relational`, `unclassified`) |
| `steps[].skipPolicy.skippableExceptions[]` | conditional | list[string] | Optional compatibility field; each value must be a loadable Java exception class name |
| `steps[].retryPolicy.enabled` | no | boolean | Optional step-level B2 first runtime slice flag. When `true`, startup validates retry policy shape and runtime wires bounded fault-tolerant retry for supported step plans |
| `steps[].retryPolicy.maxAttempts` | conditional | int | Required integer `>= 2` when `steps[].retryPolicy.enabled: true` |
| `steps[].retryPolicy.backoffMs` | conditional | long | Required non-negative integer when `steps[].retryPolicy.enabled: true` |
| `steps[].retryPolicy.retryableCategories[]` | conditional | list[string] | Preferred when retry policy is enabled; values use ETL error categories (`config`, `runtime`, `factory`, `listener`, `relational`, `unclassified`) |
| `steps[].retryPolicy.retryableExceptions[]` | conditional | list[string] | Optional compatibility field; each value must be a loadable Java exception class name |

## Single-step example

This mirrors `src/main/resources/config-jobs/csv-to-sqlserver/job-config.yaml`.

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

### Single-step example walkthrough

- `name` identifies the selected scenario in logs, evidence, and generated package derivation for package-free explicit source/target configs.
- Keep `name` non-blank for every explicit job bundle; the active generated-model contract now fails fast when it is blank.
- `isActive` is optional. When omitted, explicit startup treats the selected job as active. Set `isActive: false` only when you want startup to fail fast before referenced configs are resolved.
- `sourceConfigPath` points to the source bundle for this run and is resolved relative to the `job-config.yaml` folder when written as a relative path.
- `targetConfigPath` points to the target bundle selected for this run.
- `processorConfigPath` points to the processor bundle that contains the mapping for the step below.
- `steps` is the explicit ordered execution plan.
- `steps[].name` is the operator-visible step identity used in plan and run logs.
- `steps[].source` must match one `sourceName` from the selected source config.
- `steps[].target` must match one `targetName` from the selected target config.
- Reusing the same logical name as `steps[].source` and `steps[].target` in the same step is still allowed for the current single-step compatibility pattern (for example `Customers -> Customers`). Reusing a logical name across different ordered steps is only valid when an earlier step produces that handoff artifact and a later step consumes it.

### Optional skip-policy example (B1 first slice)

```yaml
name: customer-load-skip-policy
sourceConfigPath: source-config.yaml
targetConfigPath: target-config.yaml
processorConfigPath: processor-config.yaml
steps:
  - name: customers-step
    source: Customers
    target: CustomersOut
    skipPolicy:
      enabled: true
      skipLimit: 10
      skippableCategories:
        - runtime
```

For this first slice:

- skip policy stays step-scoped under `job-config.yaml -> steps[]`
- skip policy is opt-in; default behavior remains fail fast
- supported scope is currently CSV steps with fault-tolerant chunk execution
- when the default planner selects tasklet mode, runtime overrides to chunk mode so skip policy remains active and explicit
- prefer `skippableCategories` for product-facing configs; keep `skippableExceptions` only for advanced compatibility cases
- step planning fails fast when skip policy is combined with ordered duplicate winner selection (`duplicate + orderBy`) in this first slice

### Optional retry-policy example (B2 first runtime slice)

```yaml
name: customer-load-retry-policy
sourceConfigPath: source-config.yaml
targetConfigPath: target-config.yaml
processorConfigPath: processor-config.yaml
steps:
  - name: customers-step
    source: Customers
    target: CustomersOut
    retryPolicy:
      enabled: true
      maxAttempts: 3
      backoffMs: 250
      retryableCategories:
        - runtime
```

For this first runtime slice:

- retry policy stays step-scoped under `job-config.yaml -> steps[]`
- retry policy is opt-in; default behavior remains unchanged
- startup strictly validates retry policy shape (`maxAttempts`, `backoffMs`, categories/exceptions)
- runtime executes retry through Spring Batch fault-tolerant chunk handling
- when the default planner selects tasklet mode, runtime overrides to chunk mode so retry behavior stays explicit and bounded at the supported chunk boundary
- first-slice guardrail: one step cannot enable both `skipPolicy` and `retryPolicy`
- step planning also fails fast when retry policy is combined with ordered duplicate winner selection (`duplicate + orderBy`) because that path intentionally forces tasklet buffering
- operators now get `STEP_EVENT event=retry_attempt` for each failed attempt and `STEP_EVENT event=retry_summary` with terminal outcome (`succeeded_after_retry` or `failed_after_retries`)

### Skip-policy category cheat-sheet

Use `steps[].skipPolicy.skippableCategories[]` with these ETL category values:

| Category | When to use |
|---|---|
| `config` | Failures caused by configuration contract problems (normally not a good skip candidate) |
| `runtime` | Runtime execution failures during step processing (read/process/write path) where bounded skip may be acceptable |
| `factory` | Failures while creating dynamic reader/processor/writer components |
| `listener` | Failures raised from lifecycle listeners/hooks |
| `relational` | Relational connector/runtime failures tied to database access paths |
| `unclassified` | Failures not mapped to a specific ETL category yet |

In most cases, start with `runtime` only, then broaden deliberately when evidence shows another category should be tolerated.

Good default starter baseline:

```yaml
skipPolicy:
  enabled: true
  skipLimit: 3
  skippableCategories:
    - runtime
```

Use this as the first production-style draft, then widen categories or increase `skipLimit` only with explicit run evidence.

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
- Explicit `etl.config.job` runs now also fail fast when the selected `job-config.yaml` declares `isActive: false`.
- Step order is taken from `steps` and is no longer inferred by source/target list position.
- The current flat `steps` list is the executable baseline even when future architecture docs describe a richer `main flow -> subflow -> step` hierarchy.
- Current descriptor assembly synthesizes named subflow/status metadata from the flat ordered `steps` list for observability, so startup/job logs can emit `MAIN_FLOW_PLAN`, `SUBFLOW_PLAN`, and `SUBFLOW_SUMMARY` evidence even though execution still follows the flat `steps` list.
- When an upstream step/subflow fails, downstream descriptor-derived subflows can now be logged as `BLOCKED` with explicit dependency and handoff reasons, but `job-config.yaml` still does not require explicit authored subflow blocks.
- Relative `sourceConfigPath`, `targetConfigPath`, and `processorConfigPath` values are resolved from the `job-config.yaml` file's folder.
- Checked-in reference bundles should use `config-jobs/...`. Developer-local private bundles copied from those examples should prefer [`private-jobs/...`](../../private-jobs/README.md). Legacy `config-scenarios/...` bundle paths remain temporarily accepted only at the selected `etl.config.job` entry path for backward compatibility; once that job is loaded, its referenced `sourceConfigPath`, `targetConfigPath`, and `processorConfigPath` are resolved directly and should already be canonical.
- The runtime does not scan scenario folders automatically; one run explicitly chooses one `job-config.yaml`.
- Explicit `etl.config.job` runs now also require a non-blank `name` so generated-model naming stays deterministic and does not fall back to the job folder name.
- The optional top-level `isActive` flag defaults to `true`; when it is explicitly `false`, `ConfigLoader` now stops before referenced source/target/processor configs are resolved or steps are wired.
- `name` is the selected bundle identity shown in logs and metadata. Explicit job runs derive source and target packages as `com.etl.generated.job.<normalized-job-name>.source` and `com.etl.generated.job.<normalized-job-name>.target`.
- If a selected source or target config still authors `packageName`, runtime/build-time startup now fails fast with the selected job name, config path, logical config name, and the derived package the selected job would have used.
- During explicit startup, the selected source and target configs are validated first, then the selected processor config is validated before generated-model class checks run.
- Processor-config validation failures in explicit runs are surfaced with the selected scenario name and processor-config path so operators can identify the broken scenario bundle quickly.
- Generated-model naming/package failures in explicit runs are surfaced as config errors with the selected scenario name, job-config path, and the failing `step` / `source` / `target` so support can narrow model-resolution issues quickly.

## Planned enhancement: custom-step pairing with standard steps

This is a future-direction enhancement, not a shipped runtime field set today.

Tracked backlog item:

- [`A7 - Add custom-step pairing, context handoff, and failure-contract baseline`](../product/backlog-items/A7-custom-step-pairing-context-handoff-and-failure-contract.md)

Design intent:

- keep one explicit ordered `steps[]` contract
- add bounded customer-owned `custom` steps before/after standard steps
- allow controlled context handoff (for example `header.fileId`) from custom to standard steps
- preserve one shared continuation/failure model across both step kinds

Backward-compatibility guardrails for the planned first slice:

- `steps[].kind` stays optional; omission continues to mean `standard`
- existing standard-only jobs require no config migration and keep current runtime semantics
- existing standard step fields (`name`, `source`, `target`) stay unchanged for `kind: standard`
- custom-step evidence is additive; existing standard-step evidence remains stable

Conceptual example (future contract shape):

```yaml
name: csv-to-relational-with-header-status
sourceConfigPath: source-config.yaml
targetConfigPath: target-config.yaml
processorConfigPath: processor-config.yaml
steps:
  - name: header-start
    kind: custom
    custom:
      type: headerStart
      publish:
        fileId: header.fileId
  - name: detail-load
    kind: standard
    source: Customers
    target: CustomerDetail
  - name: header-finalize
    kind: custom
    custom:
      type: headerFinalize
```

In this planned shape, `custom.publish` maps custom-handler output fields to shared context keys consumed by downstream steps (for example `fileId -> header.fileId`).

Planned preserved examples for this enhancement:

- `src/main/resources/config-jobs/csv-to-relational-with-header-status/`
- `src/main/resources/config-jobs/xml-to-csv-with-custom-run-audit/`

## Validation / usage notes

- Every `steps[].source` value must match a configured `sourceName` in the selected source config file.
- Every `steps[].target` value must match a configured `targetName` in the selected target config file.
- If `isActive: false` is set on the selected explicit job, startup stops before downstream config resolution as a configuration failure rather than silently skipping execution.
- In explicit job mode, selected source/target config files no longer support `packageName`. The runtime and build-time generation path derive package identity from the selected non-blank `job-config.yaml` name using a normalized lowercase alphanumeric segment.
- Remove authored `packageName` from explicit bundles instead of trying to keep it aligned manually; selected-job startup now fails immediately when the property is present so naming cannot drift silently.
- Selected logical names must still remain stable enough to avoid generated-class collisions after normalization. Different names such as `Customer Feed` and `Customer-Feed` can now fail fast if they would generate the same class in the same selected job side.
- The selected processor config must contain a matching mapping for each source/target pair used by the selected steps.
- A multi-step scenario can reuse one processor config file with multiple mappings; runtime picks the mapping by `source` and `target` names, not by list position.
- When `steps[].skipPolicy.enabled=true`, `skipLimit` must be positive and at least one of `skippableCategories[]` or `skippableExceptions[]` must be provided.
- `skippableCategories[]` accepts ETL category values (`config`, `runtime`, `factory`, `listener`, `relational`, `unclassified`) and is the preferred first-choice contract for readability.
- `skippableExceptions[]` remains available as a compatibility escape hatch for advanced cases that need precise framework exception matching.
- The first B1 runtime slice applies skip policy to CSV steps through fault-tolerant chunk execution. When a step would otherwise run as tasklet, runtime overrides to chunk mode.
- The first B1 runtime slice fails fast when skip policy is combined with ordered duplicate winner selection (`duplicate` rule with `orderBy`) so conflicting buffering/runtime modes do not produce ambiguous behavior.
- When `steps[].retryPolicy.enabled=true`, `maxAttempts` must be `>=2`, `backoffMs` must be non-negative, and at least one of `retryableCategories[]` or `retryableExceptions[]` must be provided.
- `retryableCategories[]` uses the same ETL category vocabulary as skip policy (`config`, `runtime`, `factory`, `listener`, `relational`, `unclassified`).
- In the B2 first runtime slice, explicit startup still fails fast when one step configures both `skipPolicy` and `retryPolicy`.
- In the B2 first runtime slice, retry-capable steps use chunk-oriented fault tolerance; tasklet plans are overridden to chunk mode when retry policy is enabled.
- In the same slice, runtime also fails fast when retry policy is combined with ordered duplicate winner selection because that duplicate path intentionally requires tasklet buffering.
- If the selected processor config is malformed, explicit startup now fails before generated-model class validation so processor issues are not masked by unrelated missing generated classes.
- Use `etl.config.job` as the normal production-style entry point whether the selected `job-config.yaml` lives under `src/main/resources/config-jobs/` or a developer-local git-ignored private bundle under `private-jobs/`. Direct `etl.config.source`, `etl.config.target`, and `etl.config.processor` overrides are intended for demo/fallback cases only.
- Archive-on-success remains part of the selected file-backed source config (for example CSV or XML), not `job-config.yaml`.
- Rejected-record output and field-level validation rules remain part of the selected processor config, not `job-config.yaml`.

## Related design note

The broader file-ingestion hardening direction beyond the first preserved CSV proof slice and the current shared file-source archive contract is documented in [`File ingestion hardening`](../architecture/etl-core/file-ingestion-hardening.md).

## Preserved examples

- `src/main/resources/config-jobs/csv-to-sqlserver/job-config.yaml`
- `src/main/resources/config-jobs/csv-validation-reject-archive/job-config.yaml`
- `src/main/resources/config-jobs/relational-to-relational/job-config.yaml`
- `src/main/resources/config-jobs/xml-to-csv-events/job-config.yaml`
- `src/main/resources/config-jobs/xml-to-json-events/job-config.yaml`
- `src/main/resources/config-jobs/xml-nested-to-csv-to-nested-xml/job-config.yaml`
- `src/main/resources/config-jobs/xml-nested-to-csv-to-nested-xml-archive-e2e/job-config.yaml`
- `src/main/resources/config-jobs/customer-load/job-config.yaml`
- `src/main/resources/config-jobs/customer-load-skip-policy-category/job-config.yaml`
- `src/main/resources/config-jobs/customer-load-skip-policy-category-unclassified/job-config.yaml`
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
- [`OneFlow runtime fallback reference`](../architecture/etl-core/oneflow-runtime-fallback-reference.md)
- [`Hierarchical flow composition`](../architecture/etl-core/hierarchical-flow-composition.md)
- [`Flow normalization rules`](../architecture/etl-core/flow-normalization-rules.md)
- [`Runtime flow`](../architecture/etl-core/runtime-flow.md)

