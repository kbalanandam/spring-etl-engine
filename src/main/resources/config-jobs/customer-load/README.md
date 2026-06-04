# customer-load

Preserved explicit-job baseline for the shipped flat `CSV -> XML` runtime path.

## Flow

- source: CSV `Customers`
- target: XML `Customers`
- processor: default field-to-field mapping
- runtime mode with the preserved sample input: tasklet, because the bundle has 3 CSV data rows

## Files

- `job-config.yaml` - explicit selected scenario and ordered step list
- `source-config.yaml` - CSV source contract for `Customers`
- `target-config.yaml` - flat XML target contract for `Customers`
- `processor-config.yaml` - shared processor field mappings
- `output/` - scenario-local runtime output folder for `customers.xml`

## Expected behavior

- the runtime reads the CSV rows from `src/main/resources/demo-input/Customers.csv`
- the shared processor maps `id`, `name`, and `email` directly into the XML target fields
- the XML writer publishes `output/customers.xml`
- the final document contains one `<Customer>` element per accepted input row under `<Customers>`

## Run example

Generate the job-scoped XML classes first, then run the selected scenario:

```powershell
mvn --no-transfer-progress -Pxml-generation "-Detl.xml.generation.jobConfig=src/main/resources/config-jobs/customer-load/job-config.yaml" process-classes
mvn --no-transfer-progress -DskipTests "-Dspring-boot.run.jvmArguments=-Detl.config.job=src/main/resources/config-jobs/customer-load/job-config.yaml" spring-boot:run
```

This bundle intentionally omits authored `packageName` values. During explicit job runs, the runtime derives the generated model packages from `job-config.yaml -> name`, so generation and runtime stay aligned on the same selected scenario identity.

# spring-etl-engine v1.9.0 Release Notes

## Highlights

This release strengthens restart/recovery clarity, improves Operator UI observability, and adds a dedicated read-only Job Config drill-down experience.

### What users get

- Clearer restart contract behavior for selected-job runs
- Better run triage in Operator UI (`runMode`, `recoveryPolicy`, filters)
- New read-only Job Config page (opened intentionally, not shown inline by default)
- Improved authored YAML ergonomics for recovery policy tokens

---

## Key updates

### 1) Restart contract hardening (F1 baseline)

- `resume-from-checkpoint` remains intentionally unsupported and now consistently fail-fast guarded in selected-job runtime paths.
- Shipped baseline remains deterministic `rerun-from-start` behavior.
- Regression coverage expanded to include runtime-descriptor assembly guardrail paths.

### 2) Recovery policy wiring and authored aliases

- `job-config.yaml -> recoveryPolicy` is fully propagated into runtime metadata/evidence.
- Default remains `rerun-from-start` when omitted.
- New authored YAML short aliases:
  - `rerun` -> `rerun-from-start`
  - `restart` -> `resume-from-checkpoint`
- Runtime/log/API evidence continues to emit canonical tokens.

### 3) Operator Runs visibility and filtering

- Runs list and run detail now show:
  - `runMode`
  - `recoveryPolicy`
- `/api/v1/runs` supports optional filters:
  - `runMode`
  - `recoveryPolicy`
- Operator route/query state persists these filters for easier triage.

### 4) New read-only Job Config drill-down

- Added endpoint:
  - `GET /api/v1/jobs/{jobKey}/config`
- Added UI route:
  - `#/jobs/{jobKey}/config`
- Job detail now provides a clickable “View read-only config” action.
- Config content is intentionally not shown inline by default to keep job detail compact.

---

## API additions and changes

### Added

- `GET /api/v1/jobs/{jobKey}/config`  
  Returns read-only payload:
  - `jobKey`
  - `displayName`
  - `jobConfigPath`
  - `rawYaml`

### Extended

- `GET /api/v1/runs` now accepts optional:
  - `runMode`
  - `recoveryPolicy`

---

## Compatibility and behavior notes

- No checkpoint-resume execution behavior is introduced in this release.
- `resume-from-checkpoint` continues to fail fast by design.
- Changes are additive and backward-compatible for existing selected-job flows.
- Preserved `config-jobs/*` examples keep canonical `recoveryPolicy` tokens for reference clarity.

---

## Verification summary

- Full verification: **READY**
- Maven tests: **PASS**
- Smoke verification: **PASS**
- Pass rate: **627/627**

---

## Release metadata

- Version: `v1.9.0`
- Tag: `v1.9.0`
- Tag target commit: `d131d1a`

---

## Recommended next steps for users

1. Pull/tag update to `v1.9.0`.
2. If using Operator UI, hard-refresh once after upgrade.
3. For authored jobs, optionally adopt `recoveryPolicy` aliases (`rerun`, `restart`) where preferred.
4. Continue using canonical evidence tokens (`rerun-from-start`, `resume-from-checkpoint`) for ops/search/reporting.The preserved `job-config.yaml` now also sets `recoveryPolicy: rerun-from-start` explicitly so run evidence reflects the shipped F1 baseline contract. Short aliases (`rerun`, `restart`) are accepted in authored YAML, but preserved bundles keep canonical tokens for executable-reference clarity.

