# xml-nested-to-csv-to-nested-xml-archive-e2e

Preserved explicit job scenario for a two-step roundtrip flow inside one selected scenario, with XML archive-on-success enabled on the first step.

## Purpose

This scenario proves four things together inside one selected `job-config.yaml`:

1. nested XML source -> CSV intermediate target
2. CSV intermediate source -> nested XML target
3. XML source archive-on-success after the first step completes
4. `archivedSourcePath` evidence in step-finished logs

## Files

- `job-config.yaml` - explicit scenario entry point with two ordered steps
- `source-config.yaml` - nested XML source with archive-on-success plus CSV intermediate source definition
- `target-config.yaml` - CSV intermediate target plus nested XML target definition
- `processor-config.yaml` - shared processor mappings for both step pairs
- `definitions/nested-source-model.yaml` - structural nested XML source contract
- `definitions/nested-target-model.yaml` - authoritative structural nested XML target contract
- `input/nested-sample.xml` - preserved nested XML sample payload
- `output/` - scenario-local runtime folder for the intermediate CSV handoff and final XML artifact

## XML target authoring note

This preserved bundle keeps the same top-level XML target YAML shape used by simpler XML output scenarios:

- `format`
- `targetName`
- `filePath`
- `rootElement`
- `recordElement`
- optional `modelDefinitionPath`

The nested XML difference is that `modelDefinitionPath` supplies the structural target contract for the final XML shape. That means the nested XML target is not a different top-level layout; it is the same authoring pattern plus an external structural definition, while the generated package is derived from `job-config.yaml -> name` instead of an authored `packageName` field.

## Expected behavior

- step 1 reads `input/nested-sample.xml`
- step 1 writes `output/intermediate/tag-validation-intermediate.csv` with a header row
- step 1 archives the original XML file to `archive/success/nested-sample.xml`
- step 1 emits `archivedSourcePath` in `STEP_EVENT event=step_finished`
- step 2 reads that intermediate CSV file in the same job run
- step 2 keeps the normal XML target top-level metadata in that same order and uses `modelDefinitionPath` as the structural source of truth for the nested output
- step 2 writes `output/tag-validation-roundtrip.xml`

## Run example

Generate the job-scoped XML classes first, then run the scenario:

```powershell
Set-Location 'C:\spring-etl-engine'
mvn --no-transfer-progress -Pxml-generation "-Detl.xml.generation.jobConfig=src/main/resources/config-jobs/xml-nested-to-csv-to-nested-xml-archive-e2e/job-config.yaml" -DskipTests package
 java "-Detl.config.job=src/main/resources/config-jobs/xml-nested-to-csv-to-nested-xml-archive-e2e/job-config.yaml" -jar target/spring-etl-engine-1.6.0.jar
```

## Rerun note

Because archive-on-success moves `input/nested-sample.xml`, restore or recopy the input file before rerunning this scenario if you want to exercise the same archive behavior again.

