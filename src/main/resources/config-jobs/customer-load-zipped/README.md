# customer-load-zipped

Preserved explicit-job example for the first shared unzip-before-read slice on the shipped flat `CSV -> XML` runtime path.

## Flow

- source artifact: ZIP `Customers.zip`
- extracted readable source: CSV `Customers.csv`
- target: XML `Customers`
- processor: default field-to-field mapping
- unzip scope: ZIP preparation inferred directly from `filePath: input/Customers.zip` before validation, record counting, and reading

## Files

- `job-config.yaml` - explicit selected job and ordered step list
- `source-config.yaml` - CSV source contract that stays minimal and relies on the ZIP `filePath` itself
- `target-config.yaml` - flat XML target contract for `Customers`
- `processor-config.yaml` - shared processor field mappings
- `input/Customers.zip` - preserved zipped input artifact
- `output/` - scenario-local runtime output folder for `customers.xml`

## Expected behavior

- the runtime treats `input/Customers.zip` as the original ingress artifact
- the shared file-source ZIP support detects `input/Customers.zip` automatically and extracts `Customers.csv` into a runtime-owned JVM temp working location before normal CSV processing begins
- advanced ZIP overrides such as `unzip.entryName` or `unzip.extractDir` remain available on the shared file-source contract, but this preserved bundle intentionally does not need them
- the shared processor maps `id`, `name`, and `email` directly into the XML target fields
- the XML writer publishes `output/customers.xml`
- if archive or reject behavior is added later, disposition still applies to the original ZIP artifact rather than the temporary extracted CSV file
- when the default prepared location is used, cleanup removes the extracted CSV file and prunes any now-empty runtime-owned temp directories after the step finishes

## Run example

Generate the job-scoped XML classes first, then run the selected scenario:

```powershell
mvn --no-transfer-progress -Pxml-generation "-Detl.xml.generation.jobConfig=src/main/resources/config-jobs/customer-load-zipped/job-config.yaml" process-classes
mvn --no-transfer-progress -DskipTests "-Dspring-boot.run.jvmArguments=-Detl.config.job=src/main/resources/config-jobs/customer-load-zipped/job-config.yaml" spring-boot:run
```

This bundle intentionally omits authored `packageName` values. During explicit job runs, the runtime derives the generated model packages from `job-config.yaml -> name`, so generation and runtime stay aligned on the same selected job identity.

