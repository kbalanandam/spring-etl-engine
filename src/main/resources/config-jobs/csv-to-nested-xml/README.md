# csv-to-nested-xml

Preserved explicit job scenario for a CSV source to nested XML target flow.

## Purpose

This scenario proves that the shared default processor path can:

- read a CSV source through explicit `job-config.yaml` selection
- map flat CSV fields into nested XML target paths such as `profile.email` and `address.city`
- derive default generated packages from `job-config.yaml` `name` when `packageName` is omitted in the selected source/target YAMLs
- generate job-scoped nested XML target model classes through `modelDefinitionPath`
- write the final nested XML output through the active XML writer path

## Files

- `job-config.yaml` - explicit job selection and step binding
- `source-config.yaml` - CSV source definition for the preserved sample
- `target-config.yaml` - XML target definition using `modelDefinitionPath`
- `processor-config.yaml` - shared processor mapping from flat CSV fields into nested XML target fields
- `definitions/nested-target-model.yaml` - structural nested XML target contract
- `input/customers.csv` - preserved CSV sample used to verify nested XML output
- `output/` - scenario-local runtime output folder for the final nested XML artifact

When this selected job runs through the explicit job path, omitted `packageName` values default to:

- `com.etl.generated.job.csvtonestedxml.source`
- `com.etl.generated.job.csvtonestedxml.target`

## Expected behavior

- the scenario reads one CSV record from `input/customers.csv`
- the processor maps `email` into `profile.email`
- the processor maps `city` and `country` into `address.city` and `address.country`
- the final XML artifact is written to `output/customers-nested.xml`

## Run example

Generate the job-scoped XML target classes first, then run the selected scenario:

```powershell
mvn --no-transfer-progress -Pxml-generation "-Detl.xml.generation.jobConfig=src/main/resources/config-jobs/csv-to-nested-xml/job-config.yaml" -DskipTests package
java "-Detl.config.job=src/main/resources/config-jobs/csv-to-nested-xml/job-config.yaml" -jar target/spring-etl-engine-1.3.0.jar
```

The checked-in bundle root is now `config-jobs`. The runtime still accepts legacy `config-scenarios/...` paths temporarily for backward compatibility, but that alias path is now deprecated.

