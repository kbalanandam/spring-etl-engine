# xml-nested-to-csv-to-nested-xml

Preserved explicit job scenario for a two-step roundtrip flow inside one selected scenario.

## Purpose

This scenario proves that one `job-config.yaml` can execute two ordered flows in a single run:

1. nested XML source -> CSV intermediate target
2. CSV intermediate source -> nested XML target

It preserves the current shipped multi-step orchestration baseline while also proving a practical file handoff between steps inside one scenario.

## Baseline composed-flow pattern

Use this bundle as the reference pattern when adding future multi-step scenarios that chain one step's output into the next step's input inside the same selected `job-config.yaml`.

Today that baseline means:

- keep the step order explicit in `job-config.yaml`
- preserve the intermediate artifact path in the selected source/target configs
- make the handoff format readable by the downstream step, for example `includeHeader: true` for a CSV file that will be read by the CSV reader in the next step
- keep the downstream CSV source explicit with `skipHeader: true` when the intermediate handoff is written with a header row
- publish the intermediate and final file artifacts only after the owning step completes successfully so a failed rerun does not expose partial outputs as valid handoffs

## Files

- `job-config.yaml` - explicit scenario entry point with two ordered steps
- `source-config.yaml` - nested XML source plus CSV intermediate source definition
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
- step 2 reads that intermediate CSV file in the same job run
- step 2 keeps the normal XML target top-level metadata in that same order and uses `modelDefinitionPath` as the structural source of truth for the nested output
- step 2 writes `output/tag-validation-roundtrip.xml`

## Run-level count interpretation

For run-summary reconciliation, this preserved example should be read with operator-oriented rollup semantics:

- `sourceCount` comes from step 1 ingress reads against the XML source
- `writtenCount` comes from step 2 writes to the final XML target
- `rejectedCount` is the sum of rejected records across both steps
- the intermediate CSV handoff is diagnostic only through `handoffReadCount` / `handoffWriteCount`; it is not treated as final published output

## Run example

Generate the job-scoped XML classes first, then run the scenario:

```powershell
mvn --no-transfer-progress -Pxml-generation "-Detl.xml.generation.jobConfig=src/main/resources/config-jobs/xml-nested-to-csv-to-nested-xml/job-config.yaml" -DskipTests package
java "-Detl.config.job=src/main/resources/config-jobs/xml-nested-to-csv-to-nested-xml/job-config.yaml" -jar target/spring-etl-engine-1.5.0.jar
```


