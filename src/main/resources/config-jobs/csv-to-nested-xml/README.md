# csv-to-nested-xml

Preserved explicit job scenario for a CSV source to nested XML target flow.

## Purpose

This scenario proves that the shared default processor path can:

- read a CSV source through explicit `job-config.yaml` selection
- map flat CSV fields into nested XML target paths such as `profile.email` and `address.city`
- keep the same top-level XML target YAML shape used by simpler XML targets (`format`, `targetName`, `filePath`, `rootElement`, `recordElement`, plus optional `modelDefinitionPath`)
- generate job-scoped nested XML target model classes through `modelDefinitionPath`
- write the final nested XML output through the active XML writer path

## Files

- `job-config.yaml` - explicit job selection and step binding
- `source-config.yaml` - CSV source definition for the preserved sample
- `target-config.yaml` - package-free XML target definition using the shared top-level XML target shape plus `modelDefinitionPath`
- `processor-config.yaml` - shared processor mapping from flat CSV fields into nested XML target fields
- `definitions/nested-target-model.yaml` - authoritative structural nested XML target contract
- `input/customers.csv` - preserved CSV sample used to verify nested XML output
- `output/` - scenario-local runtime output folder for the final nested XML artifact

## XML target authoring note

This preserved bundle now follows the same top-level XML target authoring shape used by simpler XML output scenarios such as `customer-load`:

- `format`
- `targetName`
- `filePath`
- `rootElement`
- `recordElement`
- optional `modelDefinitionPath`

The nested XML difference is that `modelDefinitionPath` supplies the structural target contract for elements such as `profile.email` and `address.city`, while the generated package is derived from `job-config.yaml -> name`. The preserved bundle YAML stays package-free.

That means the nested scenario does **not** use a different XML target layout; it uses the same top-level target YAML shape plus an external structural definition.

## Expected behavior

- the scenario reads one CSV record from `input/customers.csv`
- the processor maps `email` into `profile.email`
- the processor maps `city` and `country` into `address.city` and `address.country`
- the XML target config keeps the normal top-level XML target metadata in that same order and uses `modelDefinitionPath` as the structural source of truth for the nested output
- the final XML artifact is written to `output/customers-nested.xml`

## Run example

Generate the job-scoped XML target classes first, then run the selected scenario:

```powershell
mvn --no-transfer-progress -Pxml-generation "-Detl.xml.generation.jobConfig=src/main/resources/config-jobs/csv-to-nested-xml/job-config.yaml" -DskipTests package
 java "-Detl.config.job=src/main/resources/config-jobs/csv-to-nested-xml/job-config.yaml" -jar target/spring-etl-engine-1.6.0.jar
```

The checked-in bundle root is now `config-jobs`. The runtime still accepts legacy `config-scenarios/...` paths temporarily for backward compatibility, but that alias path is now deprecated.

