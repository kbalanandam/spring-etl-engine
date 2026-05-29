# customer-load-retry-policy-runtime-failure

Preserved explicit-job B2 proof bundle for retry-policy runtime evidence on a flat `CSV -> CSV` flow.

## Flow

- source: CSV `Customers`
- target: CSV `CustomersCsv`
- processor: default field-to-field mapping
- retry policy: step-level `retryPolicy` with `retryableCategories: [runtime]`

## Intentional proof behavior

- input row 1 uses `id=BAD_ID`, which triggers read-time conversion failure (`runtime` category)
- startup planning confirms retry policy wiring through `STEP_READY event=retry_policy_enabled`
- run fails on a non-skippable read-path runtime exception (`NonSkippableReadException`) after chunk-mode override
- this preserved bundle is an explicit runtime-boundary example for B2, not a succeeded-after-retry scenario

## Files

- `job-config.yaml` - explicit selected scenario and ordered step list with retry policy
- `source-config.yaml` - CSV source contract for `Customers`
- `target-config.yaml` - CSV target contract for `CustomersCsv`
- `processor-config.yaml` - shared processor field mappings
- `input/Customers-bad.csv` - malformed first row used to force deterministic read-path failure-boundary evidence

## Run example

```powershell
mvn --no-transfer-progress -Pxml-generation "-Detl.xml.generation.jobConfig=src/main/resources/config-jobs/customer-load-retry-policy-runtime-failure/job-config.yaml" process-classes
mvn --no-transfer-progress -DskipTests "-Dspring-boot.run.main-class=com.etl.ETLEngineApplication" "-Dspring-boot.run.jvmArguments=-Detl.config.job=src/main/resources/config-jobs/customer-load-retry-policy-runtime-failure/job-config.yaml" spring-boot:run
```

## Evidence checkpoints

- `logs/startup/startup.log` contains step planning and retry-policy enablement evidence
- `logs/<yyyy-MM-dd>/customer-load-retry-policy-runtime-failure.log` contains runtime read-failure evidence and terminal run summary



