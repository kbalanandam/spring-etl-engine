# ADR-0004: Use explicit job-config selection for business scenarios

- Status: Accepted
- Date: 2026-04-21

## Context

The ETL engine now needs a stable way to preserve and run multiple business scenarios without rewriting the baseline resource YAML files for each run.

Examples already exist or are emerging, such as:
- `customer-load`
- `department-load`
- `cust-dept-load`
- `csv-to-sqlserver`

The engine also needs to avoid auto-discovering every scenario folder and executing all of them implicitly, because one application run must remain explicit and predictable.

## Decision

The product will use `etl.config.job` as the primary selector for one ETL run.

Runtime selection is strict by default. If `etl.config.job` is not set, startup should fail unless demo fallback is explicitly enabled for local/demo use.

That selector points to one `job-config.yaml`, and that file explicitly defines the selected scenario's:
- `sourceConfigPath`
- `targetConfigPath`
- `processorConfigPath`

Relative paths in `job-config.yaml` are resolved from the `job-config.yaml` folder.

Legacy direct source/target/processor path loading remains available only when `etl.config.allow-demo-fallback=true` is enabled. That fallback path is intended for demos and local runs, not as the enterprise default.

`JobConfig.name` is currently descriptive metadata for the selected scenario. It is not yet a separate runtime lookup mechanism.

## Consequences

### Positive
- one ETL run maps to one explicit business scenario/config bundle
- scenario folders can preserve real business flows without mutating baseline resource YAML files
- runtime behavior stays predictable and debuggable
- startup no longer silently degrades into demo/default YAML in normal execution
- future orchestration metadata can be added to `job-config.yaml` without changing the public entry point

### Negative
- the selector currently uses a file path instead of a simpler scenario-name property
- documentation must make it clear that `etl.config.job` is the product-facing scenario selector
- local/demo fallback now requires an explicit opt-in property
- contributors must avoid reintroducing scenario auto-discovery as hidden behavior

## Alternatives considered

### 1. Scenario-name property only
Example: `etl.config.scenario=customer-load`

This is more user-friendly, but it adds another abstraction layer before the product needs it. Right now it would mostly translate to a `job-config.yaml` path anyway.

### 2. Auto-discover all scenario folders
This was rejected because it makes one application run ambiguous and harder to control operationally.

### 3. Direct source/target/processor path properties only
This remains supported only through explicit demo fallback for legacy/manual runs, but it is not the preferred product-level entry point for preserved business scenarios.

## Implications for future work

Future product evolution should keep `etl.config.job` as the orchestration entry point unless there is a strong reason to add a higher-level abstraction.

If a scenario-name abstraction is introduced later, it should resolve internally to the same explicit `job-config.yaml` contract rather than bypassing it.

