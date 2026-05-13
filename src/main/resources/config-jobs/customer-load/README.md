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

